package app.kairo.reader.ui.library

import app.kairo.reader.core.model.Book
import app.kairo.reader.core.model.ReadingPosition
import app.kairo.reader.core.model.countWords
import app.kairo.reader.core.model.estimateMinutesForWords
import kotlin.math.roundToInt

data class LibraryBookProgress(
    val percentComplete: Int,
    val remainingMinutes: Int?,
)

suspend fun buildLibraryProgress(
    books: List<Book>,
    positions: List<ReadingPosition>,
    estimatedWpmByBookId: Map<String, Int>,
): Map<String, LibraryBookProgress> {
    if (books.isEmpty()) return emptyMap()
    val positionsByBook = positions.associateBy { it.bookId.value }
    val progress = mutableMapOf<String, LibraryBookProgress>()

    for (book in books) {
        val chapterWordCounts =
            book.chapters.map { chapter ->
                when {
                    chapter.wordCount > 0 -> chapter.wordCount
                    chapter.plainText.isNotBlank() -> countWords(chapter.plainText)
                    else -> 0
                }
            }
        val totalWords = chapterWordCounts.sum().coerceAtLeast(0)
        val position = positionsByBook[book.id.value]

        val wordsRead =
            if (position == null || totalWords == 0 || book.chapters.isEmpty()) {
                0
            } else {
                val chapterIndex = position.chapterIndex.coerceIn(0, book.chapters.lastIndex)
                val baseWords = chapterWordCounts.take(chapterIndex).sum()
                val chapterWordCount = chapterWordCounts.getOrNull(chapterIndex) ?: 0
                val chapterWordIndex = position.wordIndex.coerceAtLeast(0)
                val cappedChapterWordIndex =
                    if (chapterWordCount > 0) {
                        chapterWordIndex.coerceAtMost(chapterWordCount)
                    } else {
                        chapterWordIndex
                    }
                baseWords + cappedChapterWordIndex
            }

        val percentComplete =
            if (totalWords == 0) {
                0
            } else {
                ((wordsRead.toDouble() / totalWords.toDouble()) * 100.0)
                    .roundToInt()
                    .coerceIn(0, 100)
            }

        val remainingMinutes =
            if (totalWords > 0) {
                val estimatedWpm = estimatedWpmByBookId[book.id.value] ?: 0
                val remainingWords = (totalWords - wordsRead).coerceAtLeast(0)
                if (estimatedWpm <= 0 || remainingWords == 0) {
                    null
                } else {
                    estimateMinutesForWords(remainingWords, estimatedWpm)
                }
            } else {
                null
            }

        progress[book.id.value] =
            LibraryBookProgress(
                percentComplete = percentComplete,
                remainingMinutes = remainingMinutes,
            )
    }

    return progress
}
