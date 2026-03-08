package com.example.kairo.data.seed

import com.example.kairo.data.local.BookDao
import com.example.kairo.data.local.toEntity
import com.example.kairo.sample.SampleBooks

class SampleSeeder(private val bookDao: BookDao,) {
    suspend fun seedIfEmpty() {
        val existing = bookDao.peekBook()
        if (existing != null) return
        // Seed the starter book directly so first-run onboarding has real content to use.
        val sample = SampleBooks.defaultSample()
        bookDao.insertBook(sample.toEntity(), sample.chapters.map { it.toEntity(sample.id) })
    }
}
