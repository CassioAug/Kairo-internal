package com.kairo.reader.core.model

/** Unambiguous opening and closing marks shared by reader, RSVP, and ticker rendering. */
internal val PAIRED_OPENING_PUNCTUATION_CHARS =
    setOf(
        '(',
        '[',
        '{',
        '\u201C',
        '\u2018',
        '\u3008',
        '\u300A',
        '\u300C',
        '\u300E',
        '\u3010',
        '\u3014',
        '\u3016',
        '\u3018',
        '\u301A',
        '\uFF08',
        '\uFF3B',
        '\uFF5B',
    )

internal val PAIRED_CLOSING_PUNCTUATION_CHARS =
    setOf(
        ')',
        ']',
        '}',
        '\u201D',
        '\u2019',
        '\u3009',
        '\u300B',
        '\u300D',
        '\u300F',
        '\u3011',
        '\u3015',
        '\u3017',
        '\u3019',
        '\u301B',
        '\uFF09',
        '\uFF3D',
        '\uFF5D',
    )
