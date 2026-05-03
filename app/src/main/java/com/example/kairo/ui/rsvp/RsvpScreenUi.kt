@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kairo.R
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.core.model.prefersOrpWindowing
import com.example.kairo.core.model.prefersSimplifiedOrpDisplay
import com.example.kairo.core.rsvp.RsvpSpeedControl
import com.example.kairo.ui.theme.InterFontFamily
import com.example.kairo.ui.theme.RobotoFontFamily
import com.example.kairo.ui.tutorial.StartingTutorialOverlay
import com.example.kairo.ui.tutorial.StartingTutorialOverlayState
import com.example.kairo.ui.tutorial.StartingTutorialTargetIds
import com.example.kairo.ui.tutorial.startingTutorialTarget
import kotlin.math.abs

internal enum class PreviewSide { ABOVE, BELOW }

internal data class ParagraphPreviewPlacement(
    val side: PreviewSide,
    val topPx: Float,
)

internal data class CompactLandscapePreviewBand(
    val topPx: Float,
    val heightPx: Float,
)

internal data class CompactLandscapePreviewTextRegion(
    val topPx: Float,
    val heightPx: Float,
    val side: PreviewSide,
)

private data class PreviewCandidate(
    val side: PreviewSide,
    val topPx: Float,
    val overlapPx: Float,
    val score: Float,
)

@Composable
internal fun RsvpPlaybackSurface(
    context: RsvpUiContext,
    tutorialState: StartingTutorialOverlayState? = null,
    onTutorialNext: () -> Unit = {},
    onTutorialPrevious: () -> Unit = {},
    onTutorialSkip: () -> Unit = {},
) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val currentFrame = frames.getOrNull(runtime.frameIndex)
    val compactLandscape = isCompactLandscape()
    val bottomChromeInset =
        rememberBottomChromeInset(
            runtime = runtime,
            compactLandscape = compactLandscape,
        )
    val previewBottomChromeInset =
        rememberPreviewBottomChromeInset(
            runtime = runtime,
            compactLandscape = compactLandscape,
            bottomChromeInset = bottomChromeInset,
        )
    val typography =
        OrpTypography(
            fontSizeSp = runtime.currentFontSizeSp,
            fontFamily = resolveFontFamily(runtime.currentFontFamily),
            fontWeight = resolveFontWeight(runtime.currentFontWeight),
        )
    val colors =
        rememberRsvpTextColors(
            textBrightness = runtime.currentTextBrightness,
            config = context.state.profile.config,
        )
    val interactionSource = remember { MutableInteractionSource() }
    val displayedSpeed =
        rememberRsvpDisplayedSpeed(
            currentTempoMsPerWord = runtime.currentTempoMsPerWord,
            minTempoMs = context.timing.minTempoMs,
            maxTempoMs = context.timing.maxTempoMs,
        )
    val speedBandLabel =
        stringResource(
            rsvpSpeedBandLabelRes(
                tempoMsPerWord = runtime.currentTempoMsPerWord,
                extremeUnlocked = context.state.uiPrefs.extremeSpeedUnlocked,
            ),
        )
    val speedIndicatorText =
        stringResource(
            R.string.rsvp_reading_speed_indicator,
            speedBandLabel,
            displayedSpeed,
        )
    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .startingTutorialTarget(StartingTutorialTargetIds.RSVP_SURFACE) {
                targetId,
                bounds,
                ->
                tutorialTargets[targetId] = bounds
            }
            .rsvpGestureModifier(context, interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        RsvpParagraphPreview(
            context = context,
            typography = typography,
            colors = colors,
            previewBottomChromeInset = previewBottomChromeInset,
            orpBottomChromeInset = bottomChromeInset,
            compactLandscape = compactLandscape,
        )
        RsvpFocusWord(context, currentFrame, typography, colors, bottomChromeInset)
        RsvpPositionGuide(context, bottomChromeInset)
        RsvpProgressBar(context)
        RsvpTopBar(
            context = context,
            settingsModifier =
                Modifier.startingTutorialTarget(StartingTutorialTargetIds.RSVP_TOP_SETTINGS) {
                    targetId,
                    bounds,
                    ->
                    tutorialTargets[targetId] = bounds
                },
            closeModifier =
                Modifier.startingTutorialTarget(StartingTutorialTargetIds.RSVP_EXIT) {
                    targetId,
                    bounds,
                    ->
                    tutorialTargets[targetId] = bounds
                },
        )
        RsvpTempoIndicator(context, speedIndicatorText)
        RsvpFontSizeIndicator(context)
        RsvpPositioningIndicator(context)
        RsvpScrubTargetIndicator(context)
        RsvpQuickSettingsPanel(
            context = context,
            speedPercent = displayedSpeed,
            panelModifier =
                Modifier.startingTutorialTarget(StartingTutorialTargetIds.RSVP_QUICK_SETTINGS) {
                    targetId,
                    bounds,
                    ->
                    tutorialTargets[targetId] = bounds
                },
            settingsRowModifier =
                Modifier.startingTutorialTarget(StartingTutorialTargetIds.RSVP_SETTINGS_ROW) {
                    targetId,
                    bounds,
                    ->
                    tutorialTargets[targetId] = bounds
                },
        )
        RsvpBottomControls(
            context = context,
            speedIndicatorText = speedIndicatorText,
            controlsModifier =
                Modifier.startingTutorialTarget(StartingTutorialTargetIds.RSVP_PLAYBACK_CONTROLS) {
                    targetId,
                    bounds,
                    ->
                    tutorialTargets[targetId] = bounds
                },
        )
        tutorialState?.let { overlayState ->
            StartingTutorialOverlay(
                state = overlayState,
                targetBounds = overlayState.step.targetId?.let(tutorialTargets::get),
                onNext = onTutorialNext,
                onPrevious = onTutorialPrevious,
                onSkip = onTutorialSkip,
            )
        }
    }
}

@Composable
internal fun rememberRsvpDisplayedSpeed(
    currentTempoMsPerWord: Long,
    minTempoMs: Long,
    maxTempoMs: Long,
): Int =
    remember(currentTempoMsPerWord, minTempoMs, maxTempoMs) {
        RsvpSpeedControl.displaySpeed(
            RsvpSpeedControl.speedForTempoMs(
                tempoMsPerWord = currentTempoMsPerWord,
                minTempoMsPerWord = minTempoMs,
                maxTempoMsPerWord = maxTempoMs,
            ),
        )
    }

@Composable
private fun rememberRsvpTextColors(
    textBrightness: Float,
    config: RsvpConfig,
): OrpColors {
    val clampedBrightness = textBrightness.coerceIn(TEXT_BRIGHTNESS_MIN, TEXT_BRIGHTNESS_MAX)
    val guideBrightness =
        config.orpGuideBrightness
            .toFloat()
            .coerceIn(ORP_GUIDE_BRIGHTNESS_MIN, ORP_GUIDE_BRIGHTNESS_MAX)
    val pivotLineAlpha =
        (PIVOT_LINE_ALPHA_BASE * clampedBrightness * guideBrightness)
            .coerceIn(PIVOT_LINE_ALPHA_MIN, PIVOT_LINE_ALPHA_MAX)
    return OrpColors(
        pivotColor = MaterialTheme.colorScheme.primary,
        pivotLineColor = MaterialTheme.colorScheme.onBackground.copy(alpha = pivotLineAlpha),
        textColor = MaterialTheme.colorScheme.onBackground.copy(alpha = clampedBrightness),
        highlightColor = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RsvpFocusWord(
    context: RsvpUiContext,
    frame: com.example.kairo.core.model.RsvpFrame?,
    typography: OrpTypography,
    colors: OrpColors,
    bottomChromeInset: Dp,
) {
    val runtime = context.runtime
    val profile = context.state.profile
    if (frame == null) return

    Box(
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
        OrpAlignedText(
            tokens = frame.tokens,
            typography = typography,
            colors = colors,
            layout =
            OrpTextLayout(
                horizontalBias = runtime.currentHorizontalBias,
                lockPivot =
                profile.config.enablePhraseChunking &&
                    profile.config.maxWordsPerUnit.coerceAtLeast(2) > ORP_LOCK_PIVOT_WORDS,
                smoothTranslation = runtime.isScrubbing || runtime.isAdjustingPosition,
                preferWindowing = profile.config.prefersOrpWindowing(runtime.currentTempoMsPerWord),
                simplifyPunctuation =
                    profile.config.prefersSimplifiedOrpDisplay(runtime.currentTempoMsPerWord),
                guideVisible = profile.config.orpGuideEnabled,
                guideThickness =
                    profile.config.orpGuideThickness
                        .toFloat()
                        .coerceIn(ORP_GUIDE_THICKNESS_MIN, ORP_GUIDE_THICKNESS_MAX),
            ),
        )
    }
}

@Composable
private fun RsvpParagraphPreview(
    context: RsvpUiContext,
    typography: OrpTypography,
    colors: OrpColors,
    previewBottomChromeInset: Dp,
    orpBottomChromeInset: Dp,
    compactLandscape: Boolean,
) {
    val runtime = context.runtime
    val tokens = context.state.book.tokens
    if (tokens.isEmpty()) return

    val currentIndex =
        resolveCurrentTokenIndex(
            context.frameState.frames,
            runtime.frameIndex,
            context.state.book.startIndex,
        )
    val highlightIndex =
        remember(tokens, currentIndex) {
            tokens.nearestWordIndex(currentIndex)
        }
    val paragraph =
        remember(tokens, highlightIndex) {
            resolveRsvpParagraph(tokens, highlightIndex)
        } ?: return
    val highlightTextColor =
        if (compactLandscape) {
            MaterialTheme.colorScheme.primary.copy(alpha = PARAGRAPH_COMPACT_HIGHLIGHT_ALPHA)
        } else {
            MaterialTheme.colorScheme.primary
        }
    val highlightFontWeight =
        if (compactLandscape) {
            FontWeight.Bold
        } else {
            FontWeight.SemiBold
        }
    val highlightBackgroundColor =
        if (compactLandscape) {
            MaterialTheme.colorScheme.primary.copy(alpha = PARAGRAPH_COMPACT_HIGHLIGHT_BACKGROUND_ALPHA)
        } else {
            Color.Unspecified
        }
    val highlightStyle =
        remember(colors, highlightTextColor, highlightFontWeight, highlightBackgroundColor) {
            SpanStyle(
                color = highlightTextColor,
                fontWeight = highlightFontWeight,
                background = highlightBackgroundColor,
            )
        }
    val annotatedText =
        remember(paragraph, highlightIndex, highlightStyle, compactLandscape) {
            buildRsvpParagraphAnnotatedText(
                paragraph = paragraph,
                highlightIndex = highlightIndex,
                highlightStyle = highlightStyle,
                maxWords =
                    if (compactLandscape) {
                        PARAGRAPH_COMPACT_PREVIEW_WINDOW_WORDS
                    } else {
                        PARAGRAPH_PREVIEW_WINDOW_WORDS
                    },
                highlightWindowFraction =
                    if (compactLandscape) {
                        PARAGRAPH_COMPACT_PREVIEW_HIGHLIGHT_FRACTION
                    } else {
                        null
                    },
            )
        }
    val fontSizeSp =
        if (compactLandscape) {
            (typography.fontSizeSp * PARAGRAPH_COMPACT_FONT_SCALE)
                .coerceIn(MIN_PARAGRAPH_FONT_SIZE_SP, MAX_PARAGRAPH_COMPACT_FONT_SIZE_SP)
        } else {
            (typography.fontSizeSp * PARAGRAPH_FONT_SCALE)
                .coerceIn(MIN_PARAGRAPH_FONT_SIZE_SP, MAX_PARAGRAPH_FONT_SIZE_SP)
        }
    val lineHeightSp = fontSizeSp * PARAGRAPH_LINE_HEIGHT_MULTIPLIER
    val previewHeight = PARAGRAPH_PREVIEW_HEIGHT
    val lineCount =
        with(LocalDensity.current) {
            val lineHeightPx = lineHeightSp.sp.toPx().coerceAtLeast(1f)
            val previewHeightPx =
                (
                    previewHeight -
                        (PARAGRAPH_PREVIEW_CONTENT_PADDING_VERTICAL * 2)
                ).toPx().coerceAtLeast(lineHeightPx)
            (previewHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
        }
    val textStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontSize = fontSizeSp.sp,
            fontFamily = typography.fontFamily,
            fontWeight = FontWeight.Normal,
            color =
                colors.textColor.copy(
                    alpha =
                        if (compactLandscape) {
                            PARAGRAPH_COMPACT_TEXT_ALPHA
                        } else {
                            PARAGRAPH_TEXT_ALPHA
                        },
                ),
            lineHeight = lineHeightSp.sp,
        )
    val offsetY =
        maxOf(
            with(LocalDensity.current) {
                (typography.fontSizeSp * PARAGRAPH_OFFSET_MULTIPLIER).sp.toDp()
            },
            (previewHeight / 2) + PARAGRAPH_PREVIEW_MIN_ORP_CLEARANCE,
        )
    var previewSide by remember { mutableStateOf(PreviewSide.BELOW) }
    val visible =
        shouldShowParagraphPreview(
            isPlaying = runtime.isPlaying,
            isScrubbing = runtime.isScrubbing,
            isPositioningMode = runtime.isPositioningMode,
            showControls = runtime.showControls,
            isExiting = runtime.isExiting,
        )

    if (!visible) return

    val backgroundColor = MaterialTheme.colorScheme.background
    val previewSurfaceColor =
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PARAGRAPH_PREVIEW_SURFACE_ALPHA)
    val previewBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)
    val clampedVerticalBias = runtime.currentVerticalBias.coerceIn(VERTICAL_BIAS_MIN, VERTICAL_BIAS_MAX)
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = previewBottomChromeInset),
    ) {
            val density = LocalDensity.current
            val previewHeightPx = with(density) { previewHeight.toPx() }
            val previewBottomChromeInsetPx = with(density) { previewBottomChromeInset.toPx() }
            val orpBottomChromeInsetPx = with(density) { orpBottomChromeInset.toPx() }
            val preferredOffsetPx = with(density) { offsetY.toPx() }
            val edgePaddingPx = with(density) { PARAGRAPH_PREVIEW_EDGE_PADDING.toPx() }
            val compactEdgePaddingPx = with(density) { PARAGRAPH_PREVIEW_COMPACT_EDGE_PADDING.toPx() }
            val collisionGapPx = with(density) { PARAGRAPH_PREVIEW_ORP_COLLISION_GAP.toPx() }
            val switchHysteresisPx = with(density) { PARAGRAPH_PREVIEW_SWITCH_HYSTERESIS.toPx() }
            val switchOverlapThresholdPx =
                with(density) { PARAGRAPH_PREVIEW_SWITCH_OVERLAP_THRESHOLD.toPx() }
            val orpDecorHeightPx =
                with(density) {
                    (
                        (ORP_LINE_HEIGHT * 2) +
                            (ORP_POINTER_HEIGHT * 2) +
                            (ORP_TEXT_SPACER * 2)
                    ).toPx()
                }
            val orpTextHeightPx =
                with(density) {
                    (typography.fontSizeSp * ORP_COLLISION_TEXT_HEIGHT_MULTIPLIER).sp.toPx()
                }
            val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(previewHeightPx)
            val orpCenterY =
                resolveOrpCollisionCenterY(
                    previewViewportHeightPx = viewportHeightPx,
                    previewBottomChromeInsetPx = previewBottomChromeInsetPx,
                    orpBottomChromeInsetPx = orpBottomChromeInsetPx,
                    verticalBias = clampedVerticalBias,
                    minimumViewportHeightPx = previewHeightPx,
                )
            val orpBandHalfHeightPx = (orpDecorHeightPx + orpTextHeightPx) / BIAS_SCALE_FACTOR
            val protectedTop = orpCenterY - orpBandHalfHeightPx - collisionGapPx
            val protectedBottom = orpCenterY + orpBandHalfHeightPx + collisionGapPx
            val anchorTop =
                ((viewportHeightPx - previewHeightPx) * (ONE_FLOAT + clampedVerticalBias) / BIAS_SCALE_FACTOR)
            if (compactLandscape) {
                val compactBand =
                    resolveCompactLandscapePreviewBand(
                        orpCenterY = orpCenterY,
                        orpBandHalfHeightPx = orpBandHalfHeightPx,
                        viewportHeightPx = viewportHeightPx,
                        orpOverlapPx =
                            with(density) { PARAGRAPH_PREVIEW_COMPACT_ORP_OVERLAP.toPx() },
                        edgePaddingPx = compactEdgePaddingPx,
                        minHeightPx = with(density) { lineHeightSp.sp.toPx() },
                    )
                val compactTextRegion =
                    resolveCompactLandscapePreviewTextRegion(
                        orpCenterY = orpCenterY,
                        orpBandHalfHeightPx = orpBandHalfHeightPx,
                        controlsTopPx =
                            resolveCompactLandscapeControlsTop(
                                viewportHeightPx = viewportHeightPx,
                                controlsReservedHeightPx =
                                    with(density) { CONTROLS_COMPACT_RESERVED_HEIGHT.toPx() },
                                bottomChromeInsetPx = previewBottomChromeInsetPx,
                                controlsVisible = runtime.showControls,
                            ),
                        orpClearancePx =
                            with(density) { PARAGRAPH_PREVIEW_COMPACT_TEXT_ORP_CLEARANCE.toPx() },
                        edgePaddingPx = compactEdgePaddingPx,
                        minHeightPx = with(density) { lineHeightSp.sp.toPx() },
                        preferredHeightPx =
                            with(density) {
                                (
                                    lineHeightSp.sp.toPx() * COMPACT_PREVIEW_SNAP_MIN_LINES
                                ) + (PARAGRAPH_PREVIEW_CONTENT_PADDING_VERTICAL * 2).toPx()
                            },
                    )
                val compactLineCount =
                    with(density) {
                        val lineHeightPx = lineHeightSp.sp.toPx().coerceAtLeast(1f)
                        val contentHeightPx =
                            (
                                compactTextRegion.heightPx -
                                    (PARAGRAPH_PREVIEW_CONTENT_PADDING_VERTICAL * 2).toPx()
                            ).coerceAtLeast(lineHeightPx)
                        (contentHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .offset(y = with(density) { compactBand.topPx.toDp() })
                                .height(with(density) { compactBand.heightPx.toDp() })
                                .clipToBounds(),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(PARAGRAPH_FADE_HEIGHT)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to backgroundColor,
                                                    0.42f to backgroundColor.copy(alpha = PARAGRAPH_FADE_MID_ALPHA),
                                                    1f to backgroundColor.copy(alpha = 0f),
                                                ),
                                        ),
                                    ),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(PARAGRAPH_FADE_HEIGHT)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to backgroundColor.copy(alpha = 0f),
                                                    0.58f to backgroundColor.copy(alpha = PARAGRAPH_FADE_MID_ALPHA),
                                                    1f to backgroundColor,
                                                ),
                                        ),
                                    ),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .offset(y = with(density) { compactTextRegion.topPx.toDp() })
                                .height(with(density) { compactTextRegion.heightPx.toDp() })
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .clipToBounds()
                                .padding(
                                    horizontal = PARAGRAPH_PREVIEW_HORIZONTAL_PADDING,
                                    vertical = PARAGRAPH_PREVIEW_CONTENT_PADDING_VERTICAL,
                                ),
                    ) {
                        Text(
                            text = annotatedText,
                            style = textStyle,
                            overflow = TextOverflow.Clip,
                            maxLines = compactLineCount,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                return@BoxWithConstraints
            }
            val placement =
                resolveParagraphPreviewPlacement(
                    currentSide = previewSide,
                    isPositioningMode = runtime.isPositioningMode,
                    anchorTop = anchorTop,
                    previewHeightPx = previewHeightPx,
                    preferredOffsetPx = preferredOffsetPx,
                    edgePaddingPx = edgePaddingPx,
                    protectedTop = protectedTop,
                    protectedBottom = protectedBottom,
                    maxTop = (viewportHeightPx - previewHeightPx - edgePaddingPx).coerceAtLeast(edgePaddingPx),
                    switchHysteresisPx = switchHysteresisPx,
                    switchOverlapThresholdPx = switchOverlapThresholdPx,
                )
            previewSide = placement.side
            val resolvedTop = placement.topPx
            val resolvedOffsetY = with(density) { (resolvedTop - anchorTop).toDp() }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment =
                    BiasAlignment(
                        horizontalBias = CENTER_BIAS,
                        verticalBias = clampedVerticalBias,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .offset(y = resolvedOffsetY)
                            .fillMaxWidth()
                            .widthIn(max = PARAGRAPH_PREVIEW_MAX_WIDTH)
                            .padding(horizontal = PARAGRAPH_PREVIEW_HORIZONTAL_PADDING)
                            .height(previewHeight)
                            .clip(RoundedCornerShape(PARAGRAPH_PREVIEW_CORNER_RADIUS))
                            .background(previewSurfaceColor)
                            .border(
                                width = 1.dp,
                                color = previewBorderColor,
                                shape = RoundedCornerShape(PARAGRAPH_PREVIEW_CORNER_RADIUS),
                            )
                            .clipToBounds(),
                ) {
                    Text(
                        text = annotatedText,
                        style = textStyle,
                        overflow = TextOverflow.Clip,
                        maxLines = lineCount,
                        minLines = lineCount,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = PARAGRAPH_PREVIEW_CONTENT_PADDING_HORIZONTAL,
                                    vertical = PARAGRAPH_PREVIEW_CONTENT_PADDING_VERTICAL,
                                ),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(PARAGRAPH_FADE_HEIGHT)
                                .align(Alignment.TopCenter)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colorStops =
                                            arrayOf(
                                                0f to backgroundColor,
                                                0.22f to backgroundColor.copy(alpha = PARAGRAPH_FADE_STRONG_ALPHA),
                                                0.5f to backgroundColor.copy(alpha = PARAGRAPH_FADE_MID_ALPHA),
                                                0.78f to backgroundColor.copy(alpha = PARAGRAPH_FADE_SOFT_ALPHA),
                                                1f to backgroundColor.copy(alpha = 0f),
                                            ),
                                    ),
                                ),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(PARAGRAPH_FADE_HEIGHT)
                                .align(Alignment.BottomCenter)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colorStops =
                                            arrayOf(
                                                0f to backgroundColor.copy(alpha = 0f),
                                                0.22f to backgroundColor.copy(alpha = PARAGRAPH_FADE_SOFT_ALPHA),
                                                0.5f to backgroundColor.copy(alpha = PARAGRAPH_FADE_MID_ALPHA),
                                                0.78f to backgroundColor.copy(alpha = PARAGRAPH_FADE_STRONG_ALPHA),
                                                1f to backgroundColor,
                                            ),
                                    ),
                                ),
                    )
                }
            }
    }
}

internal fun resolveParagraphPreviewPlacement(
    currentSide: PreviewSide,
    isPositioningMode: Boolean,
    anchorTop: Float,
    previewHeightPx: Float,
    preferredOffsetPx: Float,
    edgePaddingPx: Float,
    protectedTop: Float,
    protectedBottom: Float,
    maxTop: Float,
    switchHysteresisPx: Float,
    switchOverlapThresholdPx: Float,
): ParagraphPreviewPlacement {
    val rawBelowTop = anchorTop + preferredOffsetPx
    val rawAboveTop = anchorTop - preferredOffsetPx
    val defaultBelowTop = rawBelowTop.coerceIn(edgePaddingPx, maxTop)
    val defaultAboveTop = rawAboveTop.coerceIn(edgePaddingPx, maxTop)
    val safeBelowTop = protectedBottom.coerceIn(edgePaddingPx, maxTop)
    val safeAboveTop = (protectedTop - previewHeightPx).coerceIn(edgePaddingPx, maxTop)

    fun overlapAmount(top: Float): Float {
        val bottom = top + previewHeightPx
        return (minOf(bottom, protectedBottom) - maxOf(top, protectedTop)).coerceAtLeast(0f)
    }

    fun buildCandidate(
        side: PreviewSide,
        rawTop: Float,
        defaultTop: Float,
        safeTop: Float,
    ): PreviewCandidate {
        val overlapAtDefault = overlapAmount(defaultTop)
        val top = if (overlapAtDefault > 0f) safeTop else defaultTop
        val overlap = overlapAmount(top)
        val score = (overlap * PREVIEW_OVERLAP_SCORE_MULTIPLIER) + abs(top - rawTop)
        return PreviewCandidate(side = side, topPx = top, overlapPx = overlap, score = score)
    }

    val belowCandidate =
        buildCandidate(
            side = PreviewSide.BELOW,
            rawTop = rawBelowTop,
            defaultTop = defaultBelowTop,
            safeTop = safeBelowTop,
        )
    val aboveCandidate =
        buildCandidate(
            side = PreviewSide.ABOVE,
            rawTop = rawAboveTop,
            defaultTop = defaultAboveTop,
            safeTop = safeAboveTop,
        )
    val clearCandidate =
        when {
            belowCandidate.overlapPx <= 0f && aboveCandidate.overlapPx > 0f -> PreviewSide.BELOW
            aboveCandidate.overlapPx <= 0f && belowCandidate.overlapPx > 0f -> PreviewSide.ABOVE
            else -> null
        }

    val resolvedSide =
        if (!isPositioningMode) {
            clearCandidate ?: when {
                aboveCandidate.score + switchHysteresisPx < belowCandidate.score -> PreviewSide.ABOVE
                belowCandidate.score + switchHysteresisPx < aboveCandidate.score -> PreviewSide.BELOW
                rawBelowTop > maxTop && rawAboveTop >= edgePaddingPx -> PreviewSide.ABOVE
                rawAboveTop < edgePaddingPx && rawBelowTop <= maxTop -> PreviewSide.BELOW
                else -> PreviewSide.BELOW
            }
        } else {
            val activeCandidate =
                if (currentSide == PreviewSide.ABOVE) {
                    aboveCandidate
                } else {
                    belowCandidate
                }
            val alternateCandidate =
                if (currentSide == PreviewSide.ABOVE) {
                    belowCandidate
                } else {
                    aboveCandidate
                }
            val activeOverlapsClearAlternate =
                activeCandidate.overlapPx > 0f &&
                    alternateCandidate.overlapPx <= 0f
            val activeOverlapExceedsHysteresis =
                activeCandidate.overlapPx > switchOverlapThresholdPx &&
                    (activeCandidate.overlapPx - alternateCandidate.overlapPx) > switchHysteresisPx
            val shouldSwitch =
                activeOverlapsClearAlternate || activeOverlapExceedsHysteresis
            if (shouldSwitch) {
                alternateCandidate.side
            } else {
                currentSide
            }
        }

    val resolvedTop =
        if (resolvedSide == PreviewSide.ABOVE) {
            aboveCandidate.topPx
        } else {
            belowCandidate.topPx
        }
    return ParagraphPreviewPlacement(side = resolvedSide, topPx = resolvedTop)
}

internal fun shouldShowParagraphPreview(
    isPlaying: Boolean,
    isScrubbing: Boolean,
    isPositioningMode: Boolean,
    showControls: Boolean,
    isExiting: Boolean,
): Boolean =
    !isExiting &&
        !isPlaying &&
        (isScrubbing || isPositioningMode || showControls)

internal fun resolveOrpCollisionCenterY(
    previewViewportHeightPx: Float,
    previewBottomChromeInsetPx: Float,
    orpBottomChromeInsetPx: Float,
    verticalBias: Float,
    minimumViewportHeightPx: Float,
): Float {
    val rootHeightPx =
        previewViewportHeightPx + previewBottomChromeInsetPx.coerceAtLeast(0f)
    val orpViewportHeightPx =
        (rootHeightPx - orpBottomChromeInsetPx.coerceAtLeast(0f))
            .coerceAtLeast(minimumViewportHeightPx)
    return orpViewportHeightPx * (ONE_FLOAT + verticalBias) / BIAS_SCALE_FACTOR
}

internal fun resolveCompactLandscapePreviewBand(
    orpCenterY: Float,
    orpBandHalfHeightPx: Float,
    viewportHeightPx: Float,
    orpOverlapPx: Float,
    edgePaddingPx: Float,
    minHeightPx: Float,
): CompactLandscapePreviewBand {
    val top = (orpCenterY - orpBandHalfHeightPx - orpOverlapPx).coerceAtLeast(edgePaddingPx)
    val bottom = (viewportHeightPx - edgePaddingPx).coerceAtLeast(top)
    val height = (bottom - top).coerceAtLeast(minHeightPx)
    return CompactLandscapePreviewBand(topPx = top, heightPx = height)
}

internal fun resolveCompactLandscapePreviewTextRegion(
    orpCenterY: Float,
    orpBandHalfHeightPx: Float,
    controlsTopPx: Float,
    orpClearancePx: Float,
    edgePaddingPx: Float,
    minHeightPx: Float,
    preferredHeightPx: Float,
): CompactLandscapePreviewTextRegion {
    val readableHeightPx = minHeightPx.coerceAtLeast(1f)
    val comfortableHeightPx = preferredHeightPx.coerceAtLeast(readableHeightPx)
    val safeControlsTopPx = (controlsTopPx - edgePaddingPx).coerceAtLeast(edgePaddingPx)
    val orpTop = orpCenterY - orpBandHalfHeightPx
    val orpBottom = orpCenterY + orpBandHalfHeightPx
    val belowTop =
        (orpBottom + orpClearancePx)
            .coerceAtLeast(edgePaddingPx)
    val belowAvailable = (safeControlsTopPx - belowTop).coerceAtLeast(0f)
    val aboveBottom =
        (orpTop - orpClearancePx)
            .coerceAtLeast(edgePaddingPx)
    val aboveAvailable = (aboveBottom - edgePaddingPx).coerceAtLeast(0f)

    fun resolvedHeight(availableHeightPx: Float): Float =
        minOf(comfortableHeightPx, availableHeightPx.coerceAtLeast(readableHeightPx))

    fun belowRegion(): CompactLandscapePreviewTextRegion =
        CompactLandscapePreviewTextRegion(
            topPx = belowTop,
            heightPx = resolvedHeight(belowAvailable),
            side = PreviewSide.BELOW,
        )

    fun aboveRegion(): CompactLandscapePreviewTextRegion {
        val height = resolvedHeight(aboveAvailable)
        val top =
            (aboveBottom - height)
                .coerceAtLeast(edgePaddingPx)
        return CompactLandscapePreviewTextRegion(
            topPx = top,
            heightPx = height,
            side = PreviewSide.ABOVE,
        )
    }

    val belowIsComfortable = belowAvailable >= comfortableHeightPx
    val aboveIsReadable = aboveAvailable >= readableHeightPx
    val useAbove = !belowIsComfortable && aboveIsReadable

    return if (useAbove) aboveRegion() else belowRegion()
}

internal fun resolveCompactLandscapeControlsTop(
    viewportHeightPx: Float,
    controlsReservedHeightPx: Float,
    bottomChromeInsetPx: Float,
    controlsVisible: Boolean,
): Float =
    if (controlsVisible && bottomChromeInsetPx <= 0f) {
        viewportHeightPx - controlsReservedHeightPx
    } else {
        viewportHeightPx
    }

@Composable
private fun RsvpPositionGuide(
    context: RsvpUiContext,
    bottomChromeInset: Dp,
) {
    val runtime = context.runtime
    val visible =
        runtime.showQuickSettings || runtime.isAdjustingPosition || runtime.isPositioningMode

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
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
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(POSITION_GUIDE_HEIGHT)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = POSITIONING_LINE_ALPHA),
                    ),
            )
        }
    }
}

@Composable
private fun isCompactLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp > configuration.screenHeightDp &&
        configuration.screenHeightDp <= 480
}

@Composable
private fun rememberBottomChromeInset(
    runtime: RsvpRuntimeState,
    compactLandscape: Boolean,
): Dp {
    val shouldProtectLowOrp =
        runtime.showControls &&
            runtime.currentVerticalBias > CONTROLS_COLLISION_VERTICAL_BIAS
    if (!shouldProtectLowOrp) return 0.dp

    return rememberControlsChromeInset(compactLandscape)
}

@Composable
private fun rememberPreviewBottomChromeInset(
    runtime: RsvpRuntimeState,
    compactLandscape: Boolean,
    bottomChromeInset: Dp,
): Dp {
    if (compactLandscape) return bottomChromeInset
    if (!runtime.showControls) return 0.dp

    return rememberControlsChromeInset(compactLandscape)
}

@Composable
private fun rememberControlsChromeInset(compactLandscape: Boolean): Dp {
    val density = LocalDensity.current
    val navigationBarsInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val controlsHeight =
        if (compactLandscape) {
            CONTROLS_COMPACT_RESERVED_HEIGHT
        } else {
            CONTROLS_RESERVED_HEIGHT
        }
    return controlsHeight + navigationBarsInset
}

private fun resolveFontFamily(fontFamily: RsvpFontFamily): FontFamily =
    when (fontFamily) {
        RsvpFontFamily.INTER -> InterFontFamily
        RsvpFontFamily.ROBOTO -> RobotoFontFamily
    }

private fun resolveFontWeight(fontWeight: RsvpFontWeight): FontWeight =
    when (fontWeight) {
        RsvpFontWeight.LIGHT -> FontWeight.Light
        RsvpFontWeight.NORMAL -> FontWeight.Normal
        RsvpFontWeight.MEDIUM -> FontWeight.Medium
    }

private const val PREVIEW_OVERLAP_SCORE_MULTIPLIER = 10f
