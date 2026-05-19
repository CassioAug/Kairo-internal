package com.kairo.reader.data.books

import java.net.IDN
import java.net.URI
import java.util.Locale

internal object WebArticleUrl {
    fun normalize(rawUrl: String): String {
        val candidate =
            rawUrl
                .trim()
                .let { extractBestWebUrl(it) ?: it }
                .removeSurrounding("<", ">")
                .trimTrailingUrlPunctuation()
                .let { value ->
                    if (value.contains("://")) value else "https://$value"
                }

        val uri =
            runCatching { URI(candidate) }
                .getOrElse { throw IllegalArgumentException("Enter a valid web link.") }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "https") {
            "Only https links are supported."
        }
        val host =
            uri.host
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Enter a valid web link.")
        val normalizedHost = IDN.toASCII(host).lowercase(Locale.ROOT)
        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val query = uri.rawQuery?.withoutTrackingParameters()?.let { "?$it" }.orEmpty()
        return "$scheme://$normalizedHost$port$path$query"
    }

    fun extractBestWebUrl(text: String): String? =
        WEB_URL_REGEX
            .findAll(text)
            .map { match -> match.value.trimTrailingUrlPunctuation() }
            .mapNotNull { url ->
                val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
                val scheme = uri.scheme?.lowercase(Locale.ROOT)
                if (scheme == "https") {
                    ScoredUrl(url = url, score = uri.shareScore())
                } else {
                    null
                }
            }
            .maxByOrNull { it.score }
            ?.url

    fun extractFirstWebUrl(text: String): String? = extractBestWebUrl(text)

    fun displayHost(normalizedUrl: String): String =
        runCatching { URI(normalizedUrl).host }
            .getOrNull()
            ?.removePrefix("www.")
            .orEmpty()
            .ifBlank { "Web article" }

    private fun String.trimTrailingUrlPunctuation(): String =
        trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

    private fun String.withoutTrackingParameters(): String? {
        val kept =
            split("&")
                .filter { parameter ->
                    val name = parameter.substringBefore("=").lowercase(Locale.ROOT)
                    name.isNotBlank() && !name.isTrackingParameter()
                }
        return kept.joinToString("&").takeIf { it.isNotBlank() }
    }

    private fun String.isTrackingParameter(): Boolean =
        startsWith("utm_") || this in TRACKING_PARAMETERS

    private fun URI.shareScore(): Int {
        val host = host?.lowercase(Locale.ROOT)?.removePrefix("www.").orEmpty()
        val path = rawPath.orEmpty().trim('/')
        val segments = path.split('/').filter { it.isNotBlank() }
        val query = rawQuery.orEmpty()

        var score = 0
        if (!host.isSocialHost()) score += 1_000
        if (segments.isNotEmpty()) score += 180
        score += (segments.size.coerceAtMost(MAX_SEGMENT_SCORE_COUNT) * 35)
        if (segments.any { it.any(Char::isDigit) }) score += 40
        if (query.isNotBlank()) score += 15
        if (host.isSocialHost()) score -= 900
        if (segments.size <= 1 && host.isSocialHost()) score -= 250
        return score
    }

    private fun String.isSocialHost(): Boolean =
        SOCIAL_HOSTS.any { socialHost -> this == socialHost || endsWith(".$socialHost") }

    private data class ScoredUrl(
        val url: String,
        val score: Int,
    )

    private val WEB_URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private const val MAX_SEGMENT_SCORE_COUNT = 6
    private val SOCIAL_HOSTS =
        setOf(
            "facebook.com",
            "fb.com",
            "instagram.com",
            "threads.net",
            "twitter.com",
            "x.com",
            "linkedin.com",
            "youtube.com",
            "youtu.be",
            "tiktok.com",
        )
    private val TRACKING_PARAMETERS =
        setOf(
            "fbclid",
            "gclid",
            "gbraid",
            "wbraid",
            "igshid",
            "mc_cid",
            "mc_eid",
            "mkt_tok",
            "ref",
            "ref_src",
            "spm",
            "at_campaign",
            "at_format",
            "at_link_id",
            "at_link_origin",
            "at_link_type",
            "at_medium",
            "at_ptr_name",
            "xtor",
            "_hsenc",
            "_hsmi",
        )
}
