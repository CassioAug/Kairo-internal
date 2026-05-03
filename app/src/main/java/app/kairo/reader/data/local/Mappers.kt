package app.kairo.reader.data.local

import app.kairo.reader.core.model.Book
import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.Bookmark
import app.kairo.reader.core.model.BookmarkItem
import app.kairo.reader.core.model.Chapter

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

fun Book.toEntity(): BookEntity =
    BookEntity(
        id = id.value,
        title = title,
        authors = authors,
        languageTag = languageTag,
        coverImage = coverImage,
        isCompleted = isCompleted,
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

fun BookEntity.toDomain(chapters: List<ChapterEntity>): Book =
    Book(
        id = BookId(id),
        title = title,
        authors = authors,
        languageTag = languageTag,
        coverImage = coverImage,
        isCompleted = isCompleted,
        chapters = chapters.sortedBy { it.index }.map { it.toDomain() },
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

fun ReadingPositionEntity.toDomain(): app.kairo.reader.core.model.ReadingPosition =
    app.kairo.reader.core.model.ReadingPosition(
        bookId = BookId(bookId),
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
        wordIndex = wordIndex,
        rsvpResumeCursor = rsvpResumeCursor,
    )

fun app.kairo.reader.core.model.ReadingPosition.toEntity(): ReadingPositionEntity =
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
