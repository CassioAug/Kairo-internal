@file:Suppress("MagicNumber")

package com.kairo.reader.ui.bionic

import kotlin.math.min

private const val BIONIC_MIN_RENDERED_CHUNK_WORDS = 4
private const val BIONIC_TARGET_CHUNK_WORDS = 30
private const val BIONIC_MAX_RENDERED_CHUNK_WORDS = 96
private const val BIONIC_MIN_RENDERED_CHUNK_CHARACTERS = 24
private const val BIONIC_SEMANTIC_HEADROOM_RATIO = 1.18f

internal fun resolveBionicTargetWordCount(
    wordCapacity: Int,
): Int {
    val safeCapacity = wordCapacity.coerceAtLeast(1)
    val targetWithHeadroom =
        (safeCapacity / BIONIC_SEMANTIC_HEADROOM_RATIO)
            .toInt()
            .coerceAtLeast(min(BIONIC_MIN_RENDERED_CHUNK_WORDS, safeCapacity))
    return min(
        BIONIC_TARGET_CHUNK_WORDS,
        targetWithHeadroom,
    )
}

internal fun estimateBionicWordCapacity(
    screenWidthDp: Int,
    fontSizeSp: Float,
    paneLineCount: Int,
    fontScale: Float = 1f,
): Int {
    val safeFontSize =
        fontSizeSp.coerceIn(BIONIC_MIN_FONT_SIZE_SP, BIONIC_MAX_FONT_SIZE_SP) *
            fontScale.coerceAtLeast(0.5f)
    val usableWidth = (min(screenWidthDp, 720) - 88).coerceAtLeast(180)
    val estimatedWordsPerLine = (usableWidth / (safeFontSize * 2.6f)).coerceAtLeast(2f)
    return (estimatedWordsPerLine * paneLineCount.coerceAtLeast(1) * 0.78f)
        .toInt()
        .coerceIn(BIONIC_MIN_RENDERED_CHUNK_WORDS, BIONIC_MAX_RENDERED_CHUNK_WORDS)
}

internal fun estimateBionicCharacterCapacity(
    screenWidthDp: Int,
    fontSizeSp: Float,
    paneLineCount: Int,
    fontScale: Float = 1f,
): Int {
    val safeFontSize =
        fontSizeSp.coerceIn(BIONIC_MIN_FONT_SIZE_SP, BIONIC_MAX_FONT_SIZE_SP) *
            fontScale.coerceAtLeast(0.5f)
    val usableWidth = (min(screenWidthDp, 720) - 88).coerceAtLeast(180)
    val estimatedCharactersPerLine = (usableWidth / (safeFontSize * 0.54f)).coerceAtLeast(8f)
    return (estimatedCharactersPerLine * paneLineCount.coerceAtLeast(1) * 0.86f)
        .toInt()
        .coerceAtLeast(BIONIC_MIN_RENDERED_CHUNK_CHARACTERS)
}

internal fun bionicPaneLineCount(
    screenWidthDp: Int,
    screenHeightDp: Int,
    fontSizeSp: Float = 24f,
    fontScale: Float = 1f,
): Int {
    val isCompactLandscape = screenWidthDp > screenHeightDp && screenHeightDp < 500
    val orientationTarget = if (isCompactLandscape) BIONIC_COMPACT_PANE_LINES else BIONIC_PANE_LINES
    val reservedHeightDp = if (isCompactLandscape) 164f else 204f
    val lineHeightDp =
        fontSizeSp.coerceIn(BIONIC_MIN_FONT_SIZE_SP, BIONIC_MAX_FONT_SIZE_SP) *
            1.48f *
            fontScale.coerceAtLeast(0.5f)
    val linesThatFit =
        ((screenHeightDp - reservedHeightDp) / lineHeightDp)
            .toInt()
            .coerceIn(BIONIC_MIN_PANE_LINES, BIONIC_PANE_LINES)
    return min(orientationTarget, linesThatFit)
}
