package com.kairo.reader.ui.reader

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
internal fun ReaderContent(
    modifier: Modifier = Modifier,
    book: Book,
    chapterIndex: Int,
    coverImage: ByteArray?,
    isLoading: Boolean,
    loadErrorMessage: String?,
    isCoverChapter: Boolean,
    isPagedChapter: Boolean,
    resolvedPageIndex: Int,
    fullScreenTitlePageImagePath: String?,
    headerCarouselImages: List<String>,
    showHeaderCarousel: Boolean,
    isBlankPage: Boolean,
    displayBlocks: List<ReaderBlock>,
    listState: LazyListState,
    listStateKey: String,
    invertedScroll: Boolean,
    bottomInset: Dp,
    overlayBottomPadding: Dp,
    focusIndex: Int,
    fontSizeSp: Float,
    textBrightness: Float,
    onSafeFocusChange: (Int) -> Unit,
    onStartRsvpForToken: (Int) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onSwipePreviewChange: (ReaderSwipeDirection?, Float) -> Unit,
    onOpenFullScreenImage: (String) -> Unit,
    invertedScrollCommands: MutableSharedFlow<InvertedScrollCommand>,
    onChapterSelected: ((Int) -> Unit)? = null,
) {
    if (isLoading) {
        ReaderLoadingState(
            modifier = modifier,
            book = book,
            coverImage = coverImage,
            isCoverChapter = isCoverChapter,
        )
        return
    }

    if (loadErrorMessage != null) {
        ReaderErrorState(modifier = modifier, message = loadErrorMessage)
        return
    }

    if (displayBlocks.isEmpty() &&
        !isBlankPage &&
        !isCoverChapter &&
        fullScreenTitlePageImagePath == null &&
        headerCarouselImages.isEmpty()
    ) {
        ReaderEmptyState(modifier = modifier)
        return
    }

    val gestureModifier =
        Modifier.pointerInput(listStateKey, invertedScroll, chapterIndex) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                onSwipePreviewChange(null, 0f)

                val touchSlop = viewConfiguration.touchSlop
                val swipeThreshold = touchSlop * 4f

                var totalX = 0f
                var totalY = 0f
                var axis = Axis.Horizontal
                var axisResolved = false

                val tracker = VelocityTracker()
                tracker.addPosition(SystemClock.uptimeMillis(), down.position)

                while (true) {
                    val event = awaitPointerEvent()
                    val change =
                        event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break

                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y
                    totalX += dx
                    totalY += dy

                    if (!axisResolved) {
                        val absX = abs(totalX)
                        val absY = abs(totalY)
                        if (absX > touchSlop || absY > touchSlop) {
                            axis = if (absX > absY) Axis.Horizontal else Axis.Vertical
                            axisResolved = true
                        } else {
                            continue
                        }
                    }

                    when (axis) {
                        Axis.Horizontal -> {
                            val direction =
                                when {
                                    totalX > 0f -> ReaderSwipeDirection.Previous
                                    totalX < 0f -> ReaderSwipeDirection.Next
                                    else -> null
                                }
                            onSwipePreviewChange(
                                direction,
                                (abs(totalX) / swipeThreshold).coerceIn(0f, 1f),
                            )
                        }
                        Axis.Vertical -> {
                            onSwipePreviewChange(null, 0f)
                            if (!invertedScroll) {
                                // Let LazyColumn handle normal vertical scrolling.
                                break
                            }
                            tracker.addPosition(
                                SystemClock.uptimeMillis(),
                                change.position
                            )
                            invertedScrollCommands.tryEmit(
                                InvertedScrollCommand.Drag(dy)
                            )
                        }
                    }
                }

                if (axisResolved) {
                    when (axis) {
                        Axis.Horizontal -> {
                            when {
                                totalX <= -swipeThreshold -> onNextPage()
                                totalX >= swipeThreshold -> onPrevPage()
                            }
                            onSwipePreviewChange(null, 0f)
                        }
                        Axis.Vertical -> {
                            onSwipePreviewChange(null, 0f)
                            if (invertedScroll) {
                                val velocity = tracker.calculateVelocity().y
                                if (abs(velocity) > 200f) {
                                    invertedScrollCommands.tryEmit(
                                        InvertedScrollCommand.Fling(velocity)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .then(gestureModifier),
    ) {
        val configuration = LocalConfiguration.current
        val compactLandscape =
            configuration.screenWidthDp > configuration.screenHeightDp &&
                configuration.screenHeightDp <= 480
        val viewportHeight = configuration.screenHeightDp.dp
        val paragraphSpacing =
            if (compactLandscape) {
                (fontSizeSp * 0.32f).dp.coerceIn(6.dp, 10.dp)
            } else {
                (fontSizeSp * 0.45f).dp.coerceIn(10.dp, 14.dp)
            }

        // LAZY block-based rendering (text + images)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !invertedScroll,
            verticalArrangement = Arrangement.spacedBy(paragraphSpacing),
            contentPadding = PaddingValues(bottom = bottomInset + overlayBottomPadding),
        ) {
            if (isCoverChapter && (!isPagedChapter || resolvedPageIndex <= 0)) {
                item(key = "book_cover_full_${book.id.value}") {
                    val context = LocalContext.current
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 2.dp,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(viewportHeight)
                            .clip(RoundedCornerShape(14.dp)),
                    ) {
                        AsyncImage(
                            model =
                            remember(coverImage, book.id.value) {
                                ImageRequest
                                    .Builder(context)
                                    .data(coverImage)
                                    .memoryCacheKey(
                                        "book_cover_full_${book.id.value}"
                                    )
                                    .crossfade(false)
                                    .build()
                            },
                            contentDescription =
                            stringResource(R.string.content_desc_cover_of_title, book.title),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
            if (fullScreenTitlePageImagePath != null &&
                (!isPagedChapter || resolvedPageIndex <= 0)
            ) {
                item(
                    key = "title_page_full_${book.id.value}_$fullScreenTitlePageImagePath"
                ) {
                    val context = LocalContext.current
                    val file =
                        remember(fullScreenTitlePageImagePath) {
                            File(context.filesDir, fullScreenTitlePageImagePath)
                        }
                    if (file.exists()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 2.dp,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(viewportHeight)
                                .clip(RoundedCornerShape(14.dp)),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription =
                                    stringResource(
                                        R.string.content_desc_title_page_of_title,
                                        book.title,
                                    ),
                                    modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .openReaderImageOnLongPress(
                                            imagePath = fullScreenTitlePageImagePath,
                                            onOpen = onOpenFullScreenImage,
                                        ),
                                    contentScale = ContentScale.Fit,
                                )
                                ReaderImageOpenHint(
                                    modifier =
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (showHeaderCarousel) {
                item(key = "chapter_images_$chapterIndex") {
                    ChapterImages(
                        imagePaths = headerCarouselImages,
                        onImageOpen = onOpenFullScreenImage,
                    )
                }
            }
            items(
                items = displayBlocks,
                key = { it.key },
            ) { block ->
                when (block) {
                    is ReaderParagraphBlock -> {
                        ParagraphText(
                            paragraph = block.paragraph,
                            focusIndex = block.paragraph.focusIndexOrNone(focusIndex),
                            fontSizeSp = fontSizeSp,
                            textBrightness = textBrightness,
                            onFocusChange = onSafeFocusChange,
                            onStartRsvp = onStartRsvpForToken,
                            onChapterSelected = onChapterSelected,
                        )
                    }
                    is ReaderImageBlock -> {
                        InlineImageBlock(
                            imagePath = block.imagePath,
                            onOpen = onOpenFullScreenImage,
                        )
                    }
                }
            }
        }

    }
}

@Composable
internal fun ReaderSwipePageChrome(
    direction: ReaderSwipeDirection?,
    progress: Float,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "readerSwipePageChromeProgress",
    )
    val activeDirection = direction ?: return
    val canNavigate =
        when (activeDirection) {
            ReaderSwipeDirection.Previous -> canGoPrev
            ReaderSwipeDirection.Next -> canGoNext
        }
    val alignment =
        when (activeDirection) {
            ReaderSwipeDirection.Previous -> Alignment.CenterStart
            ReaderSwipeDirection.Next -> Alignment.CenterEnd
        }
    val railShape =
        when (activeDirection) {
            ReaderSwipeDirection.Previous ->
                RoundedCornerShape(topEnd = 48.dp, bottomEnd = 48.dp)
            ReaderSwipeDirection.Next ->
                RoundedCornerShape(topStart = 48.dp, bottomStart = 48.dp)
        }
    val accentColor =
        if (canNavigate) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val railBrush =
        when (activeDirection) {
            ReaderSwipeDirection.Previous ->
                Brush.horizontalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.24f),
                        accentColor.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                )
            ReaderSwipeDirection.Next ->
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = 0.08f),
                        accentColor.copy(alpha = 0.24f),
                    ),
                )
        }
    val icon =
        when (activeDirection) {
            ReaderSwipeDirection.Previous -> Icons.AutoMirrored.Filled.ArrowBack
            ReaderSwipeDirection.Next -> Icons.AutoMirrored.Filled.ArrowForward
        }
    val iconDescription =
        when (activeDirection) {
            ReaderSwipeDirection.Previous ->
                stringResource(R.string.content_desc_reader_swipe_previous_page)
            ReaderSwipeDirection.Next ->
                stringResource(R.string.content_desc_reader_swipe_next_page)
        }
    val iconOffset =
        when (activeDirection) {
            ReaderSwipeDirection.Previous -> (-18).dp + (14.dp * animatedProgress)
            ReaderSwipeDirection.Next -> 18.dp - (14.dp * animatedProgress)
        }

    Box(
        modifier =
        modifier
            .alpha(animatedProgress),
    ) {
        Box(
            modifier =
            Modifier
                .align(alignment)
                .fillMaxHeight()
                .width(86.dp)
                .background(brush = railBrush, shape = railShape),
        )
        Surface(
            modifier =
            Modifier
                .align(alignment)
                .padding(horizontal = 8.dp)
                .offset(x = iconOffset),
            shape = CircleShape,
            color =
            accentColor.copy(
                alpha =
                if (canNavigate) {
                    0.92f
                } else {
                    0.44f
                },
            ),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                modifier =
                Modifier
                    .size(48.dp)
                    .padding(12.dp),
            )
        }
    }
}

internal enum class ReaderSwipeDirection {
    Previous,
    Next,
}

private fun Paragraph.focusIndexOrNone(focusIndex: Int): Int {
    val endIndex = startIndex + tokens.size - 1
    return if (focusIndex in startIndex..endIndex) focusIndex else NO_PARAGRAPH_FOCUS_INDEX
}

private const val NO_PARAGRAPH_FOCUS_INDEX = -1

@Composable
private fun ReaderLoadingState(
    modifier: Modifier,
    book: Book,
    coverImage: ByteArray?,
    isCoverChapter: Boolean,
) {
    if (isCoverChapter) {
        Box(
            modifier =
            modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val context = LocalContext.current
            Surface(
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                AsyncImage(
                    model =
                    remember(coverImage, book.id.value) {
                        ImageRequest
                            .Builder(context)
                            .data(coverImage)
                            .memoryCacheKey("book_cover_full_${book.id.value}")
                            .crossfade(false)
                            .build()
                    },
                    contentDescription =
                    stringResource(R.string.content_desc_cover_of_title, book.title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            CircularProgressIndicator()
        }
    } else {
        Box(
            modifier =
            modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ReaderEmptyState(
    modifier: Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.reader_empty_chapter),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReaderErrorState(
    modifier: Modifier,
    message: String,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.reader_chapter_load_failed, message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
