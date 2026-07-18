package com.kairo.reader.ui.reader

internal class ReaderChapterCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES,) {
    private val lock = Any()
    private val chapters =
        object : LinkedHashMap<Int, ChapterData>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Int, ChapterData>?,
            ): Boolean = size > maxEntries
        }

    operator fun get(chapterIndex: Int): ChapterData? = synchronized(lock) {
        chapters[chapterIndex]
    }

    operator fun set(chapterIndex: Int, data: ChapterData) {
        synchronized(lock) { chapters[chapterIndex] = data }
    }

    fun contains(chapterIndex: Int): Boolean = synchronized(lock) {
        chapters.containsKey(chapterIndex)
    }

    fun transformAll(transform: (ChapterData) -> ChapterData) {
        synchronized(lock) {
            chapters.entries.forEach { entry -> entry.setValue(transform(entry.value)) }
        }
    }

    fun clear() {
        synchronized(lock) { chapters.clear() }
    }

    private companion object {
        const val INITIAL_CAPACITY = 5
        const val LOAD_FACTOR = 0.75f
        const val DEFAULT_MAX_ENTRIES = 5
    }
}
