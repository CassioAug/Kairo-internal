package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.rsvp.RsvpEffectivePace
import com.kairo.reader.core.rsvp.RsvpEstimatedReadingPace
import kotlinx.coroutines.withContext

internal data class KairoNavPaceState(
    val selectedWpm: Int,
    val estimatedWpm: Int,
)

@Composable
internal fun rememberKairoNavPaceState(
    config: RsvpConfig,
    dispatcherProvider: DispatcherProvider,
): KairoNavPaceState {
    val selectedWpm by produceState(initialValue = 0, config) {
        value =
            withContext(dispatcherProvider.default) {
                RsvpEffectivePace.estimateWpm(config)
            }
    }
    val estimatedWpm by produceState(initialValue = selectedWpm, config, selectedWpm) {
        value =
            withContext(dispatcherProvider.default) {
                RsvpEstimatedReadingPace.estimateWpm(
                    config = config,
                    fallbackEstimatedWpm = selectedWpm,
                )
            }
    }
    return KairoNavPaceState(
        selectedWpm = selectedWpm,
        estimatedWpm = estimatedWpm,
    )
}
