package app.kairo.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import app.kairo.reader.core.model.Book
import app.kairo.reader.R

@Composable
internal fun ReaderHeader(
    book: Book,
    chapterIndex: Int,
    chapterTitle: String?,
    coverImage: ByteArray?,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShowMenu: () -> Unit,
    compactMode: Boolean,
    landscapeCompact: Boolean,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    pageLabel: String?,
    progressPercent: Int?,
    progressFraction: Float,
    etaLabel: String?,
    navigationModifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chapterProgress =
        remember(book.chapters, chapterIndex) {
            resolveReaderChapterProgress(book.chapters, chapterIndex)
        }
    val compressedChrome = compactMode || landscapeCompact
    val iconButtonSize = if (landscapeCompact) 40.dp else 48.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (landscapeCompact) 6.dp else 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = book.title,
                    style =
                    if (compressedChrome) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                    chapterTitle
                        ?: stringResource(
                            R.string.reader_chapter_title,
                            chapterIndex + 1,
                        ),
                    style =
                        if (landscapeCompact) {
                            MaterialTheme.typography.bodySmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = navigationModifier,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = onPrev,
                        enabled = canGoPrev,
                        modifier = Modifier.size(iconButtonSize),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_previous_page),
                            tint =
                            if (canGoPrev) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = canGoNext,
                        modifier = Modifier.size(iconButtonSize),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.content_desc_next_page),
                            tint =
                            if (canGoNext) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                        )
                    }
                }
                IconButton(
                    onClick = onToggleDetails,
                    modifier = Modifier.size(iconButtonSize),
                ) {
                    Icon(
                        imageVector =
                        if (detailsExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = stringResource(R.string.content_desc_reader_details),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = onShowMenu,
                    modifier = menuModifier.size(iconButtonSize),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.content_desc_reader_menu),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }

        AnimatedVisibility(visible = detailsExpanded) {
            val chapterProgressLabel =
                stringResource(
                    R.string.reader_chapter_of_total,
                    chapterProgress.currentNumber,
                    chapterProgress.totalNumber,
                )
            if (landscapeCompact) {
                ReaderHeaderDetailsCompact(
                    chapterProgressLabel = chapterProgressLabel,
                    pageLabel = pageLabel,
                    progressPercent = progressPercent,
                    progressFraction = progressFraction,
                    etaLabel = etaLabel,
                )
            } else {
                ReaderHeaderDetails(
                    book = book,
                    coverImage = coverImage,
                    chapterProgressLabel = chapterProgressLabel,
                    pageLabel = pageLabel,
                    progressPercent = progressPercent,
                    progressFraction = progressFraction,
                    etaLabel = etaLabel,
                    context = context,
                )
            }
        }
    }
}

@Composable
private fun ReaderHeaderDetailsCompact(
    chapterProgressLabel: String,
    pageLabel: String?,
    progressPercent: Int?,
    progressFraction: Float,
    etaLabel: String?,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chapterProgressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                )
                pageLabel?.let {
                    ReaderMetaPill(text = it)
                }
                progressPercent?.let {
                    ReaderMetaPill(text = stringResource(R.string.format_percent, it))
                }
            }
            etaLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReaderHeaderDetails(
    book: Book,
    coverImage: ByteArray?,
    chapterProgressLabel: String,
    pageLabel: String?,
    progressPercent: Int?,
    progressFraction: Float,
    etaLabel: String?,
    context: android.content.Context,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (coverImage != null && coverImage.isNotEmpty()) {
                AsyncImage(
                    model =
                    remember(coverImage, book.id.value) {
                        ImageRequest
                            .Builder(context)
                            .data(coverImage)
                            .memoryCacheKey("book_cover_thumb_${book.id.value}")
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = null,
                    modifier =
                    Modifier
                        .size(width = 46.dp, height = 60.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = chapterProgressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pageLabel?.let {
                        ReaderMetaPill(text = it)
                    }
                    progressPercent?.let {
                        ReaderMetaPill(text = stringResource(R.string.format_percent, it))
                    }
                }
                etaLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderMetaPill(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.64f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
