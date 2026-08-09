package com.kairo.reader.ui.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderParagraphAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun paragraphExposesClickLongClickAndSelectionActionsToTalkBack() {
        val selection = mutableStateOf<IntRange?>(null)
        var startedAt: Int? = null
        var extendedTo: Int? = null
        var cancelled = false
        composeRule.setContent {
            KairoTheme {
                ParagraphText(
                    state =
                        ParagraphTextState(
                            paragraph =
                                Paragraph(
                                    tokens =
                                        listOf(
                                            Token("One", TokenType.WORD),
                                            Token("two", TokenType.WORD),
                                        ),
                                    startIndex = 0,
                                ),
                            focusIndex = 0,
                            fontSizeSp = 18f,
                            textBrightness = 1f,
                            timedReadingMode = TimedReadingMode.RSVP,
                            selectionRange = selection.value,
                        ),
                    actions =
                        ParagraphTextActions(
                            onFocusChange = {},
                            onStartTimedReading = {},
                            onSelectionStart = { tokenIndex ->
                                startedAt = tokenIndex
                                selection.value = tokenIndex..tokenIndex
                            },
                            onSelectionExtend = { extendedTo = it },
                            onSelectionCancel = { cancelled = true },
                        ),
                )
            }
        }

        val paragraph = composeRule.onNodeWithText("One two")
        paragraph.assertHasClickAction()
        paragraph.performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.runOnIdle { assertEquals(0, startedAt) }

        val extendLabel =
            composeRule.activity.getString(R.string.reader_extend_selection_forward_action)
        composeRule.runOnIdle {
            val actions = paragraph.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            assertTrue(actions.first { it.label == extendLabel }.action())
        }
        composeRule.runOnIdle { assertEquals(1, extendedTo) }

        val cancelLabel = composeRule.activity.getString(R.string.reader_cancel_selection_action)
        composeRule.runOnIdle {
            val actions = paragraph.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            assertTrue(actions.first { it.label == cancelLabel }.action())
        }
        composeRule.runOnIdle { assertTrue(cancelled) }
    }
}
