package app.kairo.reader.data.seed

import app.kairo.reader.data.local.BookDao
import app.kairo.reader.data.local.toEntity
import app.kairo.reader.sample.SampleBooks

class SampleSeeder(private val bookDao: BookDao,) {
    suspend fun seedIfEmpty() {
        val existing = bookDao.peekBook()
        if (existing != null) return
        // Seed the starter book directly so first-run onboarding has real content to use.
        val sample = SampleBooks.defaultSample()
        bookDao.insertBook(sample.toEntity(), sample.chapters.map { it.toEntity(sample.id) })
    }
}
