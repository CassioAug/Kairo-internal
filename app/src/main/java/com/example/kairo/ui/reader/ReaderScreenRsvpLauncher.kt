package com.example.kairo.ui.reader

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kairo.R
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.nearestWordIndex
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
                        if (tokens.isNotEmpty()) {
                            val safeIndex = tokens.nearestWordIndex(focusIndex)
                            onStartRsvp(safeIndex)
                        }
                    },
                    onLongClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(focusListIndex)
                        }
                    },
                )
                .pointerInput(tokens, focusIndex, invertedScroll) {
                    val thresholdPx = 22f
                    var gestureFocusIndex = 0
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragAccumulator = 0f
                            gestureFocusIndex =
                                if (tokens.isNotEmpty()) {
                                    tokens.nearestWordIndex(focusIndex).coerceIn(0, tokens.lastIndex)
                                } else {
                                    0
                                }
                        },
                        onDragEnd = { dragAccumulator = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            if (tokens.isEmpty()) return@detectVerticalDragGestures
                            dragAccumulator += dragAmount
                            val steps = (dragAccumulator / thresholdPx).toInt()
                            if (steps == 0) return@detectVerticalDragGestures
                            val rawDirection = -steps
                            val effectiveDirection = if (invertedScroll) -rawDirection else rawDirection
                            val next =
                                tokens.nearestWordIndex(
                                    (gestureFocusIndex + effectiveDirection).coerceIn(0, tokens.lastIndex),
                                )
                            gestureFocusIndex = next
                            onFocusChange(next)
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
