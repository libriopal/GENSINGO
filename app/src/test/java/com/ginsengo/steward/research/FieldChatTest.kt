package com.ginsengo.steward.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** F.1: the field chat's stored conversation, notes and prompt rules. */
class FieldChatTest {

    @Test
    fun theConversationSurvivesARestartAndIsBounded() {
        val f = File.createTempFile("chat", ".json")
        val store = ChatStore(f)
        val turns = (1..(ChatStore.MAX_STORED_TURNS + 10)).map { ChatTurn(if (it % 2 == 1) ChatTurn.USER else ChatTurn.ASSISTANT, "m$it", it.toLong()) }
        store.save(ChatState(turns, listOf("hunts Haywood County", "prefers north coves")))
        val back = ChatStore(f).load()
        assertEquals(ChatStore.MAX_STORED_TURNS, back.turns.size)
        assertEquals("m${ChatStore.MAX_STORED_TURNS + 10}", back.turns.last().text)
        assertEquals(listOf("hunts Haywood County", "prefers north coves"), back.notes)
        f.delete()
    }

    @Test
    fun aMissingOrBrokenFileIsAnEmptyConversation() {
        val f = File.createTempFile("chat", ".json")
        f.writeText("{not json")
        assertEquals(ChatState(), ChatStore(f).load())
        f.delete()
        assertEquals(ChatState(), ChatStore(f).load())
    }

    @Test
    fun aRequestNeverStartsWithTheModelsTurn() {
        val t = listOf(ChatTurn(ChatTurn.ASSISTANT, "a", 1), ChatTurn(ChatTurn.USER, "q", 2), ChatTurn(ChatTurn.ASSISTANT, "b", 3))
        assertEquals("q", FieldChatRepository.dropLeadingAssistant(t).first().text)
    }

    @Test
    fun notesAreTrimmedAndTheContextRidesOnlyInTheNewMessage() {
        assertEquals("hunts Haywood County", ChatPrompt.cleanNote("  hunts   Haywood\nCounty "))
        assertEquals(null, ChatPrompt.cleanNote(" a "))
        assertEquals(ChatPrompt.MAX_NOTE_CHARS, ChatPrompt.cleanNote("x".repeat(1000))!!.length)
        val m = ChatPrompt.withContext("Area: about 35.55 N", "Where first?")
        assertTrue(m.startsWith("[Field context, from the app]") && m.endsWith("Where first?"))
        assertEquals("Where first?", ChatPrompt.withContext("", "Where first?"))
        assertTrue(ChatPrompt.system(listOf("prefers north coves")).contains("- prefers north coves"))
        assertFalse(ChatPrompt.system(emptyList()).contains("What you have learned"))
    }

    @Test
    fun geminiNotesAreTakenOutOfTheReply() {
        val json = """{"candidates":[{"content":{"parts":[{"text":"Check #2 first: north-facing cove.\nNOTE: hunts Haywood County"}]}}]}"""
        val r = GeminiChatClient.parse(json, "gemini-x")
        assertEquals("Check #2 first: north-facing cove.", r.text)
        assertEquals(listOf("hunts Haywood County"), r.notes)
    }

    /** The system prompt holds the privacy and legality lines the app promises. */
    @Test
    fun theSystemPromptKeepsThePrivacyAndLegalityRules() {
        val s = ChatPrompt.SYSTEM
        assertTrue(s.contains("never ask for coordinates"))
        assertTrue(s.contains("never say digging is legal"))
        assertTrue(s.contains("Never present a score as a sighting"))
    }
}
