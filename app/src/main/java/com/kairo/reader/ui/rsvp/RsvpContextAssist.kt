@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "TooManyFunctions",
)

package com.kairo.reader.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.shouldInsertSpaceBeforeToken

internal data class RsvpContextWindow(
    val startIndex: Int,
    val endExclusive: Int,
    val focusStartIndex: Int,
    val focusEndExclusive: Int,
)

private data class RsvpContextContent(
    val previous: AnnotatedString,
    val upcoming: AnnotatedString,
)

internal data class ContextCueSlots(
    val previousWidth: Dp,
    val focusGap: Dp,
    val upcomingWidth: Dp,
    val hasPreviousRoom: Boolean,
    val hasUpcomingRoom: Boolean,
)

internal data class ContextFocusEnvelope(
    val leftReserve: Dp,
    val rightReserve: Dp,
)

@Composable
internal fun BoxScope.RsvpContextAssist(
    context: RsvpUiContext,
    frame: RsvpFrame?,
    bottomChromeInset: Dp,
) {
    val runtime = context.runtime
    val config = context.state.profile.config
    val mode = config.contextAssistMode
    val content = rememberRsvpContextContent(context, frame)
    val visible =
        content != null &&
            mode != RsvpContextAssistMode.OFF &&
            !runtime.showControls &&
            !runtime.showQuickSettings &&
            !runtime.isPositioningMode &&
            !runtime.isExiting

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomChromeInset),
            contentAlignment =
                BiasAlignment(
                    horizontalBias = CENTER_BIAS,
                    verticalBias =
                        runtime.currentVerticalBias.coerceIn(
                            VERTICAL_BIAS_MIN,
                            VERTICAL_BIAS_MAX,
                        ),
                ),
        ) {
            val targetFocusEnvelope =
                rememberContextFocusEnvelope(
                    frames = context.frameState.frames,
                    frameIndex = runtime.frameIndex,
                    fontSizeSp = runtime.currentFontSizeSp,
                    fontFamily = runtime.currentFontFamily,
                    fontWeight = runtime.currentFontWeight,
                )
            val leftReserve =
                animateDpAsState(
                    targetValue = targetFocusEnvelope.leftReserve,
                    animationSpec = tween(durationMillis = CONTEXT_ENVELOPE_ANIMATION_MS),
                    label = "contextLeftReserve",
                ).value
            val rightReserve =
                animateDpAsState(
                    targetValue = targetFocusEnvelope.rightReserve,
                    animationSpec = tween(durationMillis = CONTEXT_ENVELOPE_ANIMATION_MS),
                    label = "contextRightReserve",
                ).value
            val guideBandHeight =
                rememberContextGuideBandHeight(
                    fontSizeSp = runtime.currentFontSizeSp,
                    fontFamily = runtime.currentFontFamily,
                    fontWeight = runtime.currentFontWeight,
                    frame = frame,
                    guideVisible = config.orpGuideEnabled,
                    guideThickness =
                        config.orpGuideThickness
                            .toFloat()
                            .coerceIn(ORP_GUIDE_THICKNESS_MIN, ORP_GUIDE_THICKNESS_MAX),
                )
            RsvpPeripheralContext(
                previous = content?.previous ?: AnnotatedString(""),
                upcoming = content?.upcoming ?: AnnotatedString(""),
                focusLeftReserve = leftReserve,
                focusRightReserve = rightReserve,
                fontSizeSp = runtime.currentFontSizeSp,
                fontFamily = runtime.currentFontFamily,
                fontWeight = runtime.currentFontWeight,
                horizontalBias = runtime.currentHorizontalBias,
                guideBandHeight = guideBandHeight,
            )
        }
    }
}

@Composable
private fun RsvpPeripheralContext(
    previous: AnnotatedString,
    upcoming: AnnotatedString,
    focusLeftReserve: Dp,
    focusRightReserve: Dp,
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
    horizontalBias: Float,
    guideBandHeight: Dp?,
) {
    val contextModifier =
        if (guideBandHeight != null) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ORP_HORIZONTAL_PADDING)
                .height(guideBandHeight)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ORP_HORIZONTAL_PADDING)
        }
    BoxWithConstraints(
        modifier = contextModifier,
        contentAlignment = Alignment.Center,
    ) {
        val slots =
            resolveContextCueSlots(
                availableWidth = maxWidth,
                focusLeftReserve = focusLeftReserve,
                focusRightReserve = focusRightReserve,
                horizontalBias = horizontalBias,
                minimumCueWidth = CONTEXT_MIN_CUE_WIDTH,
                cueInnerPadding = CONTEXT_CUE_INNER_PADDING,
            )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RsvpPeripheralCueText(
                text = if (slots.hasPreviousRoom) previous else AnnotatedString(""),
                textAlign = TextAlign.End,
                fontSizeSp = fontSizeSp,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                modifier = Modifier.width(slots.previousWidth),
            )
            Spacer(modifier = Modifier.width(slots.focusGap))
            RsvpPeripheralCueText(
                text = if (slots.hasUpcomingRoom) upcoming else AnnotatedString(""),
                textAlign = TextAlign.Start,
                fontSizeSp = fontSizeSp,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                modifier = Modifier.width(slots.upcomingWidth),
            )
        }
    }
}

@Composable
private fun rememberContextGuideBandHeight(
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
    frame: RsvpFrame?,
    guideVisible: Boolean,
    guideThickness: Float,
): Dp? {
    if (!guideVisible) return null

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val focusContent = remember(frame?.tokens) { buildOrpTextContent(frame?.tokens.orEmpty()) }
    val baseStyle = MaterialTheme.typography.displayMedium
    val focusStyle =
        remember(fontSizeSp, fontFamily, fontWeight, baseStyle) {
            baseStyle.copy(
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * ORP_TEXT_LINE_HEIGHT_MULTIPLIER).sp,
                fontFamily = resolveFontFamily(fontFamily),
                fontWeight = resolveFontWeight(fontWeight),
                letterSpacing = ORP_LETTER_SPACING_SP.sp,
            )
        }
    val measuredTextHeightPx =
        remember(focusContent, focusStyle, textMeasurer) {
            textMeasurer.measure(
                text = AnnotatedString(focusContent.fullText.ifEmpty { CONTEXT_BASELINE_SAMPLE }),
                style = focusStyle,
                overflow = TextOverflow.Clip,
                maxLines = 1,
                softWrap = false,
                constraints = Constraints(maxWidth = Int.MAX_VALUE),
            ).size.height
        }
    val measuredTextHeight = with(density) { measuredTextHeightPx.toDp() }
    return orpGuideBandHeight(measuredTextHeight, guideThickness)
}

@Composable
private fun rememberContextFocusEnvelope(
    frames: List<RsvpFrame>,
    frameIndex: Int,
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
): ContextFocusEnvelope {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.displayMedium
    val focusStyle =
        remember(fontSizeSp, fontFamily, fontWeight, baseStyle) {
            baseStyle.copy(
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * ORP_TEXT_LINE_HEIGHT_MULTIPLIER).sp,
                fontFamily = resolveFontFamily(fontFamily),
                fontWeight = resolveFontWeight(fontWeight),
                letterSpacing = ORP_LETTER_SPACING_SP.sp,
            )
        }
    val frameRange =
        remember(frameIndex, frames.size) {
            resolveContextEnvelopeFrameRange(
                frameIndex = frameIndex,
                frameCount = frames.size,
                blockSize = CONTEXT_ENVELOPE_BLOCK_FRAMES,
            )
        }
    val extentsPx =
        remember(frames, frameRange, focusStyle, textMeasurer) {
            var maxLeftPx = 0f
            var maxRightPx = 0f
            frameRange.forEach { index ->
                val content = buildOrpTextContent(frames[index].tokens)
                val text = content.fullText
                if (text.isNotEmpty()) {
                    val measured =
                        textMeasurer.measure(
                            text = AnnotatedString(text),
                            style = focusStyle,
                            overflow = TextOverflow.Clip,
                            maxLines = 1,
                            softWrap = false,
                            constraints = Constraints(maxWidth = Int.MAX_VALUE),
                        )
                    val pivot = content.pivotPosition.coerceIn(0, text.lastIndex)
                    val pivotBox = measured.getBoundingBox(pivot)
                    val pivotCenter = pivotBox.left + (pivotBox.width / BIAS_SCALE_FACTOR)
                    maxLeftPx = maxOf(maxLeftPx, pivotCenter)
                    maxRightPx = maxOf(maxRightPx, measured.size.width.toFloat() - pivotCenter)
                }
            }
            maxLeftPx to maxRightPx
        }
    val leftReserve =
        (with(density) { extentsPx.first.toDp() } + CONTEXT_FOCUS_SIDE_PADDING)
            .coerceAtLeast(CONTEXT_MIN_FOCUS_SIDE_RESERVE)
    val rightReserve =
        (with(density) { extentsPx.second.toDp() } + CONTEXT_FOCUS_SIDE_PADDING)
            .coerceAtLeast(CONTEXT_MIN_FOCUS_SIDE_RESERVE)
    return ContextFocusEnvelope(leftReserve = leftReserve, rightReserve = rightReserve)
}

@Composable
private fun RsvpPeripheralCueText(
    text: AnnotatedString,
    textAlign: TextAlign,
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
    modifier: Modifier = Modifier,
) {
    val effectiveFontSizeSp = stableContextCueFontSizeSp(fontSizeSp)
    Text(
        text = text,
        style =
            MaterialTheme.typography.displayMedium.copy(
                fontSize = effectiveFontSizeSp.sp,
                lineHeight = (effectiveFontSizeSp * ORP_TEXT_LINE_HEIGHT_MULTIPLIER).sp,
                fontFamily = resolveFontFamily(fontFamily),
                fontWeight = resolveFontWeight(fontWeight),
                letterSpacing = ORP_LETTER_SPACING_SP.sp,
            ),
        textAlign = textAlign,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

@Composable
private fun rememberRsvpContextContent(
    context: RsvpUiContext,
    frame: RsvpFrame?,
): RsvpContextContent? {
    if (frame == null) return null
    val tokens = context.state.book.tokens
    val mode = context.state.profile.config.contextAssistMode
    if (tokens.isEmpty() || mode == RsvpContextAssistMode.OFF) return null

    val currentTokenIndex = frame.originalTokenIndex.coerceIn(0, tokens.lastIndex)
    val currentToken = tokens[currentTokenIndex]
    val isBoundaryFrame =
        currentToken.type == TokenType.PARAGRAPH_BREAK ||
            currentToken.type == TokenType.PAGE_BREAK
    if (isBoundaryFrame) return null

    val window =
        remember(
            tokens,
            currentTokenIndex,
            frame.nextOriginalTokenIndex,
            mode,
        ) {
            resolveRsvpContextWindow(
                tokens = tokens,
                frameStartIndex = currentTokenIndex,
                frameEndExclusive = frame.nextOriginalTokenIndex,
                mode = mode,
            )
        } ?: return null
    val contextColor = MaterialTheme.colorScheme.onBackground
    val previousWords =
        if (mode == RsvpContextAssistMode.FULL_CLAUSE) {
            CONTEXT_CLAUSE_PREVIOUS_WORDS
        } else {
            CONTEXT_MINIMAL_PREVIOUS_WORDS
        }
    val upcomingWords =
        if (mode == RsvpContextAssistMode.FULL_CLAUSE) {
            CONTEXT_CLAUSE_UPCOMING_WORDS
        } else {
            0
        }
    val previous =
        remember(tokens, window, contextColor, previousWords) {
            buildPeripheralContextText(
                tokens = tokens,
                startIndex = window.startIndex,
                endExclusive = window.focusStartIndex,
                maxWords = previousWords,
                takeLast = true,
                color = contextColor,
                nearestAlpha = CONTEXT_PREVIOUS_NEAREST_ALPHA,
                farthestAlpha = CONTEXT_PREVIOUS_FARTHEST_ALPHA,
            )
        }
    val upcoming =
        remember(tokens, window, contextColor, upcomingWords) {
            if (upcomingWords == 0) {
                AnnotatedString("")
            } else {
                buildPeripheralContextText(
                    tokens = tokens,
                    startIndex = window.focusEndExclusive,
                    endExclusive = window.endExclusive,
                    maxWords = upcomingWords,
                    takeLast = false,
                    color = contextColor,
                    nearestAlpha = CONTEXT_UPCOMING_NEAREST_ALPHA,
                    farthestAlpha = CONTEXT_UPCOMING_FARTHEST_ALPHA,
                )
            }
        }
    return RsvpContextContent(
        previous = previous,
        upcoming = upcoming,
    )
}

internal fun resolveRsvpContextWindow(
    tokens: List<Token>,
    frameStartIndex: Int,
    frameEndExclusive: Int,
    mode: RsvpContextAssistMode,
): RsvpContextWindow? {
    if (tokens.isEmpty() || mode == RsvpContextAssistMode.OFF) return null
    val safeStart = frameStartIndex.coerceIn(0, tokens.lastIndex)
    val focusStart =
        findWordInRange(tokens, safeStart, frameEndExclusive)
            ?: findWordAtOrAfter(tokens, safeStart)
    if (focusStart < 0) return null
    val focusEnd = frameEndExclusive.coerceIn(focusStart + 1, tokens.size)

    val baseRange =
        when (mode) {
            RsvpContextAssistMode.OFF -> return null
            RsvpContextAssistMode.PREVIOUS_WORDS ->
                resolveNearbyWordRange(
                    tokens = tokens,
                    focusIndex = focusStart,
                    wordsBefore = CONTEXT_PREVIOUS_WORDS,
                    wordsAfter = CONTEXT_UPCOMING_WORDS,
                )
            RsvpContextAssistMode.FULL_CLAUSE ->
                resolveClauseRange(
                    tokens = tokens,
                    focusIndex = focusStart,
                )
        }
    return RsvpContextWindow(
        startIndex = baseRange.first,
        endExclusive = baseRange.last + 1,
        focusStartIndex = focusStart,
        focusEndExclusive = focusEnd,
    )
}

private fun resolveNearbyWordRange(
    tokens: List<Token>,
    focusIndex: Int,
    wordsBefore: Int,
    wordsAfter: Int,
): IntRange {
    var start = focusIndex
    var seenBefore = 0
    while (start > 0 && seenBefore < wordsBefore) {
        val candidate = tokens[start - 1]
        if (candidate.isParagraphBoundary() || candidate.isClausePunctuation()) break
        start -= 1
        if (candidate.type == TokenType.WORD) {
            seenBefore += 1
            if (candidate.isClauseBoundary) break
        }
    }

    var endExclusive = (focusIndex + 1).coerceAtMost(tokens.size)
    var seenAfter = 0
    while (endExclusive < tokens.size && seenAfter < wordsAfter) {
        val candidate = tokens[endExclusive]
        if (candidate.isParagraphBoundary()) break
        if (candidate.type == TokenType.WORD && candidate.isClauseBoundary) break
        endExclusive += 1
        if (candidate.isClausePunctuation()) break
        if (candidate.type == TokenType.WORD) seenAfter += 1
    }
    return start until endExclusive
}

private fun resolveClauseRange(
    tokens: List<Token>,
    focusIndex: Int,
): IntRange {
    var start = focusIndex
    while (start > 0) {
        val candidate = tokens[start - 1]
        if (candidate.isParagraphBoundary() || candidate.isClausePunctuation()) break
        start -= 1
        if (candidate.type == TokenType.WORD && candidate.isClauseBoundary) break
    }

    var endExclusive = (focusIndex + 1).coerceAtMost(tokens.size)
    while (endExclusive < tokens.size) {
        val candidate = tokens[endExclusive]
        if (candidate.isParagraphBoundary()) break
        if (candidate.type == TokenType.WORD && candidate.isClauseBoundary) break
        endExclusive += 1
        if (candidate.isClausePunctuation()) break
    }

    val wordCount = tokens.subList(start, endExclusive).count { it.type == TokenType.WORD }
    return if (wordCount <= CONTEXT_MAX_CLAUSE_WORDS) {
        start until endExclusive
    } else {
        resolveNearbyWordRange(
            tokens = tokens,
            focusIndex = focusIndex,
            wordsBefore = CONTEXT_LONG_CLAUSE_PREVIOUS_WORDS,
            wordsAfter = CONTEXT_LONG_CLAUSE_UPCOMING_WORDS,
        )
    }
}

internal fun buildPeripheralContextText(
    tokens: List<Token>,
    startIndex: Int,
    endExclusive: Int,
    maxWords: Int,
    takeLast: Boolean,
    color: Color,
    nearestAlpha: Float,
    farthestAlpha: Float,
): AnnotatedString {
    if (tokens.isEmpty() || maxWords <= 0) return AnnotatedString("")
    val safeStart = startIndex.coerceIn(0, tokens.size)
    val safeEnd = endExclusive.coerceIn(safeStart, tokens.size)
    val wordIndices =
        (safeStart until safeEnd).filter { index -> tokens[index].type == TokenType.WORD }
    if (wordIndices.isEmpty()) return AnnotatedString("")
    val selectedWords =
        if (takeLast) {
            wordIndices.takeLast(maxWords)
        } else {
            wordIndices.take(maxWords)
        }
    val fragmentStart = if (takeLast) selectedWords.first() else safeStart
    val fragmentEnd =
        if (takeLast || selectedWords.size == wordIndices.size) {
            safeEnd
        } else {
            wordIndices[selectedWords.size]
        }

    return buildAnnotatedString {
        var previous: Token? = null
        var renderedIndex = 0
        var wordOrdinal = -1
        for (index in fragmentStart until fragmentEnd) {
            val token = tokens[index]
            if (token.isParagraphBoundary()) continue
            if (shouldInsertSpaceBeforeToken(token, previous, renderedIndex)) append(' ')
            if (token.type == TokenType.WORD) wordOrdinal += 1
            val start = length
            append(token.text)
            val end = length
            val ordinal = wordOrdinal.coerceAtLeast(0)
            val progress =
                if (selectedWords.size <= 1) {
                    1f
                } else {
                    ordinal.toFloat() / (selectedWords.size - 1).toFloat()
                }
            val proximity = if (takeLast) progress else 1f - progress
            val alpha =
                (farthestAlpha + ((nearestAlpha - farthestAlpha) * proximity))
                    .coerceIn(0f, 1f)
            if (end > start) {
                addStyle(
                    SpanStyle(color = color.copy(alpha = alpha)),
                    start,
                    end,
                )
            }
            previous = token
            renderedIndex += 1
        }
    }
}

private fun findWordInRange(
    tokens: List<Token>,
    startIndex: Int,
    endExclusive: Int,
): Int? {
    val safeEnd = endExclusive.coerceIn(startIndex + 1, tokens.size)
    for (index in startIndex until safeEnd) {
        if (tokens[index].type == TokenType.WORD) return index
    }
    return null
}

private fun findWordAtOrAfter(tokens: List<Token>, startIndex: Int): Int {
    for (index in startIndex.coerceAtLeast(0) until tokens.size) {
        if (tokens[index].type == TokenType.WORD) return index
    }
    return -1
}

private fun Token.isParagraphBoundary(): Boolean =
    type == TokenType.PARAGRAPH_BREAK || type == TokenType.PAGE_BREAK

private fun Token.isClausePunctuation(): Boolean =
    type == TokenType.PUNCTUATION && text.any { it in CONTEXT_BOUNDARY_PUNCTUATION }

internal fun stableContextCueFontSizeSp(fontSizeSp: Float): Float =
    fontSizeSp.coerceAtLeast(0f) * CONTEXT_CUE_FONT_SCALE

internal fun resolveContextEnvelopeFrameRange(
    frameIndex: Int,
    frameCount: Int,
    blockSize: Int,
): IntRange {
    if (frameCount <= 0) return IntRange.EMPTY
    val safeBlockSize = blockSize.coerceAtLeast(1)
    val safeFrameIndex = frameIndex.coerceIn(0, frameCount - 1)
    val blockStart = (safeFrameIndex / safeBlockSize) * safeBlockSize
    val endExclusive = (blockStart + (safeBlockSize * 2)).coerceAtMost(frameCount)
    return blockStart until endExclusive
}

internal fun resolveContextCueSlots(
    availableWidth: Dp,
    focusLeftReserve: Dp,
    focusRightReserve: Dp,
    horizontalBias: Float,
    minimumCueWidth: Dp,
    cueInnerPadding: Dp,
): ContextCueSlots {
    val safeWidth = availableWidth.coerceAtLeast(0.dp)
    val safeBias = horizontalBias.coerceIn(HORIZONTAL_BIAS_MIN, HORIZONTAL_BIAS_MAX)
    val safeCuePadding = cueInnerPadding.coerceAtLeast(0.dp)
    val guideFraction =
        ((safeBias + ONE_FLOAT) / BIAS_SCALE_FACTOR)
            .coerceIn(ORP_BIAS_FRACTION_MIN, ORP_BIAS_FRACTION_MAX)
    val guidePosition = safeWidth * guideFraction
    val safeLeftReserve = focusLeftReserve.coerceAtLeast(0.dp)
    val safeRightReserve = focusRightReserve.coerceAtLeast(0.dp)
    val previousWidth =
        (guidePosition - safeLeftReserve - safeCuePadding).coerceIn(0.dp, safeWidth)
    val upcomingStart =
        (guidePosition + safeRightReserve + safeCuePadding).coerceIn(0.dp, safeWidth)
    val upcomingWidth = (safeWidth - upcomingStart).coerceAtLeast(0.dp)
    val visibleFocusGap = (safeWidth - previousWidth - upcomingWidth).coerceAtLeast(0.dp)

    return ContextCueSlots(
        previousWidth = previousWidth,
        focusGap = visibleFocusGap,
        upcomingWidth = upcomingWidth,
        hasPreviousRoom = previousWidth >= minimumCueWidth,
        hasUpcomingRoom = upcomingWidth >= minimumCueWidth,
    )
}

private const val CONTEXT_PREVIOUS_WORDS = 6
private const val CONTEXT_UPCOMING_WORDS = 3
private const val CONTEXT_MAX_CLAUSE_WORDS = 18
private const val CONTEXT_LONG_CLAUSE_PREVIOUS_WORDS = 10
private const val CONTEXT_LONG_CLAUSE_UPCOMING_WORDS = 7
private const val CONTEXT_MINIMAL_PREVIOUS_WORDS = 1
private const val CONTEXT_CLAUSE_PREVIOUS_WORDS = 1
private const val CONTEXT_CLAUSE_UPCOMING_WORDS = 1
private const val CONTEXT_ENVELOPE_BLOCK_FRAMES = 6
private const val CONTEXT_ENVELOPE_ANIMATION_MS = 180
private const val CONTEXT_PREVIOUS_NEAREST_ALPHA = 0.34f
private const val CONTEXT_PREVIOUS_FARTHEST_ALPHA = 0.34f
private const val CONTEXT_UPCOMING_NEAREST_ALPHA = 0.34f
private const val CONTEXT_UPCOMING_FARTHEST_ALPHA = 0.34f
private const val CONTEXT_CUE_FONT_SCALE = 1f
private const val CONTEXT_BASELINE_SAMPLE = "Ag"
private val CONTEXT_FOCUS_SIDE_PADDING = 18.dp
private val CONTEXT_MIN_FOCUS_SIDE_RESERVE = 48.dp
private val CONTEXT_MIN_CUE_WIDTH = 48.dp
private val CONTEXT_CUE_INNER_PADDING = 8.dp
private val CONTEXT_BOUNDARY_PUNCTUATION =
    setOf('.', ',', ';', ':', '!', '?', '\u2026', '\u2014', '\u2013')
