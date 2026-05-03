package app.kairo.reader.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpReadabilityModeTest {
    @Test
    fun punctuationStaysVisibleAtHighSpeed() {
        val config = RsvpConfig()

        assertFalse(config.prefersSimplifiedOrpDisplay(tempoMs = 75L))
        assertFalse(config.prefersSimplifiedOrpDisplay(tempoMs = 55L))
    }

    @Test
    fun highSpeedReadabilityFloorsNarrowAsTempoIncreases() {
        val config =
            RsvpConfig(
                minWordMs = 45L,
                longWordMinMs = 120L,
            )

        val shortWord = Token(text = "cat", type = TokenType.WORD)
        val splitChunk = Token(text = "inter", type = TokenType.WORD, isSubwordChunk = true)

        val standardShort = config.wordFloorMsForReadability(shortWord, tempoMs = 120L)
        val fastShort = config.wordFloorMsForReadability(shortWord, tempoMs = 55L)
        val standardChunk = config.wordFloorMsForReadability(splitChunk, tempoMs = 120L)
        val fastChunk = config.wordFloorMsForReadability(splitChunk, tempoMs = 55L)

        assertTrue(fastShort < standardShort)
        assertTrue(fastChunk < standardChunk)
        assertTrue(config.speedNarrowingFactor(tempoMs = 120L) > config.speedNarrowingFactor(tempoMs = 55L))
        assertTrue(fastShort >= 20L)
        assertTrue(fastChunk >= 60L)
    }
}
