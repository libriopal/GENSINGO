package com.ginsengo.steward.research

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.WebSearchTool20250305
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * The field chat through the official Anthropic Java SDK (F.1).
 *
 * One exchange is a short manual loop: the model may search the web (server-side; a `pause_turn`
 * is sent back unchanged so the server resumes) and may call the strict `remember` tool to keep a
 * note about the owner, which is stored and answered with a tool result. The system prompt (with
 * the notes) is cached; the per-message field context rides in the new user message, so the cached
 * prefix survives from one question to the next.
 */
class ClaudeChatClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String? = null,
) : ChatClient {

    override suspend fun reply(system: String, turns: List<ChatTurn>): ChatReply = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .apply { baseUrl?.let { baseUrl(it) } }
            .timeout(Duration.ofSeconds(120))
            .maxRetries(2)
            .build()
        try {
            val history = turns.map { t ->
                MessageParam.builder()
                    .role(if (t.role == ChatTurn.ASSISTANT) MessageParam.Role.ASSISTANT else MessageParam.Role.USER)
                    .content(t.text)
                    .build()
            }.toMutableList()
            val notes = ArrayList<String>()
            val text = StringBuilder()
            var servedBy = model

            repeat(MAX_STEPS) {
                val params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(8_000L)
                    .systemOfTextBlockParams(listOf(
                        TextBlockParam.builder().text(system).cacheControl(CacheControlEphemeral.builder().build()).build()
                    ))
                    .messages(history)
                    .apply {
                        if (model.contains("haiku")) addTool(WebSearchTool20250305.builder().maxUses(MAX_SEARCHES).build())
                        else addTool(WebSearchTool20260209.builder().maxUses(MAX_SEARCHES).build())
                    }
                    .addTool(rememberTool())
                    .apply {
                        if (ClaudeResearchClient.supportsEffort(model)) {
                            outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                        }
                        if (ClaudeResearchClient.supportsServerFallback(model)) {
                            putAdditionalHeader("anthropic-beta", ClaudeResearchClient.FALLBACK_BETA)
                            putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                        }
                    }
                    .build()

                val msg = client.messages().create(params)
                servedBy = msg.model().asString()
                msg.content().forEach { block -> block.text().ifPresent { text.append(it.text()) } }

                val calls = msg.content().mapNotNull { it.toolUse().orElse(null) }.filter { it.name() == ChatPrompt.REMEMBER_TOOL }
                when (msg.stopReason().orElse(null)) {
                    StopReason.TOOL_USE -> {
                        if (calls.isEmpty()) return@withContext ChatReply(text.toString().trim(), notes, servedBy)
                        val results = calls.map { call ->
                            @Suppress("UNCHECKED_CAST")
                            val input = call._input().convert(Map::class.java) as? Map<String, Any?>
                            (input?.get("note") as? String)?.let(ChatPrompt::cleanNote)?.let(notes::add)
                            ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder().toolUseId(call.id()).content("Saved.").build()
                            )
                        }
                        history += msg.toParam()
                        history += MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build()
                    }
                    StopReason.PAUSE_TURN -> history += msg.toParam()
                    StopReason.REFUSAL -> throw ResearchFailure(
                        ResearchFailure.Status.REFUSED,
                        msg.stopDetails().map { d -> listOfNotNull("The model declined", d.explanation().orElse(null)).joinToString(": ") }
                            .orElse("The model declined this message"),
                    )
                    else -> return@withContext ChatReply(text.toString().trim(), notes, servedBy)
                }
            }
            ChatReply(text.toString().trim(), notes, servedBy)
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

    private fun rememberTool(): Tool = Tool.builder()
        .name(ChatPrompt.REMEMBER_TOOL)
        .description("Keep one short, durable note about the owner (habits, county, goals, what worked or failed) for future conversations. Never a coordinate or anything that pinpoints a patch.")
        .strict(true)
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder()
                        .putAdditionalProperty("note", JsonValue.from(mapOf("type" to "string", "description" to "The note, one sentence.")))
                        .build()
                )
                .required(listOf("note"))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build()
        )
        .build()

    companion object {
        const val MAX_STEPS = 5
        const val MAX_SEARCHES = 3L
    }
}
