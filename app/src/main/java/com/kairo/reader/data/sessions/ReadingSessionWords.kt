package com.kairo.reader.data.sessions

import kotlin.math.abs

fun estimateWordsRead(
    bookWordCounts: List<Int>,
    startChapterIndex: Int,
    startWordIndex: Int,
    endChapterIndex: Int,
    endWordIndex: Int,
): Int {
    if (bookWordCounts.isEmpty()) return abs(endWordIndex - startWordIndex)
    val safeStartChapter = startChapterIndex.coerceIn(bookWordCounts.indices)
    val safeEndChapter = endChapterIndex.coerceIn(bookWordCounts.indices)
    val startAbsolute = absoluteWordIndex(bookWordCounts, safeStartChapter, startWordIndex)
    val endAbsolute = absoluteWordIndex(bookWordCounts, safeEndChapter, endWordIndex)
    return abs(endAbsolute - startAbsolute)
}

private fun absoluteWordIndex(
    bookWordCounts: List<Int>,
    chapterIndex: Int,
    wordIndex: Int,
): Int =
    bookWordCounts.take(chapterIndex).sum() +
        wordIndex.coerceIn(0, bookWordCounts[chapterIndex].coerceAtLeast(0))
