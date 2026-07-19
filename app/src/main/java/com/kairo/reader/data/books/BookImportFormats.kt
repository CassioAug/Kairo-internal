package com.kairo.reader.data.books

import java.util.Locale

internal object BookImportFormats {
    val pickerMimeTypes =
        listOf(
            "application/epub+zip",
            "application/x-mobipocket-ebook",
            "application/x-palm-database",
            "application/vnd.amazon.ebook",
            "text/plain",
            "text/markdown",
            "text/html",
            "application/x-fictionbook+xml",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/pdf",
            "application/octet-stream",
            "*/*",
        )

    val assetRootNames =
        setOf(
            "kairo_epub_assets",
            "kairo_mobi_assets",
            "kairo_fb2_assets",
            "kairo_docx_assets",
        )

    fun extensionsForDisplayName(displayName: String?): List<String> {
        val normalized = displayName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalized.isBlank()) return emptyList()
        val simple = normalized.substringAfterLast('.', "").takeIf(String::isNotBlank)
        val compound = COMPOUND_EXTENSIONS.firstOrNull { extension -> normalized.endsWith(".$extension") }
        return listOfNotNull(compound, simple).distinct()
    }

    fun extensionForMimeType(mimeType: String?): String? {
        val mime = mimeType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            mime == "application/epub+zip" || mime.contains("epub") -> "epub"
            mime == "application/x-mobipocket-ebook" || mime.contains("mobipocket") -> "mobi"
            mime == "application/x-palm-database" -> "prc"
            mime == "application/vnd.amazon.ebook" -> "azw"
            mime == "text/plain" -> "txt"
            mime == "text/markdown" || mime == "text/x-markdown" -> "md"
            mime == "text/html" || mime == "application/xhtml+xml" -> "html"
            mime == "application/x-fictionbook+xml" || mime == "application/fb2" -> "fb2"
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            mime == "application/pdf" -> "pdf"
            else -> null
        }
    }

    private val COMPOUND_EXTENSIONS = setOf("fb2.zip")
}
