package com.kairo.reader.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.shouldInsertSpaceBeforeToken
import com.kairo.reader.ui.theme.MerriweatherFontFamily

@Composable
internal fun ParagraphText(
    paragraph: Paragraph,
    focusIndex: Int,
    fontSizeSp: Float,
    textBrightness: Float,
    onFocusChange: (Int) -> Unit,
    onStartRsvp: (Int) -> Unit,
    onChapterSelected: ((Int) -> Unit)? = null,
) {
    val baseStyle =
        TextStyle(
            fontFamily = MerriweatherFontFamily,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.5f).sp,
            color = MaterialTheme.colorScheme.onBackground.copy(
                alpha = textBrightness.coerceIn(0.55f, 1.0f)
            ),
        )
    val paragraphIndent =
        remember(fontSizeSp) {
            ParagraphStyle(textIndent = TextIndent(firstLine = (fontSizeSp * 0.55f).sp))
        }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val focusStyle =
        remember(primary) {
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = primary.copy(alpha = 0.95f),
                background = primary.copy(alpha = 0.16f),
            )
        }
    val linkStyle =
        remember(tertiary) {
            SpanStyle(
                color = tertiary,
                textDecoration = TextDecoration.Underline,
            )
        }
    val localFocusIndex =
        remember(paragraph.startIndex, paragraph.tokens.size, focusIndex) {
            (focusIndex - paragraph.startIndex)
                .takeIf { localIndex -> localIndex in paragraph.tokens.indices }
                ?: NO_PARAGRAPH_FOCUS
        }

    val annotated =
        remember(
            paragraph.tokens,
            paragraph.startIndex,
            localFocusIndex,
            focusStyle,
            linkStyle,
            paragraphIndent,
        ) {
            buildAnnotatedString {
                paragraph.tokens.forEachIndexed { localIndex, token ->
                    if (token.type == TokenType.PARAGRAPH_BREAK ||
                        token.type == TokenType.PAGE_BREAK
                    ) {
                        return@forEachIndexed
                    }
                    val globalIndex = paragraph.startIndex + localIndex

                    val prevToken = if (localIndex > 0) paragraph.tokens[localIndex - 1] else null
                    val needsSpaceBefore =
                        shouldInsertSpaceBeforeToken(token, prevToken, localIndex)

                    if (needsSpaceBefore) append(" ")

                    val start = length
                    append(token.text)
                    val end = length

                    addStringAnnotation(
                        tag = "tokenIndex",
                        annotation = globalIndex.toString(),
                        start = start,
                        end = end
                    )

                    // Add link annotation if token has a link
                    if (token.linkChapterIndex != null) {
                        addStringAnnotation(
                            tag = "chapterLink",
                            annotation = token.linkChapterIndex.toString(),
                            start = start,
                            end = end
                        )
                        addStyle(linkStyle, start, end)
                    }

                    if (localIndex == localFocusIndex) addStyle(focusStyle, start, end)
                }
                addStyle(paragraphIndent, start = 0, end = length)
            }
        }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentAnnotated by rememberUpdatedState(annotated)
    val currentFocusIndex by rememberUpdatedState(focusIndex)
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)
    val currentOnStartRsvp by rememberUpdatedState(onStartRsvp)
    val currentOnChapterSelected by rememberUpdatedState(onChapterSelected)

    Text(
        text = annotated,
        style = baseStyle,
        modifier =
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { position ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val annotations = currentAnnotated
                        val offset = layout.getOffsetForPosition(position).coerceIn(
                            0,
                            (
                                annotations.length -
                                    1
                                ).coerceAtLeast(0)
                        )

                        // First check for chapter link tap
                        val linkHit = annotations.getStringAnnotations(
                            "chapterLink",
                            offset,
                            offset
                        ).firstOrNull()
                        val chapterSelected = currentOnChapterSelected
                        if (linkHit != null && chapterSelected != null) {
                            val chapterIndex = linkHit.item.toIntOrNull()
                            if (chapterIndex != null) {
                                chapterSelected(chapterIndex)
                                return@detectTapGestures
                            }
                        }

                        // Otherwise handle normal token tap
                        val hit =
                            annotations.getStringAnnotations(
                                "tokenIndex",
                                offset,
                                offset
                            ).firstOrNull()
                                ?: return@detectTapGestures
                        val tokenIndex = hit.item.toIntOrNull() ?: return@detectTapGestures
                        if (tokenIndex ==
                            currentFocusIndex
                        ) {
                            currentOnStartRsvp(tokenIndex)
                        } else {
                            currentOnFocusChange(tokenIndex)
                        }
                    },
                    onLongPress = { position ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val annotations = currentAnnotated
                        val offset = layout.getOffsetForPosition(position).coerceIn(
                            0,
                            (
                                annotations.length -
                                    1
                                ).coerceAtLeast(0)
                        )
                        val hit =
                            annotations.getStringAnnotations(
                                "tokenIndex",
                                offset,
                                offset
                            ).firstOrNull()
                                ?: return@detectTapGestures
                        val tokenIndex = hit.item.toIntOrNull() ?: return@detectTapGestures
                        if (tokenIndex != currentFocusIndex) currentOnFocusChange(tokenIndex)
                        currentOnStartRsvp(tokenIndex)
                    },
                )
            },
        onTextLayout = { layoutResult = it },
    )
}

private const val NO_PARAGRAPH_FOCUS = -1
