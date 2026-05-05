package app.kairo.reader.core.model

data class RsvpFrame(
    val tokens: List<Token>,
    val durationMs: Long,
    // Index into the original (non-expanded) token list for position tracking
    // Used when syncing RSVP position back to the reader view
    val originalTokenIndex: Int = 0,
    // Cursor into the expanded RSVP token stream for exact frame/chunk resume
    val resumeCursor: Int = originalTokenIndex,
    // First original token index after this frame's consumed reading unit.
    val nextOriginalTokenIndex: Int = originalTokenIndex + 1,
)
