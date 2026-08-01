package com.kairo.reader.data.local

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.BookmarkItem
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.TableOfContentsEntry
import com.kairo.reader.core.model.TableOfContentsTarget

private const val IMAGE_PATHS_DELIMITER = "|||"

private fun encodeImagePaths(paths: List<String>): String =
    paths
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(IMAGE_PATHS_DELIMITER)

private fun decodeImagePaths(raw: String): List<String> =
    if (raw.isBlank()) emptyList() else raw.split(IMAGE_PATHS_DELIMITER).filter { it.isNotBlank() }

fun Book.toEntity(importFingerprint: String? = null): BookEntity =
    BookEntity(
        id = id.value,
        title = title,
        authors = authors,
        languageTag = languageTag,
        coverImage = coverImage,
        isCompleted = isCompleted,
        importFingerprint = importFingerprint,
    )

fun Chapter.toEntity(bookId: BookId): ChapterEntity =
    ChapterEntity(
        bookId = bookId.value,
        index = index,
        title = title,
        htmlContent = htmlContent,
        plainText = plainText,
        imagePaths = encodeImagePaths(imagePaths),
        wordCount = wordCount,
    )

fun BookEntity.toDomain(
    chapters: List<ChapterEntity>,
    tableOfContentsEntries: List<TableOfContentsEntryEntity> = emptyList(),
): Book =
    Book(
        id = BookId(id),
        title = title,
        authors = authors,
        languageTag = languageTag,
        coverImage = coverImage,
        isCompleted = isCompleted,
        chapters = chapters.sortedBy { it.index }.map { it.toDomain() },
        tableOfContents =
            tableOfContentsEntries
                .sortedBy { it.entryIndex }
                .map { it.toDomain() },
    )

fun TableOfContentsEntry.toEntity(
    bookId: BookId,
    entryIndex: Int,
): TableOfContentsEntryEntity =
    TableOfContentsEntryEntity(
        bookId = bookId.value,
        entryIndex = entryIndex,
        label = label,
        depth = depth,
        chapterIndex = target?.chapterIndex,
        characterOffset = target?.characterOffset,
    )

fun TableOfContentsEntryEntity.toDomain(): TableOfContentsEntry =
    TableOfContentsEntry(
        label = label,
        depth = depth,
        target =
            chapterIndex?.let { resolvedChapterIndex ->
                TableOfContentsTarget(
                    chapterIndex = resolvedChapterIndex,
                    characterOffset = characterOffset ?: 0,
                )
            },
    )

fun ChapterEntity.toDomain(): Chapter =
    Chapter(
        index = index,
        title = title,
        htmlContent = htmlContent,
        plainText = plainText,
        imagePaths = decodeImagePaths(imagePaths),
        wordCount = wordCount,
    )

fun ReadingPositionEntity.toDomain(): com.kairo.reader.core.model.ReadingPosition =
    com.kairo.reader.core.model.ReadingPosition(
        bookId = BookId(bookId),
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
        wordIndex = wordIndex,
        rsvpResumeCursor = rsvpResumeCursor,
    )

fun com.kairo.reader.core.model.ReadingPosition.toEntity(): ReadingPositionEntity =
    ReadingPositionEntity(
        bookId = bookId.value,
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
        wordIndex = wordIndex,
        rsvpResumeCursor = rsvpResumeCursor,
    )

fun BookmarkEntity.toDomain(): Bookmark =
    Bookmark(
        id = id,
        bookId = BookId(bookId),
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
        previewText = previewText,
        createdAt = createdAt,
    )

fun Bookmark.toEntity(): BookmarkEntity =
    BookmarkEntity(
        id = id,
        bookId = bookId.value,
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
        previewText = previewText,
        createdAt = createdAt,
    )

fun BookmarkWithBookEntity.toDomain(): BookmarkItem =
    BookmarkItem(
        bookmark = bookmark.toDomain(),
        book = book.toDomain(chapters = emptyList()),
        chapterCount = chapterCount,
    )
