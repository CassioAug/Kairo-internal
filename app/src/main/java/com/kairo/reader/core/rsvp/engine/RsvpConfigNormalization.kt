package com.kairo.reader.core.rsvp.engine

import com.kairo.reader.core.model.RsvpConfig

internal fun RsvpConfig.normalizedForPlayback(): RsvpConfig {
    val safeMinWordMs = minWordMs.coerceAtLeast(1L)
    return copy(
        tempoMsPerWord = tempoMsPerWord.coerceAtLeast(1L),
        minWordMs = safeMinWordMs,
        longWordMinMs = longWordMinMs.coerceAtLeast(safeMinWordMs),
        longWordChars = longWordChars.coerceAtLeast(1),
        syllableExtraMs = syllableExtraMs.coerceAtLeast(0L),
        rarityExtraMaxMs = rarityExtraMaxMs.coerceAtLeast(0L),
        complexityStrength = complexityStrength.coerceAtLeast(0.0),
        lengthStrength = lengthStrength.coerceAtLeast(0.0),
        lengthExponent = lengthExponent.coerceAtLeast(0.1),
        maxWordsPerUnit = maxWordsPerUnit.coerceAtLeast(1),
        maxCharsPerUnit = maxCharsPerUnit.coerceAtLeast(1),
        subwordChunkPauseMs = subwordChunkPauseMs.coerceAtLeast(0L),
        commaPauseMs = commaPauseMs.coerceAtLeast(0L),
        periodPauseMs = periodPauseMs.coerceAtLeast(0L),
        semicolonPauseMs = semicolonPauseMs.coerceAtLeast(0L),
        colonPauseMs = colonPauseMs.coerceAtLeast(0L),
        dashPauseMs = dashPauseMs.coerceAtLeast(0L),
        parenthesesPauseMs = parenthesesPauseMs.coerceAtLeast(0L),
        quotePauseMs = quotePauseMs.coerceAtLeast(0L),
        sentenceEndPauseMs = sentenceEndPauseMs.coerceAtLeast(0L),
        paragraphPauseMs = paragraphPauseMs.coerceAtLeast(0L),
        paragraphPauseMultiplier = paragraphPauseMultiplier.coerceIn(0.75, 2.5),
        pageBreakPauseMultiplier = pageBreakPauseMultiplier.coerceIn(1.0, 5.0),
        pauseScaleExponent = pauseScaleExponent.coerceAtLeast(0.0),
        minPauseScale = minPauseScale.coerceIn(0.0, MAX_MIN_PAUSE_SCALE),
        startDelayMs = startDelayMs.coerceAtLeast(0L),
        endDelayMs = endDelayMs.coerceAtLeast(0L),
        rampUpFrames = rampUpFrames.coerceAtLeast(0),
        rampDownFrames = rampDownFrames.coerceAtLeast(0),
        maxChunkLength = maxChunkLength.coerceAtLeast(0),
        punctuationPauseFactor = punctuationPauseFactor.coerceAtLeast(0.0),
        longWordMultiplier = longWordMultiplier.coerceAtLeast(0.0),
        adaptiveDifficultyMaxHoldMs = adaptiveDifficultyMaxHoldMs.coerceAtLeast(0L),
        complexWordHoldMs = complexWordHoldMs.coerceAtLeast(0L),
        complexWordThreshold = complexWordThreshold.coerceAtLeast(0.0),
        clausePauseFactor = clausePauseFactor.coerceAtLeast(1.0),
        parentheticalMultiplier = parentheticalMultiplier.coerceAtLeast(0.0),
        dialogueMultiplier = dialogueMultiplier.coerceAtLeast(0.0),
        smoothingAlpha = smoothingAlpha.coerceIn(0.0, 1.0),
        maxSpeedupFactor = maxSpeedupFactor.coerceAtLeast(1.0),
        maxSlowdownFactor = maxSlowdownFactor.coerceAtLeast(1.0),
        focalSupportCompression = focalSupportCompression.coerceIn(MIN_FOCAL_SUPPORT_COMPRESSION, 1.0),
        anticipatoryLandingBoost = anticipatoryLandingBoost.coerceIn(1.0, MAX_ANTICIPATORY_LANDING_BOOST),
        dialoguePunctuationScale = dialoguePunctuationScale.coerceIn(0.5, 1.0),
        parentheticalAsideMultiplier = parentheticalAsideMultiplier.coerceIn(0.5, 1.0),
    )
}

internal fun RsvpConfig.frameTimingKey(): RsvpConfig =
    copy(
        baseWpm = 0,
        orpEnabled = false,
        orpHighlightEnabled = false,
        orpGuideEnabled = false,
        orpGuideBrightness = 0.0,
        orpGuideThickness = 0.0,
    )
