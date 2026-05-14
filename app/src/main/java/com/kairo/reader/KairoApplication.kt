package com.kairo.reader

import android.app.Application
import androidx.room.Room
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.rsvp.ComprehensionRsvpEngine
import com.kairo.reader.core.rsvp.RsvpEngine
import com.kairo.reader.data.bookmarks.BookmarkRepository
import com.kairo.reader.data.bookmarks.BookmarkRepositoryImpl
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.books.BookRepositoryImpl
import com.kairo.reader.data.books.EpubBookParser
import com.kairo.reader.data.books.MobiBookParser
import com.kairo.reader.data.library.LibraryRepository
import com.kairo.reader.data.library.LibraryRepositoryImpl
import com.kairo.reader.data.local.KairoDatabase
import com.kairo.reader.data.local.MIGRATION_1_2
import com.kairo.reader.data.local.MIGRATION_2_3
import com.kairo.reader.data.local.MIGRATION_3_4
import com.kairo.reader.data.local.MIGRATION_4_5
import com.kairo.reader.data.local.MIGRATION_5_6
import com.kairo.reader.data.local.MIGRATION_6_7
import com.kairo.reader.data.local.MIGRATION_7_8
import com.kairo.reader.data.local.MIGRATION_8_9
import com.kairo.reader.data.local.MIGRATION_9_10
import com.kairo.reader.data.preferences.PreferencesRepository
import com.kairo.reader.data.preferences.PreferencesRepositoryImpl
import com.kairo.reader.data.reading.ReadingPositionRepository
import com.kairo.reader.data.reading.ReadingPositionRepositoryImpl
import com.kairo.reader.data.rsvp.RsvpFrameRepository
import com.kairo.reader.data.rsvp.RsvpFrameRepositoryImpl
import com.kairo.reader.data.seed.SampleSeeder
import com.kairo.reader.data.token.TokenRepository
import com.kairo.reader.data.token.TokenRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KairoApplication : Application() {
    val dispatcherProvider = DefaultDispatcherProvider()
    private val applicationScope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    private lateinit var database: KairoDatabase
    lateinit var bookRepository: BookRepository
        private set
    lateinit var tokenRepository: TokenRepository
        private set
    lateinit var readingPositionRepository: ReadingPositionRepository
        private set
    lateinit var bookmarkRepository: BookmarkRepository
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set
    lateinit var libraryRepository: LibraryRepository
        private set
    val rsvpEngine: RsvpEngine = ComprehensionRsvpEngine()
    lateinit var rsvpFrameRepository: RsvpFrameRepository
        private set
    lateinit var sampleSeeder: SampleSeeder
        private set

    override fun onCreate() {
        super.onCreate()
        database =
            Room
                .databaseBuilder(
                    applicationContext,
                    KairoDatabase::class.java,
                    "kairo.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                )
                .build()

        val parsers =
            listOf(
                EpubBookParser(dispatcherProvider),
                MobiBookParser(dispatcherProvider),
            )

        bookRepository = BookRepositoryImpl(database.bookDao(), parsers, applicationContext)
        tokenRepository = TokenRepositoryImpl(bookRepository, dispatcherProvider)
        readingPositionRepository = ReadingPositionRepositoryImpl(database.readingPositionDao())
        bookmarkRepository = BookmarkRepositoryImpl(database.bookmarkDao())
        preferencesRepository = PreferencesRepositoryImpl(this)
        libraryRepository =
            LibraryRepositoryImpl(
                bookRepository,
                database.bookDao(),
                database.readingPositionDao(),
                database.bookmarkDao(),
                applicationContext,
                dispatcherProvider,
            )
        rsvpFrameRepository = RsvpFrameRepositoryImpl(tokenRepository, rsvpEngine, dispatcherProvider)
        sampleSeeder = SampleSeeder(database.bookDao())

        applicationScope.launch { sampleSeeder.seedIfEmpty() }
    }
}
