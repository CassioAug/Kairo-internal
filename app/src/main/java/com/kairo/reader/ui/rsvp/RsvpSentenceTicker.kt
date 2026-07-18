package com.kairo.reader.ui.rsvp

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import kotlin.math.roundToInt

@Composable
internal fun RsvpSentenceTicker(
    content: RsvpSentenceTickerContent,
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
    horizontalBias: Float,
    guideBandHeight: Dp?,
    frameDurationMs: Long,
) {
    if (content.text.isEmpty()) return
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val effectiveFontSizeSp = stableContextCueFontSizeSp(fontSizeSp)
    val textStyle =
        rememberRsvpContextTextStyle(effectiveFontSizeSp, fontFamily, fontWeight)
    val tickerModifier =
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
        modifier = tickerModifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val measured =
            remember(content.text.text, textStyle, textMeasurer) {
                textMeasurer.measure(
                    text = AnnotatedString(content.text.text),
                    style = textStyle,
                    overflow = TextOverflow.Clip,
                    maxLines = 1,
                    softWrap = false,
                    constraints = Constraints(maxWidth = Int.MAX_VALUE),
                )
            }
        val safePivot =
            content.pivotPosition.coerceIn(0, (content.text.length - 1).coerceAtLeast(0))
        val pivotBox = measured.getBoundingBox(safePivot)
        val pivotCenter = pivotBox.left + (pivotBox.width / BIAS_SCALE_FACTOR)
        val safeFocusStart = content.displayedFocusStart.coerceIn(0, content.text.lastIndex)
        val safeFocusEnd =
            (content.displayedFocusEndExclusive - 1)
                .coerceIn(safeFocusStart, content.text.lastIndex)
        val focusStartBox = measured.getBoundingBox(safeFocusStart)
        val focusEndBox = measured.getBoundingBox(safeFocusEnd)
        val focusWidth = (focusEndBox.right - focusStartBox.left).coerceAtLeast(pivotBox.width)
        val maxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(MIN_ORP_WIDTH_PX)
        val bounds =
            calculateOrpBounds(
                maxWidthPx = maxWidthPx,
                effectiveBias =
                horizontalBias.coerceIn(HORIZONTAL_BIAS_MIN, HORIZONTAL_BIAS_MAX),
                baseEdgePx = with(density) { ORP_BASE_EDGE.toPx() },
                extraEdgePx = with(density) { ORP_EXTRA_EDGE.toPx() },
                measuredWidthPx = focusWidth,
            )
        val targetTranslation = bounds.desiredPivotX - pivotCenter
        val tickerWidth = with(density) { measured.size.width.toDp() }
        val motionDurationMs =
            (frameDurationMs.coerceAtLeast(1L) * CONTEXT_TICKER_MOTION_DURATION_FRACTION)
                .roundToInt()
                .coerceIn(
                    CONTEXT_TICKER_MOTION_MIN_MS,
                    CONTEXT_TICKER_MOTION_MAX_MS,
                )
        val translationX =
            animateFloatAsState(
                targetValue = targetTranslation,
                animationSpec =
                tween(
                    durationMillis = motionDurationMs,
                    easing = LinearOutSlowInEasing,
                ),
                label = "sentenceTickerTranslation",
            ).value

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush =
                        Brush.horizontalGradient(
                            ZERO_FLOAT to Color.Transparent,
                            CONTEXT_TICKER_EDGE_FADE_FRACTION to Color.Black,
                            (ONE_FLOAT - CONTEXT_TICKER_EDGE_FADE_FRACTION) to Color.Black,
                            ONE_FLOAT to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Text(
                text = content.text,
                style = textStyle,
                color = Color.Transparent,
                overflow = TextOverflow.Clip,
                maxLines = 1,
                softWrap = false,
                modifier =
                Modifier
                    .wrapContentSize(
                        align = Alignment.CenterStart,
                        unbounded = true,
                    )
                    .requiredWidth(tickerWidth)
                    .graphicsLayer {
                        this.translationX = snapTranslationToRenderPixel(translationX)
                    },
            )
        }
    }
}
