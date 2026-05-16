package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.Token

internal object RsvpLaunchSnapshotStore {
    private const val MAX_SNAPSHOTS = 2
    private const val SNAPSHOT_TTL_MS = 60_000L

    private data class SnapshotKey(
        val bookId: String,
        val chapterIndex: Int,
    )

    private data class Snapshot(
        val tokens: List<Token>,
        val createdAtMs: Long,
    )

    private val snapshots = LinkedHashMap<SnapshotKey, Snapshot>()

    fun put(
        bookId: String,
        chapterIndex: Int,
        tokens: List<Token>,
    ) {
        val key = SnapshotKey(bookId, chapterIndex)
        if (tokens.isEmpty()) {
            snapshots.remove(key)
            return
        }
        pruneExpired()
        snapshots.remove(key)
        snapshots[key] = Snapshot(tokens = tokens, createdAtMs = System.currentTimeMillis())
        trimToMaxSize()
    }

    fun tokensFor(
        bookId: String,
        chapterIndex: Int,
    ): List<Token> {
        pruneExpired()
        return snapshots[SnapshotKey(bookId, chapterIndex)]?.tokens.orEmpty()
    }

    fun clear(
        bookId: String,
        chapterIndex: Int,
    ) {
        snapshots.remove(SnapshotKey(bookId, chapterIndex))
    }

    private fun pruneExpired() {
        snapshots.entries.removeAll { it.value.isExpired() }
    }

    private fun trimToMaxSize() {
        while (snapshots.size > MAX_SNAPSHOTS) {
            val oldestKey = snapshots.keys.firstOrNull() ?: return
            snapshots.remove(oldestKey)
        }
    }

    private fun Snapshot.isExpired(): Boolean =
        System.currentTimeMillis() - createdAtMs > SNAPSHOT_TTL_MS
}
