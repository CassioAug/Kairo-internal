package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.rsvp.RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD
import com.kairo.reader.ui.rsvp.HORIZONTAL_BIAS_MAX
import com.kairo.reader.ui.rsvp.HORIZONTAL_BIAS_MIN
import com.kairo.reader.ui.rsvp.VERTICAL_BIAS_MAX
import com.kairo.reader.ui.rsvp.VERTICAL_BIAS_MIN

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun RsvpAdvancedSettingsContent(
    showAdvanced: Boolean,
    state: RsvpSettingsState,
    actions: RsvpSettingsActions,
) {
    val context = LocalContext.current
    val config = state.config
    val tempoMsPerWord = state.tempoMsPerWord
    val unlockExtremeSpeed = state.unlockExtremeSpeed
    val rsvpFontFamily = state.fontFamily
    val rsvpFontWeight = state.fontWeight
    val rsvpVerticalBias = state.verticalBias
    val rsvpHorizontalBias = state.horizontalBias
    val onTempoMsPerWordChange = actions.onTempoMsPerWordChange
    val onUnlockExtremeSpeedChange = actions.onUnlockExtremeSpeedChange
    val onRsvpFontWeightChange = actions.onFontWeightChange
    val onRsvpFontFamilyChange = actions.onFontFamilyChange
    val onRsvpVerticalBiasChange = actions.onVerticalBiasChange
    val onRsvpHorizontalBiasChange = actions.onHorizontalBiasChange
    fun updateConfig(updater: (RsvpConfig) -> RsvpConfig) = actions.onConfigChange(updater)
    if (showAdvanced) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_speed_limits_title),
                summary =
                stringResource(
                    if (unlockExtremeSpeed) {
                        R.string.rsvp_speed_limits_enabled
                    } else {
                        R.string.rsvp_speed_limits_disabled
                    },
                ),
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_unlock_extreme_speeds_title),
                    subtitle = stringResource(R.string.rsvp_unlock_extreme_speeds_subtitle_long),
                    checked = unlockExtremeSpeed,
                    onCheckedChange = { enabled ->
                        onUnlockExtremeSpeedChange(enabled)
                        if (!enabled && tempoMsPerWord < SAFE_MIN_TEMPO_MS_PER_WORD) {
                            onTempoMsPerWordChange(SAFE_MIN_TEMPO_MS_PER_WORD)
                        }
                    },
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_readability_floors_title),
                summary =
                stringResource(
                    R.string.rsvp_readability_floors_summary,
                    config.longWordChars,
                    config.subwordChunkPauseMs,
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_long_word_threshold_title),
                    valueLabel = { context.getString(R.string.format_chars, it.toInt()) },
                    rawValue = config.longWordChars.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(longWordChars = newValue.toInt().coerceIn(8, 14)) }
                    },
                    valueRange = 8f..14f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_split_word_pause_title),
                    subtitle = stringResource(R.string.rsvp_split_word_pause_subtitle),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.subwordChunkPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(subwordChunkPauseMs = newValue.toLong().coerceIn(0L, 200L))
                        }
                    },
                    valueRange = 0f..200f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_difficulty_model_title),
                summary =
                stringResource(
                    R.string.rsvp_difficulty_model_summary,
                    config.syllableExtraMs,
                    config.rarityExtraMaxMs,
                    formatPercent(context, config.complexityStrength),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_syllable_boost_title),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.syllableExtraMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(syllableExtraMs = newValue.toLong().coerceIn(0L, 45L)) }
                    },
                    valueRange = 0f..45f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_rarity_boost_title),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.rarityExtraMaxMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(rarityExtraMaxMs = newValue.toLong().coerceIn(0L, 200L)) }
                    },
                    valueRange = 0f..200f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_complexity_strength_title),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.complexityStrength * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(complexityStrength = (newValue / 100.0).coerceIn(0.0, 1.0))
                        }
                    },
                    valueRange = 0f..100f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_punctuation_pauses_title),
                summary =
                stringResource(
                    R.string.rsvp_punctuation_pauses_summary,
                    formatMultiplier(context, config.punctuationPauseFactor),
                    config.commaPauseMs,
                    config.periodPauseMs,
                    config.paragraphPauseMs,
                    formatMultiplier(context, config.pageBreakPauseMultiplier),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_breathing_title),
                    subtitle = stringResource(R.string.rsvp_punctuation_breathing_subtitle),
                    valueLabel = { context.getString(R.string.format_multiplier, it) },
                    rawValue = config.punctuationPauseFactor.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(punctuationPauseFactor = newValue.toDouble().coerceIn(0.5, 1.75))
                        }
                    },
                    valueRange = 0.5f..1.75f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_comma),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.commaPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(commaPauseMs = newValue.toLong().coerceIn(0L, 260L)) }
                    },
                    valueRange = 0f..260f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_period),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.periodPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(periodPauseMs = newValue.toLong().coerceIn(0L, 500L)) }
                    },
                    valueRange = 0f..500f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_dash),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.dashPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(dashPauseMs = newValue.toLong().coerceIn(0L, 320L)) }
                    },
                    valueRange = 0f..320f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_semicolon),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.semicolonPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(semicolonPauseMs = newValue.toLong().coerceIn(0L, 360L)) }
                    },
                    valueRange = 0f..360f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_colon),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.colonPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(colonPauseMs = newValue.toLong().coerceIn(0L, 360L)) }
                    },
                    valueRange = 0f..360f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_parentheses),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.parenthesesPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(parenthesesPauseMs = newValue.toLong().coerceIn(0L, 320L))
                        }
                    },
                    valueRange = 0f..320f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_quotes),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.quotePauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(quotePauseMs = newValue.toLong().coerceIn(0L, 200L)) }
                    },
                    valueRange = 0f..200f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_paragraph),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.paragraphPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(paragraphPauseMs = newValue.toLong().coerceIn(0L, 800L)) }
                    },
                    valueRange = 0f..800f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_paragraph_strength_title),
                    subtitle = stringResource(R.string.rsvp_punctuation_paragraph_strength_subtitle),
                    valueLabel = { context.getString(R.string.format_multiplier, it) },
                    rawValue = config.paragraphPauseMultiplier.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(paragraphPauseMultiplier = newValue.toDouble().coerceIn(0.75, 2.5))
                        }
                    },
                    valueRange = 0.75f..2.5f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_page_break_title),
                    subtitle = stringResource(R.string.rsvp_punctuation_page_break_subtitle),
                    valueLabel = { context.getString(R.string.format_multiplier, it) },
                    rawValue = config.pageBreakPauseMultiplier.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(pageBreakPauseMultiplier = newValue.toDouble().coerceIn(1.0, 5.0))
                        }
                    },
                    valueRange = 1f..5f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_pause_scaling_title),
                summary =
                stringResource(
                    R.string.rsvp_pause_scaling_summary,
                    formatPercent(context, config.pauseScaleExponent),
                    formatPercent(context, config.minPauseScale),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_scale_exponent_title),
                    subtitle = stringResource(R.string.rsvp_scale_exponent_subtitle),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.pauseScaleExponent * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(pauseScaleExponent = (newValue / 100.0).coerceIn(0.2, 0.9))
                        }
                    },
                    valueRange = 20f..90f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_minimum_scale_title),
                    subtitle = stringResource(R.string.rsvp_minimum_scale_subtitle),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.minPauseScale * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(minPauseScale = (newValue / 100.0).coerceIn(0.3, 1.0)) }
                    },
                    valueRange = 30f..100f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_context_shaping_title),
                summary =
                stringResource(
                    R.string.rsvp_context_shaping_summary,
                    formatDeltaPercent(context, config.parentheticalMultiplier),
                    formatPercent(context, config.dialogueMultiplier),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_parentheticals_title),
                    valueLabel = { context.getString(R.string.format_plus_percent, it.toInt()) },
                    rawValue = ((config.parentheticalMultiplier - 1.0) * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(parentheticalMultiplier = (1.0 + newValue / 100.0).coerceIn(1.0, 1.35))
                        }
                    },
                    valueRange = 0f..35f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_dialogue_pace_title),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.dialogueMultiplier * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(dialogueMultiplier = (newValue / 100.0).coerceIn(0.85, 1.05))
                        }
                    },
                    valueRange = 85f..105f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_dialogue_punctuation_title),
                    subtitle = stringResource(R.string.rsvp_dialogue_punctuation_subtitle),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.dialoguePunctuationScale * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                dialoguePunctuationScale =
                                percentToMultiplier(newValue, minValue = 0.5, maxValue = 1.0),
                            )
                        }
                    },
                    valueRange = 50f..100f,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_parenthetical_aside_title),
                    subtitle = stringResource(R.string.rsvp_parenthetical_aside_subtitle),
                    checked = config.useParentheticalAside,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useParentheticalAside = enabled) }
                    },
                )
                if (config.useParentheticalAside) {
                    DeferredSliderRow(
                        title = stringResource(R.string.rsvp_parenthetical_aside_pace_title),
                        subtitle = stringResource(R.string.rsvp_parenthetical_aside_pace_subtitle),
                        valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                        rawValue = (config.parentheticalAsideMultiplier * 100).toFloat(),
                        onCommit = { newValue ->
                            updateConfig {
                                it.copy(
                                    parentheticalAsideMultiplier =
                                    percentToMultiplier(newValue, minValue = 0.5, maxValue = 1.0),
                                )
                            }
                        },
                        valueRange = 50f..100f,
                    )
                }
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_adaptive_pacing_title),
                summary =
                stringResource(
                    R.string.rsvp_adaptive_pacing_summary,
                    config.adaptiveDifficultyMaxHoldMs,
                    config.complexWordHoldMs,
                    formatMultiplier(context, config.complexWordThreshold),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_difficulty_boost_title),
                    subtitle = stringResource(R.string.rsvp_difficulty_boost_subtitle),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.adaptiveDifficultyMaxHoldMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(adaptiveDifficultyMaxHoldMs = newValue.toLong().coerceIn(0L, 200L))
                        }
                    },
                    valueRange = 0f..200f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_complex_word_boost_title),
                    subtitle = stringResource(R.string.rsvp_complex_word_boost_subtitle),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.complexWordHoldMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(complexWordHoldMs = newValue.toLong().coerceIn(0L, 200L)) }
                    },
                    valueRange = 0f..200f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_complex_word_threshold_title),
                    subtitle = stringResource(R.string.rsvp_complex_word_threshold_subtitle),
                    valueLabel = { context.getString(R.string.format_multiplier, it) },
                    rawValue = config.complexWordThreshold.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(complexWordThreshold = newValue.toDouble().coerceIn(1.0, 1.6))
                        }
                    },
                    valueRange = 1f..1.6f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_rhythm_title),
                summary =
                stringResource(
                    R.string.rsvp_rhythm_summary,
                    formatPercent(context, config.smoothingAlpha),
                    formatDeltaPercent(context, config.clausePauseFactor),
                    stringResource(blinkModeLabelRes(config.blinkMode)),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_stability_title),
                    subtitle = stringResource(R.string.rsvp_stability_subtitle),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.smoothingAlpha * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(smoothingAlpha = (newValue / 100.0).coerceIn(0.0, 1.0)) }
                    },
                    valueRange = 0f..100f,
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_focal_stress_title),
                    subtitle = stringResource(R.string.rsvp_focal_stress_subtitle),
                    checked = config.useFocalStress,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useFocalStress = enabled) }
                    },
                )

                if (config.useFocalStress) {
                    DeferredSliderRow(
                        title = stringResource(R.string.rsvp_focal_support_title),
                        subtitle = stringResource(R.string.rsvp_focal_support_subtitle),
                        valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                        rawValue = (config.focalSupportCompression * 100).toFloat(),
                        onCommit = { newValue ->
                            updateConfig {
                                it.copy(
                                    focalSupportCompression =
                                    percentToMultiplier(newValue, minValue = 0.75, maxValue = 1.0),
                                )
                            }
                        },
                        valueRange = 75f..100f,
                    )
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_anticipatory_landing_title),
                    subtitle = stringResource(R.string.rsvp_anticipatory_landing_subtitle),
                    checked = config.useAnticipatoryLanding,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useAnticipatoryLanding = enabled) }
                    },
                )

                if (config.useAnticipatoryLanding) {
                    DeferredSliderRow(
                        title = stringResource(R.string.rsvp_anticipatory_landing_strength_title),
                        subtitle = stringResource(R.string.rsvp_anticipatory_landing_strength_subtitle),
                        valueLabel = { context.getString(R.string.format_plus_percent, it.toInt()) },
                        rawValue = ((config.anticipatoryLandingBoost - 1.0) * 100).toFloat(),
                        onCommit = { newValue ->
                            updateConfig {
                                it.copy(anticipatoryLandingBoost = (1.0 + newValue / 100.0).coerceIn(1.0, 1.2))
                            }
                        },
                        valueRange = 0f..20f,
                    )
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_clause_pacing_title),
                    subtitle = stringResource(R.string.rsvp_clause_pacing_subtitle),
                    checked = config.useClausePausing,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useClausePausing = enabled) }
                    },
                )

                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_clause_strength_title),
                    subtitle = stringResource(R.string.rsvp_clause_strength_subtitle),
                    valueLabel = { context.getString(R.string.format_plus_percent, it.toInt()) },
                    rawValue = ((config.clausePauseFactor.coerceIn(1.0, 1.6) - 1.0) * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(clausePauseFactor = (1.0 + newValue / 100.0).coerceIn(1.0, 1.6))
                        }
                    },
                    valueRange = 0f..60f,
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_prosody_pacing_title),
                    subtitle = stringResource(R.string.rsvp_prosody_pacing_subtitle),
                    checked = config.useProsodyPacing,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useProsodyPacing = enabled) }
                    },
                )

                if (config.useProsodyPacing) {
                    DeferredSliderRow(
                        title = stringResource(R.string.rsvp_prosody_strength_title),
                        subtitle = stringResource(R.string.rsvp_prosody_strength_subtitle),
                        valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                        rawValue = (config.prosodyStrength * 100).toFloat(),
                        onCommit = { newValue ->
                            updateConfig {
                                it.copy(prosodyStrength = (newValue / 100.0).coerceIn(0.0, 1.6))
                            }
                        },
                        valueRange = 0f..160f,
                    )
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_punctuation_landing_title),
                    subtitle = stringResource(R.string.rsvp_punctuation_landing_subtitle),
                    checked = config.usePunctuationLandingHold,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(usePunctuationLandingHold = enabled) }
                    },
                )

                BlinkModeSelector(
                    selected = config.blinkMode,
                    onSelect = { mode -> updateConfig { it.copy(blinkMode = mode) } },
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_display_details_title),
                summary =
                stringResource(
                    R.string.rsvp_display_details_summary,
                    stringResource(rsvpFontFamilyLabelRes(rsvpFontFamily)),
                    stringResource(rsvpFontWeightLabelRes(rsvpFontWeight)),
                    formatBias(context, rsvpVerticalBias),
                    formatBias(context, rsvpHorizontalBias),
                ),
            ) {
                RsvpFontFamilySelector(
                    selected = rsvpFontFamily,
                    onFontFamilyChange = onRsvpFontFamilyChange,
                )
                RsvpFontWeightSelector(
                    selected = rsvpFontWeight,
                    onFontWeightChange = onRsvpFontWeightChange,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_vertical_position_title),
                    valueLabel = { context.getString(R.string.format_percent, (it * 100).toInt()) },
                    rawValue = rsvpVerticalBias,
                    onCommit = onRsvpVerticalBiasChange,
                    valueRange = VERTICAL_BIAS_MIN..VERTICAL_BIAS_MAX,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_left_bias_title),
                    valueLabel = { context.getString(R.string.format_percent, (it * 100).toInt()) },
                    rawValue = rsvpHorizontalBias,
                    onCommit = onRsvpHorizontalBiasChange,
                    valueRange = HORIZONTAL_BIAS_MIN..HORIZONTAL_BIAS_MAX,
                )
            }
        }
    }
}
