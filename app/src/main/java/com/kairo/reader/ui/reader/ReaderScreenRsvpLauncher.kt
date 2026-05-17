package com.kairo.reader.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.nearestWordIndex
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderRsvpLauncher(
    tokens: List<Token>,
    focusIndex: Int,
    invertedScroll: Boolean,
    listState: LazyListState,
    focusListIndex: Int,
    progressFraction: Float,
    onFocusChange: (Int) -> Unit,
    onStartRsvp: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val progressPercent = (progressFraction * 100f).toInt().coerceIn(0, 100)
    val currentTokens by rememberUpdatedState(tokens)
    val currentFocusIndex by rememberUpdatedState(focusIndex)
    val currentInvertedScroll by rememberUpdatedState(invertedScroll)
    val currentFocusListIndex by rememberUpdatedState(focusListIndex)
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)
    val currentOnStartRsvp by rememberUpdatedState(onStartRsvp)

    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .combinedClickable(
                    onClick = {
                        val latestTokens = currentTokens
                        if (latestTokens.isNotEmpty()) {
                            val safeIndex = latestTokens.nearestWordIndex(currentFocusIndex)
                            currentOnStartRsvp(safeIndex)
                        }
                    },
                    onLongClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(currentFocusListIndex)
                        }
                    },
                )
                .pointerInput(Unit) {
                    val thresholdPx = 22f
                    var gestureFocusIndex = 0
                    detectVerticalDragGestures(
                        onDragStart = {
                            val latestTokens = currentTokens
                            dragAccumulator = 0f
                            gestureFocusIndex =
                                if (latestTokens.isNotEmpty()) {
                                    latestTokens.nearestWordIndex(currentFocusIndex).coerceIn(
                                        0,
                                        latestTokens.lastIndex,
                                    )
                                } else {
                                    0
                                }
                        },
                        onDragEnd = { dragAccumulator = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            val latestTokens = currentTokens
                            if (latestTokens.isEmpty()) return@detectVerticalDragGestures
                            dragAccumulator += dragAmount
                            val steps = (dragAccumulator / thresholdPx).toInt()
                            if (steps == 0) return@detectVerticalDragGestures
                            val rawDirection = -steps
                            val effectiveDirection =
                                if (currentInvertedScroll) -rawDirection else rawDirection
                            val next =
                                latestTokens.nearestWordIndex(
                                    (gestureFocusIndex + effectiveDirection).coerceIn(
                                        0,
                                        latestTokens.lastIndex,
                                    ),
                                )
                            gestureFocusIndex = next
                            currentOnFocusChange(next)
                            dragAccumulator -= steps * thresholdPx
                        },
                    )
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 14.dp, bottom = 6.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 2.dp,
                    modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.content_desc_start_rsvp),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.reader_rsvp_dock_progress, progressPercent),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
