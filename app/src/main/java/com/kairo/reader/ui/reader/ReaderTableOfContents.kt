package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.TableOfContentsEntry

internal fun resolveActiveTableOfContentsEntry(
    entries: List<TableOfContentsEntry>,
    chapterIndex: Int,
    focusIndex: Int,
    chapterData: ChapterData?,
): TableOfContentsEntry? {
    if (entries.isEmpty()) return null
    val linkedEntries = entries.filter { it.target != null }
    val entriesInCurrentChapter =
        linkedEntries.filter { entry -> entry.target?.chapterIndex == chapterIndex }
    if (entriesInCurrentChapter.isNotEmpty() && chapterData != null) {
        val positionedEntries =
            entriesInCurrentChapter.map { entry ->
                val target = requireNotNull(entry.target)
                val tokenIndex =
                    ReaderTextPositionResolver.resolveTokenIndex(
                        plainText = chapterData.plainText,
                        tokens = chapterData.tokens,
                        characterOffset = target.characterOffset,
                    )
                entry to tokenIndex
            }
        return positionedEntries.lastOrNull { (_, tokenIndex) -> tokenIndex <= focusIndex }?.first
            ?: positionedEntries.first().first
    }
    return linkedEntries.lastOrNull { entry ->
        val targetChapterIndex = entry.target?.chapterIndex ?: return@lastOrNull false
        targetChapterIndex <= chapterIndex
    }
}
