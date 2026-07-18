package com.kairo.reader.data.books

import com.kairo.reader.data.books.epub.EpubPathResolver

internal object EpubCoverSelector {
    fun select(imagePathsLower: Collection<String>): String? {
        if (imagePathsLower.isEmpty()) return null
        return imagePathsLower.minWithOrNull(
            compareBy(
                ::priority,
                EpubPathResolver::pathDepth,
                { it.length },
                { it },
            ),
        )
    }

    private fun priority(pathLower: String): Int {
        val fileName = pathLower.substringAfterLast('/')
        return when {
            fileName.contains("cover") -> 0
            fileName.contains("front") -> 1
            fileName.contains("title") -> 2
            else -> 3
        }
    }
}
