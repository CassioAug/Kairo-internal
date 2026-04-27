package com.example.kairo

import android.app.Application
import androidx.room.Room
import com.example.kairo.core.dispatchers.DefaultDispatcherProvider
import com.example.kairo.core.rsvp.ComprehensionRsvpEngine
import com.example.kairo.core.rsvp.RsvpEngine
import com.example.kairo.data.bookmarks.BookmarkRepository
import com.example.kairo.data.bookmarks.BookmarkRepositoryImpl
import com.example.kairo.data.books.BookRepository
import com.example.kairo.data.books.BookRepositoryImpl
import com.example.kairo.data.books.EpubBookParser
import com.example.kairo.data.books.MobiBookParser
import com.example.kairo.data.library.LibraryRepository
import com.example.kairo.data.library.LibraryRepositoryImpl
import com.example.kairo.data.local.KairoDatabase
import com.example.kairo.data.local.MIGRATION_1_2
import com.example.kairo.data.local.MIGRATION_2_3
import com.example.kairo.data.local.MIGRATION_3_4
import com.example.kairo.data.local.MIGRATION_4_5
import com.example.kairo.data.local.MIGRATION_5_6
import com.example.kairo.data.local.MIGRATION_6_7
import com.example.kairo.data.local.MIGRATION_7_8
import com.example.kairo.data.local.MIGRATION_8_9
import com.example.kairo.data.preferences.PreferencesRepository
import com.example.kairo.data.preferences.PreferencesRepositoryImpl
import com.example.kairo.data.reading.ReadingPositionRepository
import com.example.kairo.data.reading.ReadingPositionRepositoryImpl
import com.example.kairo.data.rsvp.RsvpFrameRepository
import com.example.kairo.data.rsvp.RsvpFrameRepositoryImpl
import com.example.kairo.data.seed.SampleSeeder
import com.example.kairo.data.token.TokenRepository
import com.example.kairo.data.token.TokenRepositoryImpl
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
