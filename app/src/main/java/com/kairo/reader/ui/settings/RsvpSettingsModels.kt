package com.kairo.reader.ui.settings

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpCustomProfile
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight

data class RsvpSettingsState(
    val selectedProfileId: String,
    val customProfiles: List<RsvpCustomProfile>,
    val config: RsvpConfig,
    val tempoMsPerWord: Long,
    val profileComparisonConfig: RsvpConfig = config,
    val estimatedWpmOverride: Int? = null,
    val unlockExtremeSpeed: Boolean,
    val fontSizeSp: Float,
    val textBrightness: Float,
    val fontFamily: RsvpFontFamily,
    val fontWeight: RsvpFontWeight,
    val verticalBias: Float,
    val horizontalBias: Float,
)

data class RsvpSettingsActions(
    val onSelectProfile: (String) -> Unit,
    val onSaveCustomProfile: (String, RsvpConfig) -> Unit,
    val onDeleteCustomProfile: (String) -> Unit,
    val onTempoMsPerWordChange: (Long) -> Unit,
    val onConfigChange: ((RsvpConfig) -> RsvpConfig) -> Unit,
    val onUnlockExtremeSpeedChange: (Boolean) -> Unit,
    val onFontSizeChange: (Float) -> Unit,
    val onTextBrightnessChange: (Float) -> Unit,
    val onFontWeightChange: (RsvpFontWeight) -> Unit,
    val onFontFamilyChange: (RsvpFontFamily) -> Unit,
    val onVerticalBiasChange: (Float) -> Unit,
    val onHorizontalBiasChange: (Float) -> Unit,
)
