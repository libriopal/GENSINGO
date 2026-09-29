package com.ginsengo.steward

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ginsengo.steward.research.ClaudeResearchClient
import com.ginsengo.steward.research.KeyVault
import com.ginsengo.steward.research.PromptCandidate
import com.ginsengo.steward.research.Provider
import com.ginsengo.steward.research.ResearchRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two things the JVM tests cannot establish, run on Android itself:
 *  - the Anthropic SDK (Jackson, kotlin-reflect, OkHttp) actually works on ART, and
 *  - KeyVault round-trips through the real Android Keystore.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theAnthropicSdkRunsOnArt() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setHeader("content-type", "application/json").setBody("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[
                   {"type":"server_tool_use","id":"srvtoolu_1","name":"web_search","input":{"query":"q"}},
                   {"type":"web_search_tool_result","tool_use_id":"srvtoolu_1","content":[
                     {"type":"web_search_result","url":"https://extension.psu.edu/ginseng","title":"PSU","encrypted_content":"e","page_age":null}]},
                   {"type":"tool_use","id":"toolu_1","name":"submit_suggestions","input":{"summary":"S",
                     "suggestions":[{"candidate_id":"C1","headline":"H","rationale":"R","look_for":"L","source_urls":[]}]}}],
                 "stop_reason":"tool_use","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":1}}
            """.trimIndent()))
            val request = ResearchRequest("about 35.55 N, 82.95 W", "North Carolina", null, null, "2026-09-28", "-",
                listOf(PromptCandidate("C1", "1-3 km", 900, 14, "north-east", 0.8, DoubleArray(6) { 0.8 })))
            val reply = ClaudeResearchClient("k", "claude-opus-5-5", server.url("/").toString().trimEnd('/')).research(request)
            assertEquals("C1", reply.output.items.single().candidateId)
            assertEquals(1, reply.retrieved.size)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun keysRoundTripThroughTheAndroidKeystoreAndAreNotStoredInClear() {
        val vault = KeyVault(context)
        val secret = "sk-ant-test-" + System.nanoTime()
        vault.set(Provider.CLAUDE, secret)
        assertEquals(secret, vault.get(Provider.CLAUDE))
        val raw = context.getSharedPreferences("gensingo_keys", 0).getString(Provider.CLAUDE.name, null)!!
        assertFalse("key stored in clear", raw.contains("sk-ant-test"))
        vault.set(Provider.CLAUDE, "")
        assertNull(vault.get(Provider.CLAUDE))
    }
}
