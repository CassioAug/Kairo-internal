package com.kairo.reader.data.books

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.TableOfContentsEntry

internal fun hasCompatibleEpubTocCoordinates(
    existingChapters: List<Chapter>,
    probedChapters: List<Chapter>,
    probedTableOfContents: List<TableOfContentsEntry>,
): Boolean {
    if (existingChapters.size != probedChapters.size) return false
    val existingTextByIndex = existingChapters.associate { chapter -> chapter.index to chapter.plainText }
    val probedTextByIndex = probedChapters.associate { chapter -> chapter.index to chapter.plainText }
    if (existingTextByIndex.size != existingChapters.size ||
        probedTextByIndex.size != probedChapters.size ||
        existingTextByIndex != probedTextByIndex
    ) {
        return false
    }
    return probedTableOfContents.all { entry ->
        entry.target?.let { target ->
            val plainText = existingTextByIndex[target.chapterIndex] ?: return@let false
            target.characterOffset in 0..plainText.length
        } != false
    }
}
