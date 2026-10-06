package com.ginsengo.steward.research

import com.ginsengo.steward.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The field chat's state and its one action, [send] (F.1). Holds the same gates as the research
 * call: the owner's consent ("Research model" switch), a key for the chosen provider, and a
 * connection. The conversation persists across restarts ([ChatStore]); offline, it can still be
 * read, and nothing is queued to send later.
 */
class FieldChatRepository(
    private val store: ChatStore,
    private val settings: SettingsStore,
    private val keys: KeyVault,
    private val isOnline: () -> Boolean,
    /** Tests swap the provider clients; the app builds them from the settings. */
    private val clientFor: (Provider, String, String) -> ChatClient = { p, key, model ->
        when (p) {
            Provider.CLAUDE -> ClaudeChatClient(key, model)
            Provider.GEMINI -> GeminiChatClient(key, model)
        }
    },
) {
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    /** Why a message cannot be sent now, or null when it can. */
    fun blocker(): String? = when {
        !settings.researchConsent -> "Turn on \"Research model\" in Layers (with your own API key) to chat."
        !keys.has(settings.provider) -> "Add your ${settings.provider.label} API key in Layers → Research model to chat."
        !isOnline() -> "Chat needs a connection. Your conversation is kept; ask again when you have signal."
        else -> null
    }

    /** Sends [message] with [context] (the app's field context); returns an error text, or null on success. */
    suspend fun send(message: String, context: String): String? {
        blocker()?.let { return it }
        val provider = settings.provider
        val key = keys.get(provider) ?: return "No API key for ${provider.label}."
        val now = System.currentTimeMillis()
        val before = _state.value
        val withUser = before.copy(turns = before.turns + ChatTurn(ChatTurn.USER, message.trim(), now))
        _state.value = withUser
        val sent = withUser.turns.takeLast(ChatPrompt.SENT_TURNS).toMutableList()
        // The newest message carries the field context; the stored copy stays as the owner typed it.
        sent[sent.lastIndex] = sent.last().copy(text = ChatPrompt.withContext(context, message.trim()))
        return try {
            val reply = clientFor(provider, key, settings.model(provider)).reply(ChatPrompt.system(withUser.notes), dropLeadingAssistant(sent))
            val text = reply.text.ifBlank { "(no answer)" }
            val after = withUser.copy(
                turns = withUser.turns + ChatTurn(ChatTurn.ASSISTANT, text, System.currentTimeMillis()),
                notes = (withUser.notes + reply.notes).distinct(),
            )
            _state.value = after
            store.save(after)
            null
        } catch (e: ResearchFailure) {
            store.save(withUser)
            e.message ?: "The chat failed"
        } catch (e: Exception) {
            store.save(withUser)
            "The chat failed (${e.javaClass.simpleName})"
        }
    }

    fun forgetNote(note: String) {
        val s = _state.value.copy(notes = _state.value.notes - note)
        _state.value = s; store.save(s)
    }

    fun clearConversation() {
        val s = _state.value.copy(turns = emptyList())
        _state.value = s; store.save(s)
    }

    companion object {
        /** A request must start with the owner's turn: a trimmed window can begin mid-exchange. */
        fun dropLeadingAssistant(turns: List<ChatTurn>): List<ChatTurn> = turns.dropWhile { it.role == ChatTurn.ASSISTANT }
    }
}
