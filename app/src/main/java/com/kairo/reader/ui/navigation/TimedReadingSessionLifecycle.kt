package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.kairo.reader.data.sessions.ReadingSessionTracker

@Composable
internal fun TrackTimedReadingSessionLifecycle(
    tracker: ReadingSessionTracker,
    lifecycleOwner: LifecycleOwner,
    isPlaying: Boolean,
) {
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    DisposableEffect(tracker, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START ->
                        tracker.setActive(latestIsPlaying, System.currentTimeMillis())
                    Lifecycle.Event.ON_STOP ->
                        tracker.setActive(false, System.currentTimeMillis())
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.setActive(false, System.currentTimeMillis())
        }
    }
}
