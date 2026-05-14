package com.kairo.reader.data.books

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.kairo.reader.core.language.BookLanguageResolver
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import com.kairo.reader.data.local.BookDao
import com.kairo.reader.data.local.toDomain
import com.kairo.reader.data.local.toEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val parsers: List<BookParser>,
    private val appContext: android.content.Context,
) : BookRepository {
    // Mutex to prevent concurrent import operations which can crash the app
    private val importMutex = Mutex()

    override suspend fun importBook(uri: Uri): BookImportResult =
        importMutex.withLock {
            val extension = resolveExtension(uri)
            val parser =
                parsers.firstOrNull { it.supports(extension) }
                    ?: throw IllegalArgumentException("No parser found for .$extension files")

            val sourceFingerprint = resolveSourceFingerprint(uri, extension)
            sourceFingerprint
                ?.let { fingerprint -> bookDao.getBookByImportFingerprint(fingerprint) }
                ?.let { existing ->
                    return@withLock BookImportResult(
                        book = existing.toDomain(bookDao.getChapters(existing.id)),
                        alreadyImported = true,
                    )
                }

            // Parse the book - let errors propagate for proper error handling
            val bookId =
                sourceFingerprint?.let(ImportFingerprint::bookIdForFingerprint)
                    ?: BookId(UUID.randomUUID().toString())
            val parsedBook = parser.parse(appContext, uri, bookId)
            val resolvedLanguageTag = BookLanguageResolver.resolve(parsedBook)
            val book =
                parsedBook.copy(
                    languageTag = resolvedLanguageTag,
                    coverImage = optimizeCoverForDb(parsedBook.coverImage),
                    chapters =
                    parsedBook.chapters.map { chapter ->
                        if (chapter.wordCount > 0) {
                            chapter
                        } else if (chapter.plainText.length <= MAX_WORD_COUNT_CHARS) {
                            chapter.copy(wordCount = countWords(chapter.plainText))
                        } else {
                            // Defer heavy word counts for very large chapters.
                            chapter
                        }
                    },
                )
            findExistingDuplicate(
                parsedBook = book,
                sourceFingerprint = sourceFingerprint,
            )?.let { existing ->
                deleteBookAssets(book.id.value)
                return@withLock BookImportResult(
                    book = existing,
                    alreadyImported = true,
                )
            }

            // Save to database
            bookDao.insertBook(
                book.toEntity(importFingerprint = sourceFingerprint),
                book.chapters.map { it.toEntity(book.id) },
            )
            return@withLock BookImportResult(
                book = book,
                alreadyImported = false,
            )
        }

    private fun resolveSourceFingerprint(
        uri: Uri,
        extension: String,
    ): String? =
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ImportFingerprint.sourceFingerprint(extension, input)
            }
        }.getOrNull()

    private suspend fun findExistingDuplicate(
        parsedBook: Book,
        sourceFingerprint: String?,
    ): Book? {
        val candidates = bookDao.getBooksByTitleForImportDedupe(parsedBook.title)
        if (candidates.isEmpty()) return null

        val parsedFingerprint = ImportFingerprint.contentFingerprint(parsedBook)
        candidates.forEach { candidate ->
            if (candidate.id == parsedBook.id.value) return@forEach
            if (candidate.authors != parsedBook.authors) return@forEach

            val candidateChapters = bookDao.getChaptersWithContent(candidate.id)
            if (candidateChapters.size != parsedBook.chapters.size) return@forEach

            val candidateBook = candidate.toDomain(candidateChapters)
            if (ImportFingerprint.contentFingerprint(candidateBook) == parsedFingerprint) {
                sourceFingerprint?.let { fingerprint ->
                    runCatching {
                        bookDao.setImportFingerprintIfEmpty(candidate.id, fingerprint)
                    }
                }
                return candidateBook
            }
        }
        return null
    }

    private fun deleteBookAssets(bookId: String) {
        runCatching {
            File(appContext.filesDir, "kairo_epub_assets/$bookId").deleteRecursively()
        }
        runCatching {
            File(appContext.filesDir, "kairo_mobi_assets/$bookId").deleteRecursively()
        }
    }

    private fun resolveExtension(uri: Uri): String {
        // Try to get extension from the display name (most reliable for file pickers)
        val displayName =
            runCatching {
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                }
            }.getOrNull()

        val extFromDisplay =
            displayName
                ?.substringAfterLast('.', "")
                ?.lowercase()
                .orEmpty()

        // Check the MIME type
        val mime =
            appContext.contentResolver
                .getType(uri)
                ?.lowercase()
                .orEmpty()
        val extFromMime =
            when {
                mime.contains("epub") || mime == "application/epub+zip" -> "epub"
                mime.contains("mobi") || mime.contains("x-mobipocket") -> "mobi"
                else -> ""
            }

        // Try path segment as fallback
        val pathExt =
            uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase()
                .orEmpty()

        return when {
            extFromDisplay.isNotEmpty() -> extFromDisplay
            extFromMime.isNotEmpty() -> extFromMime
            pathExt.isNotEmpty() -> pathExt
            else -> DEFAULT_EXTENSION
        }
    }

    override suspend fun getBook(bookId: BookId): Book {
        val bookEntity = requireNotNull(bookDao.getBook(bookId.value)) { "Book not found" }
        val chapters = bookDao.getChapters(bookId.value)
        return bookEntity.toDomain(chapters)
    }

    override suspend fun getChapter(
        bookId: BookId,
        chapterIndex: Int,
    ): Chapter {
        val entity =
            requireNotNull(bookDao.getChapter(bookId.value, chapterIndex)) { "Chapter missing" }
        return entity.toDomain()
    }

    override suspend fun updateChapterWordCount(
        bookId: BookId,
        chapterIndex: Int,
        wordCount: Int,
    ) {
        if (wordCount <= 0) return
        bookDao.updateChapterWordCount(bookId.value, chapterIndex, wordCount)
    }

    override suspend fun getBookLanguageTag(bookId: BookId): String? =
        bookDao.getBookLanguageTag(bookId.value)

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.getBooks().map { entities ->
            entities.map { bookEntity ->
                val chapters = bookDao.getChapters(bookEntity.id)
                bookEntity.toDomain(chapters)
            }
        }

    private fun optimizeCoverForDb(coverImage: ByteArray?): ByteArray? {
        if (coverImage == null || coverImage.isEmpty()) return coverImage
        if (coverImage.size <= MAX_COVER_DB_BYTES) return coverImage

        val safeFallback =
            coverImage.takeIf { it.size <= MAX_COVER_DB_BYTES }

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size, bounds)

            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching safeFallback

            // CursorWindow on many devices is ~2MB; keep cover comfortably under that, and also
            // cap pixel dimensions so first-time decode/render is fast.
            val shouldOptimize =
                coverImage.size > MAX_COVER_DB_BYTES ||
                    width > COVER_MAX_DIM_PX ||
                    height > COVER_MAX_DIM_PX
            if (!shouldOptimize) return@runCatching coverImage

            val sampleSize = calculateInSampleSize(width, height, COVER_MAX_DIM_PX)
            val decode =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            val bitmap =
                BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size, decode)
                    ?: return@runCatching safeFallback

            try {
                val out = ByteArrayOutputStream()
                var quality = INITIAL_COVER_JPEG_QUALITY
                var encoded: ByteArray
                do {
                    out.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    encoded = out.toByteArray()
                    quality -= JPEG_QUALITY_STEP
                } while (encoded.size > MAX_COVER_DB_BYTES && quality >= MIN_COVER_JPEG_QUALITY)
                encoded
            } finally {
                bitmap.recycle()
            }
        }.getOrNull() ?: safeFallback
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimPx: Int,
    ): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w > maxDimPx || h > maxDimPx) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private companion object {
        private const val DEFAULT_EXTENSION = "epub"
        private const val MAX_COVER_DB_BYTES = 256 * 1024
        private const val COVER_MAX_DIM_PX = 1080
        private const val INITIAL_COVER_JPEG_QUALITY = 90
        private const val JPEG_QUALITY_STEP = 10
        private const val MIN_COVER_JPEG_QUALITY = 60
        private const val MAX_WORD_COUNT_CHARS = 120_000
    }
}
