package com.kairo.reader.core.tokenization

internal object PageNumberHeuristics {
    fun shouldStripStandalonePageNumbers(html: String): Boolean {
        if (html.isBlank()) return false
        return PAGE_BREAK_ATTRIBUTE_REGEX.containsMatchIn(html) ||
            PAGE_NUMBER_MARKUP_REGEX.containsMatchIn(html) ||
            PAGE_NUMBER_ARIA_REGEX.containsMatchIn(html)
    }

    fun hasExplicitPageNumberMarkup(html: String): Boolean =
        html.isNotBlank() &&
            (
                PAGE_NUMBER_MARKUP_REGEX.containsMatchIn(html) ||
                    PAGE_NUMBER_ARIA_REGEX.containsMatchIn(html)
            )

    fun stripStandalonePageNumbers(
        text: String,
        allowSingleRomanNumeral: Boolean = false,
        allowSingleExplicitCandidate: Boolean = false,
    ): String {
        val lines = text.lines()
        val candidates =
            lines.mapIndexedNotNull { index, line ->
                val value =
                    pageNumberValue(
                        line.trim(),
                        allowSingleRomanNumeral = allowSingleRomanNumeral,
                    ) ?: return@mapIndexedNotNull null
                PageNumberCandidate(index, value)
            }
        val shouldStrip =
            hasSequentialPageNumberEvidence(candidates) ||
                (allowSingleExplicitCandidate && candidates.size == 1)
        if (!shouldStrip) return text
        val candidateIndexes = candidates.mapTo(mutableSetOf()) { it.index }
        return lines
            .filterIndexed { index, _ -> index !in candidateIndexes }
            .joinToString("\n")
    }

    private fun hasSequentialPageNumberEvidence(candidates: List<PageNumberCandidate>): Boolean {
        if (candidates.size < MIN_SEQUENTIAL_PAGE_NUMBER_COUNT) return false
        return candidates
            .map { it.value }
            .windowed(MIN_SEQUENTIAL_PAGE_NUMBER_COUNT)
            .any { values ->
                values.zipWithNext().all { (previous, next) -> next == previous + 1 }
            }
    }

    private fun pageNumberValue(
        text: String,
        allowSingleRomanNumeral: Boolean,
    ): Int? {
        if (text.isEmpty()) return null
        if (text.all { it.isDigit() }) {
            return text.toIntOrNull()?.takeIf { it in 1..MAX_REASONABLE_PAGE_NUMBER }
        }
        if (!ROMAN_NUMERAL_REGEX.matches(text)) return null
        if (!allowSingleRomanNumeral && text.length <= 1) return null
        return romanNumeralValue(text)?.takeIf { it in 1..MAX_REASONABLE_PAGE_NUMBER }
    }

    private fun romanNumeralValue(text: String): Int? {
        var total = 0
        var previous = 0
        val upper = text.uppercase()
        for (index in upper.indices.reversed()) {
            val char = upper[index]
            val value =
                when (char) {
                    'I' -> 1
                    'V' -> 5
                    'X' -> 10
                    'L' -> 50
                    'C' -> 100
                    'D' -> 500
                    'M' -> 1000
                    else -> return null
                }
            if (value < previous) {
                total -= value
            } else {
                total += value
                previous = value
            }
        }
        return total
    }

    private data class PageNumberCandidate(
        val index: Int,
        val value: Int,
    )

    private val PAGE_BREAK_ATTRIBUTE_REGEX =
        Regex(
            """\b(?:epub:type|role)\s*=\s*['"][^'"]*\b(?:pagebreak|doc-pagebreak)\b[^'"]*['"]""",
            RegexOption.IGNORE_CASE,
        )

    private val PAGE_NUMBER_MARKUP_REGEX =
        Regex(
            """\b(?:class|id)\s*=\s*['"][^'"]*\b(?:pagenum|page[\s_-]?(?:num|number|no)|pgnum|folio|pagebreak)\b[^'"]*['"]""",
            RegexOption.IGNORE_CASE,
        )

    private val PAGE_NUMBER_ARIA_REGEX =
        Regex(
            """\baria-label\s*=\s*['"][^'"]*\bpage\b[^'"]*['"]""",
            RegexOption.IGNORE_CASE,
        )

    private val ROMAN_NUMERAL_REGEX = Regex("^[ivxlcdm]+$", RegexOption.IGNORE_CASE)
    private const val MIN_SEQUENTIAL_PAGE_NUMBER_COUNT = 3
    private const val MAX_REASONABLE_PAGE_NUMBER = 10_000
}
