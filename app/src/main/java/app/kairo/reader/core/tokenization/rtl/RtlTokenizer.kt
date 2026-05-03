package app.kairo.reader.core.tokenization.rtl

import app.kairo.reader.core.model.Chapter
import app.kairo.reader.core.model.Token
import app.kairo.reader.core.model.withoutInlinePhysicalPageBreaks
import app.kairo.reader.core.tokenization.ChapterTokenizer

class RtlTokenizer(
    config: RtlSegmenterConfig = RtlSegmenterConfig(),
) : ChapterTokenizer {
    private val segmenter = RtlSegmenter(config)

    override fun tokenize(chapter: Chapter): List<Token> {
        val cleanedText =
            if (RtlTextNormalizer.shouldStripPageNumbers(chapter.htmlContent)) {
                RtlTextNormalizer.stripStandalonePageNumbers(chapter.plainText)
            } else {
                chapter.plainText
            }
        val normalized = RtlTextNormalizer.normalize(cleanedText)
        if (normalized.isEmpty()) return emptyList()

        val withPageBreaks = RtlTextNormalizer.normalizePageBreakMarkers(normalized)
        val paragraphs = RtlParagraphSplitter.split(withPageBreaks)
        val tokens = mutableListOf<Token>()

        paragraphs.forEachIndexed { index, paragraph ->
            val isPageBreak = RtlParagraphSplitter.isPageBreakParagraph(paragraph)
            if (isPageBreak) {
                tokens += RtlTokenFactory.pageBreak(RtlParagraphSplitter.pageBreakText(paragraph))
            } else {
                tokens += segmenter.tokenizeParagraph(paragraph)
            }

            val nextParagraph = paragraphs.getOrNull(index + 1)
            val nextIsPageBreak =
                nextParagraph?.let { RtlParagraphSplitter.isPageBreakParagraph(it) } == true
            if (index < paragraphs.lastIndex && !isPageBreak && !nextIsPageBreak) {
                tokens += RtlTokenFactory.paragraphBreak()
            }
        }

        return RtlLinkApplier.apply(
            tokens.withoutInlinePhysicalPageBreaks().toMutableList(),
            chapter,
            segmenter::tokenizeInlineText,
        )
    }
}
