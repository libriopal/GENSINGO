package com.ginsengo.steward.research

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private val REQUEST = ResearchRequest(
    coarseRegion = "about 35.55 N, 82.95 W", stateName = "North Carolina", stateRules = "rules",
    seasonLine = "SEASON OPEN: x", dateIso = "2026-09-28", memory = "- none",
    candidates = listOf(
        PromptCandidate("C1", "1-3 km", 900, 14, "north-east", 0.81, DoubleArray(6) { 0.8 }),
        PromptCandidate("C2", "3-6 km", 950, 18, "north", 0.77, DoubleArray(6) { 0.7 }),
    ),
)

private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

/**
 * Claude's wire format, through the real SDK, against a local server speaking the
 * documented Messages API shapes. What this does not prove: that the live service accepts
 * the request. No API key was available in the build environment (EINCOL_REPORT Phase 7).
 */
class ClaudeWireTest {

    private lateinit var server: MockWebServer

    @Before fun up() { server = MockWebServer().apply { start() } }
    @After fun down() { server.shutdown() }

    private fun client() = ClaudeResearchClient("sk-test", "claude-opus-5-5", server.url("/").toString().trimEnd('/'))

    private val searchBlocks = """
        {"type":"server_tool_use","id":"srvtoolu_1","name":"web_search","input":{"query":"ginseng habitat"}},
        {"type":"web_search_tool_result","tool_use_id":"srvtoolu_1","content":[
          {"type":"web_search_result","url":"https://extension.psu.edu/ginseng","title":"Penn State Extension","encrypted_content":"e","page_age":null}]}
    """

    private fun message(content: String, stop: String, extra: String = "") = MockResponse()
        .setHeader("content-type", "application/json")
        .setBody("""{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
            "content":[$content],"stop_reason":"$stop","stop_sequence":null$extra,
            "usage":{"input_tokens":10,"output_tokens":20}}""")

    private val submission = """
        {"type":"tool_use","id":"toolu_1","name":"submit_suggestions","input":{"summary":"Cove country.",
          "suggestions":[{"candidate_id":"C2","headline":"H","rationale":"R","look_for":"L",
          "source_urls":["https://extension.psu.edu/ginseng","https://not-retrieved.org/x"]}]}}
    """

    @Test
    fun submissionIsParsedAndRetrievedPagesAreCollected() = runBlocking {
        server.enqueue(message("$searchBlocks, $submission", "tool_use"))
        val reply = client().research(REQUEST)
        assertEquals("Cove country.", reply.output.summary)
        assertEquals("C2", reply.output.items.single().candidateId)
        assertEquals(listOf("https://extension.psu.edu/ginseng"), reply.retrieved.map { it.url })
        assertEquals("claude-opus-5-5", reply.model)
    }

    @Test
    fun theRequestCarriesSearchStrictSubmitEffortAndFallback() = runBlocking {
        server.enqueue(message(submission, "tool_use"))
        client().research(REQUEST)
        val req = server.takeRequest()
        assertEquals("sk-test", req.getHeader("x-api-key"))
        assertTrue(req.getHeader("anthropic-beta").orEmpty().contains(ClaudeResearchClient.FALLBACK_BETA))
        val body = json(req.body.readUtf8())
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("medium", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertEquals(ResearchPrompt.SYSTEM, body["system"]!!.jsonPrimitive.content)
        val tools = body["tools"]!!.jsonArray.map { it.jsonObject }
        assertTrue(tools.any { it["type"]?.jsonPrimitive?.content == "web_search_20260209" })
        val submit = tools.single { it["name"]?.jsonPrimitive?.content == ResearchPrompt.TOOL_NAME }
        assertEquals("true", submit["strict"]!!.jsonPrimitive.content)
        val schema = submit["input_schema"]!!.jsonObject
        assertEquals("false", schema["additionalProperties"]!!.jsonPrimitive.content)
        val idEnum = schema["properties"]!!.jsonObject["suggestions"]!!.jsonObject["items"]!!.jsonObject["properties"]!!
            .jsonObject["candidate_id"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("C1", "C2"), idEnum)
        // The user turn is the prompt, and the prompt has no fix in it.
        val user = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(user.contains("about 35.55 N, 82.95 W"))
    }

    @Test
    fun pauseTurnIsResumedWithTheAssistantTurnAndNoInventedUserMessage() = runBlocking {
        server.enqueue(message(searchBlocks, "pause_turn"))
        server.enqueue(message(submission, "tool_use"))
        val reply = client().research(REQUEST)
        assertEquals(2, server.requestCount)
        assertEquals(1, reply.retrieved.size)   // collected from the paused turn
        server.takeRequest()
        val second = json(server.takeRequest().body.readUtf8())
        val msgs = second["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant"), msgs.map { it["role"]!!.jsonPrimitive.content })
        val types = (msgs[1]["content"] as JsonArray).map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertTrue(types.contains("web_search_tool_result"))
    }

    @Test
    fun aRefusalIsAnErrorNotAnEmptyList() = runBlocking {
        server.enqueue(message("", "refusal", ""","stop_details":{"type":"refusal","category":null,"explanation":"Declined."}"""))
        expect(ResearchFailure.Status.REFUSED) { client().research(REQUEST) }
    }

    @Test
    fun finishingWithoutSubmittingIsReported() = runBlocking {
        server.enqueue(message("""{"type":"text","text":"Here are my thoughts."}""", "end_turn"))
        expect(ResearchFailure.Status.NO_SUBMISSION) { client().research(REQUEST) }
    }

    @Test
    fun aRejectedKeyIsAnAuthFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("content-type", "application/json")
            .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        expect(ResearchFailure.Status.AUTH) { client().research(REQUEST) }
    }

    @Test
    fun theParserIgnoresMalformedItemsWithoutInventingIds() {
        val out = ClaudeResearchClient.parse(mapOf(
            "summary" to "s",
            "suggestions" to listOf(mapOf("headline" to "no id"), "junk", mapOf("candidate_id" to "C1")),
        ))
        assertEquals(listOf("C1"), out.items.map { it.candidateId })
    }
}

class GeminiWireTest {

    private lateinit var server: MockWebServer

    @Before fun up() { server = MockWebServer().apply { start() } }
    @After fun down() { server.shutdown() }

    private fun client() = GeminiResearchClient("g-key", "gemini-2.5-flash", server.url("/").toString().trimEnd('/'))

    @Test
    fun keyTravelsInAHeaderAndGroundingIsParsed() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"candidates":[{"content":{"role":"model","parts":[{"text":"```json\n{\"summary\":\"S\",\"suggestions\":[{\"candidate_id\":\"C1\",\"headline\":\"H\",\"rationale\":\"R\",\"look_for\":\"L\",\"source_urls\":[\"https://www.ncagr.gov/x\"]}]}\n```"}]},
              "finishReason":"STOP",
              "groundingMetadata":{"groundingChunks":[{"web":{"uri":"https://vertexaisearch.cloud.google.com/grounding-api-redirect/abc","title":"ncagr.gov"}}]}}]}
        """.trimIndent()))
        val reply = client().research(REQUEST)
        val req = server.takeRequest()
        assertEquals("g-key", req.getHeader("x-goog-api-key"))
        assertFalse("key must not be in the URL", req.path.orEmpty().contains("key="))
        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", req.path)
        val body = json(req.body.readUtf8())
        assertTrue((body["tools"] as JsonArray).any { (it as JsonObject).containsKey("google_search") })
        assertEquals("C1", reply.output.items.single().candidateId)
        assertEquals("ncagr.gov", reply.retrieved.single().title)
    }

    @Test
    fun blockedAndBadKeyAreDistinctFailures() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        expect(ResearchFailure.Status.REFUSED) { client().research(REQUEST) }
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""))
        expect(ResearchFailure.Status.AUTH) { client().research(REQUEST) }
    }

    @Test
    fun proseWithNoJsonIsNoSubmission() {
        try {
            GeminiResearchClient.parseOutput("I think C1 is best.")
            fail("expected failure")
        } catch (e: ResearchFailure) {
            assertEquals(ResearchFailure.Status.NO_SUBMISSION, e.status)
        }
    }

    @Test
    fun theSystemPromptAsksForJsonInsteadOfTheTool() {
        assertFalse(GeminiResearchClient.SYSTEM_JSON.contains("calling the ${ResearchPrompt.TOOL_NAME} tool"))
        assertTrue(GeminiResearchClient.SYSTEM_JSON.contains("\"candidate_id\""))
        assertNull(Regex("""-?\d{2}\.\d{3,}""").find(GeminiResearchClient.SYSTEM_JSON))
    }
}

private suspend fun expect(status: ResearchFailure.Status, block: suspend () -> Unit) {
    try {
        block()
        fail("expected $status")
    } catch (e: ResearchFailure) {
        assertEquals(e.message, status, e.status)
    }
}
