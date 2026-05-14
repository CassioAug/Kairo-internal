package com.kairo.reader.data.books

import java.net.IDN
import java.net.URI
import java.util.Locale

internal object WebArticleUrl {
    fun normalize(rawUrl: String): String {
        val candidate =
            rawUrl
                .trim()
                .let { extractFirstWebUrl(it) ?: it }
                .removeSurrounding("<", ">")
                .trimTrailingUrlPunctuation()
                .let { value ->
                    if (value.contains("://")) value else "https://$value"
                }

        val uri =
            runCatching { URI(candidate) }
                .getOrElse { throw IllegalArgumentException("Enter a valid web link.") }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") {
            "Only http and https links are supported."
        }
        val host =
            uri.host
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Enter a valid web link.")
        val normalizedHost = IDN.toASCII(host).lowercase(Locale.ROOT)
        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "$scheme://$normalizedHost$port$path$query"
    }

    fun extractFirstWebUrl(text: String): String? =
        WEB_URL_REGEX
            .find(text)
            ?.value
            ?.trimTrailingUrlPunctuation()

    fun displayHost(normalizedUrl: String): String =
        runCatching { URI(normalizedUrl).host }
            .getOrNull()
            ?.removePrefix("www.")
            .orEmpty()
            .ifBlank { "Web article" }

    private fun String.trimTrailingUrlPunctuation(): String =
        trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

    private val WEB_URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
}
