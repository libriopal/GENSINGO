package com.ginsengo.steward.research

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.WebSearchTool20250305
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

/** One research call to one provider. */
fun interface ResearchClient {
    suspend fun research(request: ResearchRequest): ProviderReply
}

/**
 * Claude, through the official Anthropic Java SDK.
 *
 * The request carries two tools: server-side web search, so the rationale can rest on pages
 * actually retrieved, and a strict client tool whose schema only admits the candidate IDs the
 * device sent (an enum), so an unknown place is not merely rejected afterwards but cannot be
 * expressed at all. [ResearchValidator] still checks, because the Gemini path has no such
 * constraint and one validator should guard both.
 *
 * The call is a manual loop because web search can end a turn with `pause_turn` (the
 * server-side search loop hit its iteration limit); the paused assistant turn is sent back
 * unchanged and the server resumes. Capped at [MAX_CONTINUATIONS].
 */
class ClaudeResearchClient(
    private val apiKey: String,
    private val model: String,
    /** Tests point this at a local mock server; the app never sets it. */
    private val baseUrl: String? = null,
) : ResearchClient {

    override suspend fun research(request: ResearchRequest): ProviderReply = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .apply { baseUrl?.let { baseUrl(it) } }
            // Web search plus reasoning over it routinely takes tens of seconds.
            .timeout(Duration.ofSeconds(150))
            .maxRetries(2)
            .build()
        try {
            val ids = request.candidates.map { it.id }
            val submit = Tool.builder()
                .name(ResearchPrompt.TOOL_NAME)
                .description(ResearchPrompt.TOOL_DESCRIPTION)
                .strict(true)
                .inputSchema(inputSchema(ids))
                .build()

            val history = mutableListOf<MessageParam>(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(ResearchPrompt.user(request))
                    .build()
            )
            val retrieved = LinkedHashMap<String, SourceRef>()

            repeat(MAX_CONTINUATIONS + 1) {
                val params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16_000L)
                    .system(ResearchPrompt.SYSTEM)
                    .messages(history)
                    .apply {
                        if (isHaiku(model)) addTool(WebSearchTool20250305.builder().maxUses(MAX_SEARCHES).build())
                        else addTool(WebSearchTool20260209.builder().maxUses(MAX_SEARCHES).build())
                    }
                    .addTool(submit)
                    .apply {
                        if (supportsEffort(model)) {
                            // Opus 5.5 defaults to medium; set it explicitly so a default
                            // change upstream does not silently change cost or depth.
                            outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                        }
                        if (supportsServerFallback(model)) {
                            // A refused request is retried server-side on a model chosen for
                            // the refusal category instead of simply stopping.
                            putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                            putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                        }
                    }
                    .build()

                val msg = client.messages().create(params)
                collectRetrieved(msg, retrieved)

                val submission = msg.content().firstNotNullOfOrNull { block ->
                    block.toolUse().orElse(null)?.takeIf { it.name() == ResearchPrompt.TOOL_NAME }
                }
                if (submission != null) {
                    @Suppress("UNCHECKED_CAST")
                    val input = submission._input().convert(Map::class.java) as? Map<String, Any?>
                        ?: throw ResearchFailure(ResearchFailure.Status.BAD_REPLY, "Empty submission")
                    return@withContext ProviderReply(parse(input), retrieved.values.toList(), msg.model().asString())
                }

                when (msg.stopReason().orElse(null)) {
                    StopReason.PAUSE_TURN -> history += msg.toParam()
                    StopReason.REFUSAL -> throw ResearchFailure(
                        ResearchFailure.Status.REFUSED,
                        msg.stopDetails().map { d ->
                            listOfNotNull("The model declined", d.explanation().orElse(null)).joinToString(": ")
                        }.orElse("The model declined this request"),
                    )
                    StopReason.MAX_TOKENS -> throw ResearchFailure(
                        ResearchFailure.Status.TRUNCATED, "The reply was cut off before it was submitted",
                    )
                    else -> throw ResearchFailure(
                        ResearchFailure.Status.NO_SUBMISSION, "The model finished without submitting suggestions",
                    )
                }
            }
            throw ResearchFailure(ResearchFailure.Status.NO_SUBMISSION, "Search did not finish after $MAX_CONTINUATIONS continuations")
        } catch (e: ResearchFailure) {
            throw e
        } catch (e: UnauthorizedException) {
            throw ResearchFailure(ResearchFailure.Status.AUTH, "The Claude API key was rejected")
        } catch (e: PermissionDeniedException) {
            throw ResearchFailure(ResearchFailure.Status.AUTH, "This key cannot use $model")
        } catch (e: RateLimitException) {
            throw ResearchFailure(ResearchFailure.Status.RATE_LIMITED, "Rate limited; try again shortly")
        } catch (e: AnthropicServiceException) {
            throw ResearchFailure(ResearchFailure.Status.ERROR, "Claude API error ${e.statusCode()}")
        } catch (e: AnthropicIoException) {
            throw ResearchFailure(ResearchFailure.Status.OFFLINE, "Could not reach the Claude API")
        } finally {
            client.close()
        }
    }

    private fun collectRetrieved(msg: Message, into: MutableMap<String, SourceRef>) {
        for (block in msg.content()) {
            val result = block.webSearchToolResult().orElse(null) ?: continue
            val pages = result.content().resultBlocks().orElse(null) ?: continue // an error object
            for (p in pages) {
                into.putIfAbsent(ResearchValidator.normalise(p.url()), SourceRef(p.url(), p.title()))
            }
        }
    }

    private fun inputSchema(ids: List<String>): Tool.InputSchema {
        val schema = ResearchPrompt.toolSchema(ids)
        @Suppress("UNCHECKED_CAST")
        val props = schema["properties"] as Map<String, Any>
        return Tool.InputSchema.builder()
            .properties(
                Tool.InputSchema.Properties.builder().apply {
                    props.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
                }.build()
            )
            .required(listOf("summary", "suggestions"))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }

    companion object {
        const val MAX_CONTINUATIONS = 3
        const val MAX_SEARCHES = 5L
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        private fun isHaiku(model: String) = model.contains("haiku")
        fun supportsEffort(model: String) = !isHaiku(model)
        fun supportsServerFallback(model: String) = model in setOf(
            "claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5",
        )

        /** Pure, so the mapping from the tool input to [ModelOutput] is tested without HTTP. */
        fun parse(input: Map<String, Any?>): ModelOutput {
            val summary = input["summary"] as? String ?: ""
            val items = (input["suggestions"] as? List<*>).orEmpty().mapNotNull { raw ->
                val m = raw as? Map<*, *> ?: return@mapNotNull null
                ModelItem(
                    candidateId = m["candidate_id"] as? String ?: return@mapNotNull null,
                    headline = m["headline"] as? String ?: "",
                    rationale = m["rationale"] as? String ?: "",
                    lookFor = m["look_for"] as? String ?: "",
                    sourceUrls = (m["source_urls"] as? List<*>).orEmpty().filterIsInstance<String>(),
                )
            }
            return ModelOutput(summary, items)
        }
    }
}
