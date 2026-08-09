package com.kairo.reader.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration12To13Test {
    @Test
    fun migrationRemovesOrphansAndAddsCascadeForeignKeysAndCheckpointSchema() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-12-13-${UUID.randomUUID()}.db"
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(databaseName)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(12) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                createVersion12Tables(db)
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        }
                    ).build()
            )
        try {
            val database = helper.writableDatabase
            seedVersion12Rows(database)

            MIGRATION_12_13.migrate(database)

            assertEquals(1, database.rowCount("saved_annotations"))
            assertEquals(1, database.rowCount("reading_sessions"))
            assertCascadeBookForeignKey(database, "saved_annotations")
            assertCascadeBookForeignKey(database, "reading_sessions")
            assertCascadeBookForeignKey(database, "reading_session_checkpoints")
            assertTrue(database.columns("reading_session_checkpoints").contains("startedAt"))
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersion12Tables(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL(
            """
            CREATE TABLE saved_annotations (
                id TEXT NOT NULL PRIMARY KEY,
                bookId TEXT NOT NULL,
                chapterIndex INTEGER NOT NULL,
                startTokenIndex INTEGER NOT NULL,
                endTokenIndex INTEGER NOT NULL,
                selectedText TEXT NOT NULL,
                note TEXT NOT NULL,
                color TEXT NOT NULL,
                kind TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE reading_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                bookId TEXT NOT NULL,
                mode TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER NOT NULL,
                activeDurationMs INTEGER NOT NULL,
                startChapterIndex INTEGER NOT NULL,
                startTokenIndex INTEGER NOT NULL,
                endChapterIndex INTEGER NOT NULL,
                endTokenIndex INTEGER NOT NULL,
                wordsRead INTEGER NOT NULL,
                effectiveWpm INTEGER NOT NULL,
                isWordCountEstimated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun seedVersion12Rows(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO books(id) VALUES ('book')")
        listOf("book", "missing").forEach { bookId ->
            db.execSQL(
                """
                INSERT INTO saved_annotations
                VALUES ('annotation-$bookId', '$bookId', 0, 0, 1, 'passage', '',
                        'YELLOW', 'HIGHLIGHT', 1, 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO reading_sessions
                VALUES ('session-$bookId', '$bookId', 'READER', 1, 300001, 300000,
                        0, 0, 0, 20, 20, 4, 1)
                """.trimIndent()
            )
        }
    }

    private fun assertCascadeBookForeignKey(
        db: SupportSQLiteDatabase,
        table: String,
    ) {
        val rows =
            db.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Triple(
                                cursor.getString(cursor.getColumnIndexOrThrow("table")),
                                cursor.getString(cursor.getColumnIndexOrThrow("from")),
                                cursor.getString(cursor.getColumnIndexOrThrow("on_delete")),
                            )
                        )
                    }
                }
            }
        assertTrue(rows.contains(Triple("books", "bookId", "CASCADE")))
    }

    private fun SupportSQLiteDatabase.rowCount(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.columns(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }
}
