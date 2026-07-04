package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType

internal fun buildVisualChapterPages(
    textPages: List<ChapterPage>,
    blocks: List<ReaderBlock>,
    tokens: List<Token>,
): List<ChapterPage> {
    if (textPages.isEmpty()) {
        return when {
            blocks.any { it is ReaderImageBlock } ->
                blocks
                    .filterIsInstance<ReaderImageBlock>()
                    .mapIndexed { pageIndex, block ->
                        ChapterPage(
                            index = pageIndex,
                            startTokenIndex = 0,
                            endTokenIndex = 0,
                            wordCount = 0,
                            kind = ChapterPageKind.IMAGE,
                            imagePath = block.imagePath,
                            imageIndex = block.index,
                            focusTokenIndex = 0,
                        )
                    }
            tokens.any { it.type == TokenType.PAGE_BREAK } ->
                listOf(
                    ChapterPage(
                        index = 0,
                        startTokenIndex = 0,
                        endTokenIndex = 0,
                        wordCount = 0,
                        kind = ChapterPageKind.BLANK,
                        focusTokenIndex = 0,
                    ),
                )
            else -> emptyList()
        }
    }

    val visualPages = mutableListOf<ChapterPage>()

    fun appendTextPage(
        page: ChapterPage,
        startTokenIndex: Int,
        endTokenIndex: Int,
    ) {
        val wordCount =
            tokens
                .subList(startTokenIndex, endTokenIndex + 1)
                .count { it.type == TokenType.WORD }
        if (wordCount <= 0) return
        val focusTokenIndex =
            (startTokenIndex..endTokenIndex)
                .firstOrNull { index -> tokens.getOrNull(index)?.type == TokenType.WORD }
                ?: startTokenIndex
        visualPages += page.copy(
            index = visualPages.size,
            startTokenIndex = startTokenIndex,
            endTokenIndex = endTokenIndex,
            wordCount = wordCount,
            kind = ChapterPageKind.TEXT,
            imagePath = null,
            imageIndex = null,
            focusTokenIndex = focusTokenIndex,
        )
    }

    textPages.forEach { page ->
        appendTextPage(
            page = page,
            startTokenIndex = page.startTokenIndex,
            endTokenIndex = page.endTokenIndex,
        )
    }

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
            is ReaderImageBlock -> {
                if (isImageAnchoredToPage(block, pageStart, pageEnd)) {
                    sliced.add(block)
                }
            }
        }
    }

    return sliced
}

private fun isImageAnchoredToPage(
    block: ReaderImageBlock,
    pageStart: Int,
    pageEnd: Int,
): Boolean {
    val anchorTokenIndex = block.anchorTokenIndex ?: return pageStart == 0
    return anchorTokenIndex in pageStart..pageEnd
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
