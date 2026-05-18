package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType

internal fun buildVisualChapterPages(
    textPages: List<ChapterPage>,
    blocks: List<ReaderBlock>,
    tokens: List<Token>,
): List<ChapterPage> {
    if (textPages.isEmpty() && blocks.none { it is ReaderImageBlock }) return emptyList()

    val visualPages = mutableListOf<ChapterPage>()
    val addedTextPageIndexes = mutableSetOf<Int>()

    fun appendTextPage(page: ChapterPage) {
        if (!addedTextPageIndexes.add(page.index)) return
        visualPages += page.copy(
            index = visualPages.size,
            kind = ChapterPageKind.TEXT,
            imagePath = null,
            imageIndex = null,
            focusTokenIndex = page.startTokenIndex,
        )
    }

    fun appendTextPagesForRange(
        startTokenIndex: Int,
        endTokenIndex: Int,
    ) {
        textPages
            .filter { page ->
                page.index !in addedTextPageIndexes &&
                    page.endTokenIndex >= startTokenIndex &&
                    page.startTokenIndex <= endTokenIndex
            }.forEach(::appendTextPage)
    }

    blocks.forEach { block ->
        when (block) {
            is ReaderParagraphBlock -> {
                val paragraph = block.paragraph
                val endTokenIndex = paragraph.startIndex + paragraph.tokens.size - 1
                appendTextPagesForRange(paragraph.startIndex, endTokenIndex)
            }
            is ReaderImageBlock -> {
                val anchorTokenIndex =
                    imagePageAnchorTokenIndex(
                        tokens = tokens,
                        visualPages = visualPages,
                        textPages = textPages,
                        addedTextPageIndexes = addedTextPageIndexes,
                    )
                visualPages += ChapterPage(
                    index = visualPages.size,
                    startTokenIndex = anchorTokenIndex,
                    endTokenIndex = anchorTokenIndex,
                    wordCount = 0,
                    kind = ChapterPageKind.IMAGE,
                    imagePath = block.imagePath,
                    imageIndex = block.index,
                    focusTokenIndex = anchorTokenIndex,
                )
            }
        }
    }

    textPages.forEach(::appendTextPage)
    return visualPages
}

internal fun sliceBlocksForPage(
    blocks: List<ReaderBlock>,
    page: ChapterPage,
): List<ReaderBlock> =
    when (page.kind) {
        ChapterPageKind.TEXT ->
            sliceTextBlocksForPage(
                blocks = blocks,
                pageStart = page.startTokenIndex,
                pageEnd = page.endTokenIndex,
            )
        ChapterPageKind.IMAGE -> sliceImageBlocksForPage(blocks, page)
        ChapterPageKind.BLANK -> emptyList()
    }

private fun sliceTextBlocksForPage(
    blocks: List<ReaderBlock>,
    pageStart: Int,
    pageEnd: Int,
): List<ReaderBlock> {
    if (blocks.isEmpty()) return emptyList()
    val sliced = mutableListOf<ReaderBlock>()

    for (block in blocks) {
        when (block) {
            is ReaderParagraphBlock -> {
                val paragraph = block.paragraph
                val blockStart = paragraph.startIndex
                val blockEnd = paragraph.startIndex + paragraph.tokens.size - 1

                if (blockEnd < pageStart) {
                    continue
                }
                if (blockStart > pageEnd) break

                val localStart = (pageStart - blockStart).coerceAtLeast(0)
                val localEnd =
                    (pageEnd - blockStart).coerceAtMost(paragraph.tokens.lastIndex)
                if (localStart > localEnd) {
                    continue
                }

                val slicedParagraph =
                    if (localStart == 0 && localEnd == paragraph.tokens.lastIndex) {
                        paragraph
                    } else {
                        Paragraph(
                            tokens = paragraph.tokens.subList(localStart, localEnd + 1),
                            startIndex = blockStart + localStart,
                        )
                    }

                sliced.add(ReaderParagraphBlock(slicedParagraph))
            }
            is ReaderImageBlock -> Unit
        }
    }

    return sliced
}

private fun sliceImageBlocksForPage(
    blocks: List<ReaderBlock>,
    page: ChapterPage,
): List<ReaderBlock> {
    val imageIndex = page.imageIndex
    val imagePath = page.imagePath
    return blocks.filter { block ->
        block is ReaderImageBlock &&
            (imageIndex == null || block.index == imageIndex) &&
            (imagePath == null || block.imagePath == imagePath)
    }
}

private fun imagePageAnchorTokenIndex(
    tokens: List<Token>,
    visualPages: List<ChapterPage>,
    textPages: List<ChapterPage>,
    addedTextPageIndexes: Set<Int>,
): Int {
    if (tokens.isEmpty()) return 0
    val previousTextPage = visualPages.lastOrNull { it.kind == ChapterPageKind.TEXT }
    val nextTextPage = textPages.firstOrNull { it.index !in addedTextPageIndexes }
    val firstWordIndex = tokens.indexOfFirst { it.type == TokenType.WORD }.takeIf { it >= 0 }
    val anchor =
        previousTextPage?.endTokenIndex
            ?: nextTextPage?.startTokenIndex
            ?: firstWordIndex
            ?: 0
    return anchor.coerceIn(0, tokens.lastIndex)
}
