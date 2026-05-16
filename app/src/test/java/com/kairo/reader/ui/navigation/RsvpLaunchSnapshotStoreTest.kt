package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpLaunchSnapshotStoreTest {
    @Test
    fun put_keepsOnlyTwoMostRecentSnapshots() {
        val first = listOf(word("One"))
        val second = listOf(word("Two"))
        val third = listOf(word("Three"))

        RsvpLaunchSnapshotStore.put(bookId = "bounded-1", chapterIndex = 0, tokens = first)
        RsvpLaunchSnapshotStore.put(bookId = "bounded-2", chapterIndex = 0, tokens = second)
        RsvpLaunchSnapshotStore.put(bookId = "bounded-3", chapterIndex = 0, tokens = third)

        assertTrue(RsvpLaunchSnapshotStore.tokensFor("bounded-1", 0).isEmpty())
        assertEquals(second, RsvpLaunchSnapshotStore.tokensFor("bounded-2", 0))
        assertEquals(third, RsvpLaunchSnapshotStore.tokensFor("bounded-3", 0))

        RsvpLaunchSnapshotStore.clear("bounded-2", 0)
        RsvpLaunchSnapshotStore.clear("bounded-3", 0)
    }

    @Test
    fun put_withEmptyTokensClearsExistingSnapshot() {
        val tokens = listOf(word("Cached"))

        RsvpLaunchSnapshotStore.put(bookId = "empty-clear", chapterIndex = 1, tokens = tokens)
        RsvpLaunchSnapshotStore.put(bookId = "empty-clear", chapterIndex = 1, tokens = emptyList())

        assertTrue(RsvpLaunchSnapshotStore.tokensFor("empty-clear", 1).isEmpty())
    }

    private fun word(text: String): Token = Token(text = text, type = TokenType.WORD)
}
