package com.ginsengo.steward.research

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The field chat through Gemini's REST API (F.1), for owners who chose Gemini in the Research
 * settings; the same request shape and key handling as [GeminiResearchClient] (the key in the
 * `x-goog-api-key` header, never the URL). Gemini has no tool here, so a note to keep is asked for
 * as a final line `NOTE: ...`, which is taken out of the reply and stored.
 */
class GeminiChatClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) : ChatClient {

    override suspend fun reply(system: String, turns: List<ChatTurn>): ChatReply = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", system + NOTE_RULE) }) }
            }
            putJsonArray("contents") {
                turns.forEach { t ->
                    add(buildJsonObject {
                        put("role", if (t.role == ChatTurn.ASSISTANT) "model" else "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", t.text) }) }
                    })
                }
            }
            putJsonArray("tools") { add(buildJsonObject { putJsonObject("google_search") { } }) }
        }.toString()
        val conn = URL("$baseUrl/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        val (code, text) = try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val c = conn.responseCode
            c to ((if (c in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: java.io.IOException) {
            throw ResearchFailure(ResearchFailure.Status.OFFLINE, "Could not reach Gemini")
        } finally {
            conn.disconnect()
        }
        when (code) {
            in 200..299 -> Unit
            401, 403 -> throw ResearchFailure(ResearchFailure.Status.AUTH, "The Gemini API key was rejected")
            429 -> throw ResearchFailure(ResearchFailure.Status.RATE_LIMITED, "Rate limited; try again shortly")
            else -> throw ResearchFailure(ResearchFailure.Status.ERROR, "Gemini API error $code")
        }
        parse(text, model)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        const val NOTE_RULE = "\n\nWhen the owner tells you something durable about themselves (county, habits, goals, what worked), " +
            "add it as a final line starting with \"NOTE: \". Never a coordinate."

        /** Pure: response JSON in, reply out (notes split off the text). */
        fun parse(text: String, model: String): ChatReply {
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw ResearchFailure(ResearchFailure.Status.BAD_REPLY, "Unreadable Gemini response")
            val cand = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
                ?: throw ResearchFailure(ResearchFailure.Status.REFUSED, "Gemini returned no answer")
            val reply = (cand["content"]?.jsonObject?.get("parts") as? JsonArray).orEmpty()
                .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
            val notes = ArrayList<String>()
            val kept = reply.lines().filter { line ->
                val t = line.trim()
                if (t.startsWith("NOTE:", ignoreCase = true)) { ChatPrompt.cleanNote(t.substring(5))?.let(notes::add); false } else true
            }.joinToString("\n").trim()
            return ChatReply(kept, notes, model)
        }
    }
}
