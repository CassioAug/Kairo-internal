package com.kairo.reader.sample

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter

object SampleBooks {
    const val STARTER_BOOK_ID = "kairo-starter-book"
    const val STARTER_BOOK_TITLE = "Kairo Starter Book"

    fun defaultSample(): Book = starterBook()

    fun starterBook(): Book =
        Book(
            id = BookId(STARTER_BOOK_ID),
            title = STARTER_BOOK_TITLE,
            authors = listOf("Kairo Team"),
            languageTag = "en",
            coverImage = null,
            chapters =
                listOf(
                    chapter(
                        index = 0,
                        title = "Welcome",
                        paragraphs =
                            listOf(
                                "Welcome to Kairo. This starter book exists so the first-run tutorial can show you real reading screens instead of empty placeholders.",
                                "You can keep it for practice, or delete it later like any other book in your library.",
                                "When you are ready, move to the next chapter and try the Reader tools for real.",
                            ),
                    ),
                    chapter(
                        index = 1,
                        title = "Reader Basics",
                        paragraphs =
                            listOf(
                                "In Reader mode, the highlighted focus word marks your current launch point for RSVP. Tap that word, or use the play dock, to jump straight into speed reading.",
                                "Use the previous and next buttons near the top to change pages or chapters. You can also swipe left or right across the reader to move backward or forward.",
                                "Open the Reader menu to reach bookmarks, the table of contents, focus mode, and Reader settings without leaving the book.",
                            ),
                    ),
                    chapter(
                        index = 2,
                        title = "RSVP Practice",
                        paragraphs =
                            listOf(
                                "RSVP keeps your eyes centered while words appear in one stable position. Tap the screen to pause or resume playback.",
                                "When controls are visible, use the center button for play or pause. The side buttons step backward and forward one frame at a time.",
                                "Swipe up or down to adjust tempo. Swipe left or right to scrub through the session. If you want more options, pause and open the settings button in the top corner.",
                            ),
                    ),
                    chapter(
                        index = 3,
                        title = "Make It Yours",
                        paragraphs =
                            listOf(
                                "The main Settings screen gives you language, RSVP tuning, Reader appearance, focus mode, and the Starting tutorial replay entry.",
                                "Inside Reader and RSVP, the local settings surfaces let you change the view without backing out of the current book.",
                                "Once you are comfortable, import your own EPUB or MOBI files. If you no longer need this guide, delete Kairo Starter Book from the library.",
                            ),
                    ),
                ),
        )

    private fun chapter(
        index: Int,
        title: String,
        paragraphs: List<String>,
    ): Chapter {
        val plainText = paragraphs.joinToString(separator = "\n\n")
        val htmlContent =
            buildString {
                append("<html><body>")
                paragraphs.forEach { paragraph ->
                    append("<p>")
                    append(paragraph.escapeHtml())
                    append("</p>")
                }
                append("</body></html>")
            }
        return Chapter(
            index = index,
            title = title,
            htmlContent = htmlContent,
            plainText = plainText,
        )
    }

    private fun String.escapeHtml(): String =
        this
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
