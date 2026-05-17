package com.kairo.reader.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartingTutorialCoordinatorTest {
    @Test
    fun clampTutorialStepIndex_keepsValidRestoredIndex() {
        val steps = startingTutorialSteps(includeReaderAndRsvp = false)

        assertEquals(2, clampTutorialStepIndex(2, steps))
    }

    @Test
    fun clampTutorialStepIndex_clampsRestoredLongSessionIndexToCurrentSteps() {
        val shorterSteps = startingTutorialSteps(includeReaderAndRsvp = false)
        val longerSteps = startingTutorialSteps(includeReaderAndRsvp = true)
        val restoredIndexFromLongerSession = longerSteps.lastIndex

        assertEquals(
            shorterSteps.lastIndex,
            clampTutorialStepIndex(restoredIndexFromLongerSession, shorterSteps),
        )
    }

    @Test
    fun clampTutorialStepIndex_returnsNullForEmptyStepList() {
        assertNull(clampTutorialStepIndex(3, emptyList()))
    }
}
