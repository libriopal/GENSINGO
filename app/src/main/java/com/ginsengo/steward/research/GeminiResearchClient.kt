package com.ginsengo.steward.research

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini, kept as an option because the previous build integrated it.
 *
 * Two changes from that integration: the key travels in the `x-goog-api-key` header rather
 * than `?key=` in the URL (URLs end up in proxy and server logs), and it is the user's key
 * entered at runtime rather than a build machine's key compiled into every APK.
 *
 * Grounding uses the `google_search` tool. Gemini does not allow a response schema together
 * with search grounding, so the model is asked for JSON in text and the reply is parsed
 * leniently; the validator then enforces what the schema would have. Grounding returns
 * redirect links and bare domain titles rather than page URLs, so citations are checked at
 * domain level ([ResearchValidator.Witness.DOMAIN]) and the UI says so.
 */
class GeminiResearchClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) : ResearchClient {

    override suspend fun research(request: ResearchRequest): ProviderReply = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", SYSTEM_JSON) }) }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", ResearchPrompt.user(request)) }) }
                })
            }
            putJsonArray("tools") { add(buildJsonObject { putJsonObject("google_search") {} }) }
        }.toString()

        val (code, text) = try {
            post("$baseUrl/v1beta/models/$model:generateContent", body)
        } catch (e: IOException) {
            throw ResearchFailure(ResearchFailure.Status.OFFLINE, "Could not reach the Gemini API")
        }
        when (code) {
            200 -> Unit
            400 -> throw ResearchFailure(
                if (text.contains("API_KEY_INVALID") || text.contains("API key not valid"))
                    ResearchFailure.Status.AUTH else ResearchFailure.Status.ERROR,
                if (text.contains("API_KEY")) "The Gemini API key was rejected" else "Gemini rejected the request",
            )
            401, 403 -> throw ResearchFailure(ResearchFailure.Status.AUTH, "The Gemini API key was rejected")
            429 -> throw ResearchFailure(ResearchFailure.Status.RATE_LIMITED, "Rate limited; try again shortly")
            else -> throw ResearchFailure(ResearchFailure.Status.ERROR, "Gemini API error $code")
        }
        parseResponse(text, model)
    }

    private fun post(url: String, body: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        val SYSTEM_JSON: String = ResearchPrompt.SYSTEM.replace(
            "Finish by calling the ${ResearchPrompt.TOOL_NAME} tool exactly once.",
            "Reply with one JSON object and nothing else: " +
                    "{\"summary\": string, \"suggestions\": [{\"candidate_id\": string, \"headline\": string, " +
                    "\"rationale\": string, \"look_for\": string, \"source_urls\": [string]}]}. " +
                    "List suggestions best first.",
        )

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Pure: response JSON text in, reply out. Tested against the documented shape. */
        fun parseResponse(text: String, model: String): ProviderReply {
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw ResearchFailure(ResearchFailure.Status.BAD_REPLY, "Unreadable Gemini response")
            root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
                throw ResearchFailure(ResearchFailure.Status.REFUSED, "Gemini blocked the request ($it)")
            }
            val cand = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
                ?: throw ResearchFailure(ResearchFailure.Status.BAD_REPLY, "Gemini returned no candidates")
            when (val finish = cand["finishReason"]?.jsonPrimitive?.contentOrNull) {
                null, "STOP" -> Unit
                "MAX_TOKENS" -> throw ResearchFailure(ResearchFailure.Status.TRUNCATED, "The reply was cut off")
                "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT" ->
                    throw ResearchFailure(ResearchFailure.Status.REFUSED, "Gemini stopped ($finish)")
                else -> Unit
            }
            val reply = (cand["content"]?.jsonObject?.get("parts") as? JsonArray).orEmpty()
                .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                .joinToString("")
            val retrieved = (cand["groundingMetadata"]?.jsonObject?.get("groundingChunks") as? JsonArray).orEmpty()
                .mapNotNull { ch ->
                    val web = ch.jsonObject["web"]?.jsonObject ?: return@mapNotNull null
                    val uri = web["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    SourceRef(uri, web["title"]?.jsonPrimitive?.contentOrNull ?: "")
                }
            return ProviderReply(parseOutput(reply), retrieved, model)
        }

        /** Extracts the first top-level JSON object from free text (models wrap it in fences). */
        fun parseOutput(reply: String): ModelOutput {
            val start = reply.indexOf('{')
            val end = reply.lastIndexOf('}')
            if (start < 0 || end <= start) {
                throw ResearchFailure(ResearchFailure.Status.NO_SUBMISSION, "Gemini did not return suggestions")
            }
            val obj = runCatching { json.parseToJsonElement(reply.substring(start, end + 1)).jsonObject }
                .getOrElse { throw ResearchFailure(ResearchFailure.Status.BAD_REPLY, "Gemini returned malformed JSON") }
            val items = (obj["suggestions"] as? JsonArray).orEmpty().mapNotNull { el ->
                val m = el as? JsonObject ?: return@mapNotNull null
                ModelItem(
                    candidateId = m.str("candidate_id") ?: return@mapNotNull null,
                    headline = m.str("headline").orEmpty(),
                    rationale = m.str("rationale").orEmpty(),
                    lookFor = m.str("look_for").orEmpty(),
                    sourceUrls = (m["source_urls"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                )
            }
            return ModelOutput(obj.str("summary").orEmpty(), items)
        }

        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
