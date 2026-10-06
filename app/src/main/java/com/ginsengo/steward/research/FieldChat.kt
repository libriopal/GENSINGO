package com.ginsengo.steward.research

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The field chat (F.1, the owner's request: "a persistent live LLM chat with suggestions and memory
 * for when online"). Provider-neutral parts: the stored conversation, the memory notes, the prompt.
 * The calls live in [ClaudeChatClient] (Anthropic SDK) and [GeminiChatClient] (Gemini REST).
 *
 * Privacy, the same line the research call holds: the model is told the ~11 km cell the owner is
 * in and never a coordinate; places are described by distance and direction from the owner, which
 * locate nothing without the owner's own position. The conversation and the notes are a file in
 * app-private storage, excluded from every backup; nothing is logged.
 */
@Serializable
data class ChatTurn(val role: String, val text: String, val time: Long) {
    companion object {
        const val USER = "user"
        const val ASSISTANT = "assistant"
    }
}

@Serializable
data class ChatState(val turns: List<ChatTurn> = emptyList(), val notes: List<String> = emptyList())

/** What a provider returns for one exchange: the reply and any notes the model chose to keep. */
data class ChatReply(val text: String, val notes: List<String>, val model: String)

fun interface ChatClient {
    /** [turns] ends with the user's new message, already carrying [ChatPrompt.withContext]. */
    suspend fun reply(system: String, turns: List<ChatTurn>): ChatReply
}

/** The conversation on disk: one small JSON file. */
class ChatStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(): ChatState = runCatching { json.decodeFromString(ChatState.serializer(), file.readText()) }.getOrDefault(ChatState())

    @Synchronized
    fun save(state: ChatState) {
        val trimmed = state.copy(
            turns = state.turns.takeLast(MAX_STORED_TURNS),
            notes = state.notes.takeLast(MAX_NOTES),
        )
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(ChatState.serializer(), trimmed))
            tmp.renameTo(file)
        }
    }

    companion object {
        const val MAX_STORED_TURNS = 400
        const val MAX_NOTES = 40
    }
}

object ChatPrompt {

    /** The turns sent with each request: the recent conversation, not all of it. */
    const val SENT_TURNS = 30
    const val MAX_NOTE_CHARS = 240
    const val REMEMBER_TOOL = "remember"

    val SYSTEM = """
You are the field companion inside Gensingo, an Android app that a ginseng digger carries while hiking in the eastern US. The app computes, on the phone, a terrain-based habitat model for American ginseng (Panax quinquefolius): slope, aspect, position on slope, elevation band, moisture from upslope drainage. It ranks places within 10 miles, records the owner's finds, tracks, and where they have walked, and shows OpenStreetMap logging roads and trails.

How to help:
- Give practical, specific field advice: which ground to check first and why, what to look for on arrival (companion plants such as black cohosh, blue cohosh, rattlesnake fern, maidenhair fern, goldenseal, spicebush; canopy of sugar maple, tulip poplar, basswood, white ash), how to read a cove, how to plan a walk with the time and light left.
- Use the field context given with each message: the season status, the ranked places with their terrain scores and factors, the owner's saved places and finds counts. Refer to ranked places by their number (#1, #2...) and to saved places by name.
- Terrain scores are a model estimate from elevation data; they cannot see soil calcium, canopy, deer browse or harvest pressure. Say so when it matters. Never present a score as a sighting.
- Legality: never say digging is legal anywhere. Season status and land rules come from the app; on land the owner does not own, permission or a permit is needed (national forests issue permits in some states; national parks prohibit digging). Encourage the 3-prong minimum, planting the berries of any plant harvested, and leaving young plants.
- Keep replies short and usable on a phone in the woods: a few sentences or a short list. End with one concrete next step when there is one.
- When the owner tells you something durable about themselves or their ground (where they usually hunt by county, what has worked, what to avoid, their goals), call the ${REMEMBER_TOOL} tool with one short note. Do not store coordinates or anything that pinpoints a patch.
- You do not know the owner's exact position, only an approximate 11 km area; never ask for coordinates.
""".trimIndent()

    /** The system prompt plus the owner's kept notes (stable between turns, so it caches). */
    fun system(notes: List<String>): String =
        if (notes.isEmpty()) SYSTEM
        else SYSTEM + "\n\nWhat you have learned about this owner (their notes, keep using them):\n" +
            notes.joinToString("\n") { "- $it" }

    /** The user's message with the current field context in front of it. */
    fun withContext(context: String, message: String): String =
        if (context.isBlank()) message else "[Field context, from the app]\n$context\n[End of context]\n\n$message"

    /** Starter questions shown above the input. */
    val QUICK = listOf(
        "Where should I look first today, and why?",
        "Plan a 3-hour walk through the best spots",
        "What should I check when I reach #1?",
        "Is it in season here, and what are the rules?",
        "Which companion plants mean good ground?",
        "I found nothing at the last spot. What next?",
    )

    fun cleanNote(raw: String): String? =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_NOTE_CHARS).takeIf { it.length >= 3 }
}
