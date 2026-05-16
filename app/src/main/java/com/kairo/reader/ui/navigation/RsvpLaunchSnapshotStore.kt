package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.Token

internal object RsvpLaunchSnapshotStore {
    private data class Snapshot(
        val bookId: String,
        val chapterIndex: Int,
        val tokens: List<Token>,
    )

    private var snapshot: Snapshot? = null

    fun put(
        bookId: String,
        chapterIndex: Int,
        tokens: List<Token>,
    ) {
        if (tokens.isEmpty()) return
        snapshot = Snapshot(bookId = bookId, chapterIndex = chapterIndex, tokens = tokens)
    }

    fun tokensFor(
        bookId: String,
        chapterIndex: Int,
    ): List<Token> =
        snapshot
            ?.takeIf { it.bookId == bookId && it.chapterIndex == chapterIndex }
            ?.tokens
            .orEmpty()
}
