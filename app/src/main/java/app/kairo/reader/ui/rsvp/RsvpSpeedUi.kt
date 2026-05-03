package app.kairo.reader.ui.rsvp

import androidx.annotation.StringRes
import app.kairo.reader.R
import app.kairo.reader.core.rsvp.RsvpSpeedControl

@StringRes
internal fun rsvpSpeedBandLabelRes(
    tempoMsPerWord: Long,
    extremeUnlocked: Boolean,
): Int =
    when (RsvpSpeedControl.bandForTempoMs(tempoMsPerWord, extremeUnlocked)) {
        RsvpSpeedControl.SpeedBand.VERY_SLOW -> R.string.rsvp_speed_band_very_slow
        RsvpSpeedControl.SpeedBand.SLOW -> R.string.rsvp_speed_band_slow
        RsvpSpeedControl.SpeedBand.STEADY -> R.string.rsvp_speed_band_steady
        RsvpSpeedControl.SpeedBand.FAST -> R.string.rsvp_speed_band_fast
        RsvpSpeedControl.SpeedBand.VERY_FAST -> R.string.rsvp_speed_band_very_fast
        RsvpSpeedControl.SpeedBand.EXTREME -> R.string.rsvp_speed_band_extreme
    }
