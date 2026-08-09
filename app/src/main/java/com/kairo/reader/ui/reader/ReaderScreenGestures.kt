package com.kairo.reader.ui.reader

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.flow.MutableSharedFlow

internal sealed interface InvertedScrollCommand {
    data class Drag(val dy: Float) : InvertedScrollCommand

    data class Fling(val velocityY: Float) : InvertedScrollCommand
}

internal enum class Axis { Horizontal, Vertical }

internal data class ReaderGestureState(
    val listStateKey: String,
    val invertedScroll: Boolean,
    val chapterIndex: Int,
    val invertedScrollCommands: MutableSharedFlow<InvertedScrollCommand>,
    val isPageGestureEnabled: () -> Boolean,
)

internal data class ReaderGestureActions(
    val onPreviousPage: () -> Unit,
    val onNextPage: () -> Unit,
    val onSwipePreviewChange: (ReaderSwipeDirection?, Float) -> Unit,
)

internal fun Modifier.readerPageGestures(
    state: ReaderGestureState,
    actions: ReaderGestureActions,
): Modifier =
    pointerInput(state.listStateKey, state.invertedScroll, state.chapterIndex) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!state.isPageGestureEnabled()) return@awaitEachGesture
            val pointerId = down.id
            actions.onSwipePreviewChange(null, 0f)

            val touchSlop = viewConfiguration.touchSlop
            val swipeThreshold = touchSlop * SWIPE_THRESHOLD_MULTIPLIER
            var totalX = 0f
            var totalY = 0f
            var axis = Axis.Horizontal
            var axisResolved = false
            var tracking = true
            var gestureCancelled = false
            val tracker = VelocityTracker()
            tracker.addPosition(SystemClock.uptimeMillis(), down.position)

            while (tracking) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
                if (!state.isPageGestureEnabled()) {
                    gestureCancelled = true
                    tracking = false
                } else if (change == null || !change.pressed) {
                    tracking = false
                } else {
                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y
                    totalX += dx
                    totalY += dy

                    if (!axisResolved) {
                        val absX = abs(totalX)
                        val absY = abs(totalY)
                        if (absX > touchSlop || absY > touchSlop) {
                            axis = if (absX > absY) Axis.Horizontal else Axis.Vertical
                            axisResolved = true
                        }
                    }

                    if (axisResolved) {
                        when (axis) {
                            Axis.Horizontal ->
                                actions.onSwipePreviewChange(
                                    totalX.toSwipeDirection(),
                                    (abs(totalX) / swipeThreshold).coerceIn(0f, 1f),
                                )
                            Axis.Vertical -> {
                                actions.onSwipePreviewChange(null, 0f)
                                if (state.invertedScroll) {
                                    tracker.addPosition(SystemClock.uptimeMillis(), change.position)
                                    state.invertedScrollCommands.tryEmit(InvertedScrollCommand.Drag(dy))
                                } else {
                                    tracking = false
                                }
                            }
                        }
                    }
                }
            }

            if (axisResolved && !gestureCancelled) {
                finishReaderGesture(
                    axis = axis,
                    totalX = totalX,
                    swipeThreshold = swipeThreshold,
                    invertedScroll = state.invertedScroll,
                    tracker = tracker,
                    commands = state.invertedScrollCommands,
                    actions = actions,
                    pageGesturesEnabled = state.isPageGestureEnabled(),
                )
            } else {
                actions.onSwipePreviewChange(null, 0f)
            }
        }
    }

private fun Float.toSwipeDirection(): ReaderSwipeDirection? =
    when {
        this > 0f -> ReaderSwipeDirection.Previous
        this < 0f -> ReaderSwipeDirection.Next
        else -> null
    }

private fun finishReaderGesture(
    axis: Axis,
    totalX: Float,
    swipeThreshold: Float,
    invertedScroll: Boolean,
    tracker: VelocityTracker,
    commands: MutableSharedFlow<InvertedScrollCommand>,
    actions: ReaderGestureActions,
    pageGesturesEnabled: Boolean,
) {
    actions.onSwipePreviewChange(null, 0f)
    when (axis) {
        Axis.Horizontal ->
            when (resolveReaderPageSwipeAction(totalX, swipeThreshold, pageGesturesEnabled)) {
                ReaderPageSwipeAction.NEXT -> actions.onNextPage()
                ReaderPageSwipeAction.PREVIOUS -> actions.onPreviousPage()
                null -> Unit
            }
        Axis.Vertical -> {
            if (invertedScroll) {
                val velocity = tracker.calculateVelocity().y
                if (abs(velocity) > FLING_START_VELOCITY) {
                    commands.tryEmit(InvertedScrollCommand.Fling(velocity))
                }
            }
        }
    }
}

internal enum class ReaderPageSwipeAction { PREVIOUS, NEXT }

internal fun resolveReaderPageSwipeAction(
    totalX: Float,
    swipeThreshold: Float,
    pageGesturesEnabled: Boolean,
): ReaderPageSwipeAction? =
    when {
        !pageGesturesEnabled -> null
        totalX <= -swipeThreshold -> ReaderPageSwipeAction.NEXT
        totalX >= swipeThreshold -> ReaderPageSwipeAction.PREVIOUS
        else -> null
    }

internal suspend fun performInvertedFling(
    listState: LazyListState,
    initialVelocityY: Float,
) {
    var velocity = initialVelocityY
    var lastFrameNanos = withFrameNanos { it }
    val stopVelocityPxPerSec = FLING_STOP_VELOCITY
    val frictionPerSecond = FLING_FRICTION_PER_SECOND

    while (abs(velocity) > stopVelocityPxPerSec) {
        val frameNanos = withFrameNanos { it }
        val dtSec = ((frameNanos - lastFrameNanos).coerceAtLeast(0L)) / NANOSECONDS_PER_SECOND
        lastFrameNanos = frameNanos

        val dy = velocity * dtSec
        val consumed = listState.scrollBy(dy)
        if (consumed == 0f) break

        velocity *= exp(-frictionPerSecond * dtSec)
    }
}

private const val SWIPE_THRESHOLD_MULTIPLIER = 4f
private const val FLING_START_VELOCITY = 200f
private const val FLING_STOP_VELOCITY = 40f
private const val FLING_FRICTION_PER_SECOND = 8f
private const val NANOSECONDS_PER_SECOND = 1_000_000_000f
