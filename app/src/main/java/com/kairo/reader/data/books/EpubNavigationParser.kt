package com.kairo.reader.data.books

import com.kairo.reader.data.books.epub.EpubHtmlEntities
import com.kairo.reader.data.books.epub.EpubNavigationReference
import java.util.Locale

internal class EpubNavigationParser(
    private val markupParser: EpubMarkupParser = EpubMarkupParser(),
) {
    fun parse(
        document: String,
        isNcx: Boolean,
    ): List<EpubNavigationReference> {
        if (document.isBlank()) return emptyList()
        val parsed = markupParser.parse(document)
        return if (isNcx) {
            parseNcx(parsed)
        } else {
            parseNavigationDocument(parsed)
        }
    }

    private fun parseNavigationDocument(document: EpubMarkupDocument): List<EpubNavigationReference> {
        val navigationElements = findDescendants(document.children, NAV_TAG)
        val toc =
            navigationElements.firstOrNull(::isTocNavigation)
                ?: navigationElements.firstOrNull()
                ?: return emptyList()
        val list =
            directChildren(toc, ORDERED_LIST_TAG).firstOrNull()
                ?: findDescendants(toc.children, ORDERED_LIST_TAG).firstOrNull()
                ?: return emptyList()
        val entries = mutableListOf<EpubNavigationReference>()
        appendNavigationList(list, depth = 0, entries)
        return entries
    }

    private fun appendNavigationList(
        list: EpubMarkupElementNode,
        depth: Int,
        entries: MutableList<EpubNavigationReference>,
    ) {
        if (depth > MAX_NAVIGATION_DEPTH || entries.size >= MAX_NAVIGATION_ENTRIES) return
        directChildren(list, LIST_ITEM_TAG).forEach { item ->
            if (entries.size >= MAX_NAVIGATION_ENTRIES) return
            val labelNode =
                item.children
                    .filterIsInstance<EpubMarkupElementNode>()
                    .firstOrNull { child ->
                        child.localName() == ANCHOR_TAG || child.localName() == SPAN_TAG
                    }
            val label = labelNode?.let(::navigationLabel)
            if (!label.isNullOrBlank()) {
                entries +=
                    EpubNavigationReference(
                        label = label,
                        depth = depth,
                        href = labelNode.attributes["href"]?.trim()?.takeIf(String::isNotBlank),
                    )
            }
            directChildren(item, ORDERED_LIST_TAG).forEach { nested ->
                appendNavigationList(
                    list = nested,
                    depth = if (label.isNullOrBlank()) depth else depth + 1,
                    entries = entries,
                )
            }
        }
    }

    private fun parseNcx(document: EpubMarkupDocument): List<EpubNavigationReference> {
        val navMap = findDescendants(document.children, NAV_MAP_TAG).firstOrNull() ?: return emptyList()
        val entries = mutableListOf<EpubNavigationReference>()
        appendNcxPoints(navMap, depth = 0, entries)
        return entries
    }

    private fun appendNcxPoints(
        parent: EpubMarkupElementNode,
        depth: Int,
        entries: MutableList<EpubNavigationReference>,
    ) {
        if (depth > MAX_NAVIGATION_DEPTH || entries.size >= MAX_NAVIGATION_ENTRIES) return
        directChildren(parent, NAV_POINT_TAG).forEach { point ->
            if (entries.size >= MAX_NAVIGATION_ENTRIES) return
            val navLabel = directChildren(point, NAV_LABEL_TAG).firstOrNull()
            val label =
                navLabel
                    ?.let { findDescendants(it.children, TEXT_TAG).firstOrNull() }
                    ?.let(::navigationLabel)
            val href =
                directChildren(point, CONTENT_TAG)
                    .firstOrNull()
                    ?.attributes
                    ?.get("src")
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            if (!label.isNullOrBlank()) {
                entries += EpubNavigationReference(label = label, depth = depth, href = href)
            }
            appendNcxPoints(
                parent = point,
                depth = if (label.isNullOrBlank()) depth else depth + 1,
                entries = entries,
            )
        }
    }

    private fun isTocNavigation(node: EpubMarkupElementNode): Boolean {
        val semanticTokens =
            sequenceOf(
                node.attributes["epub:type"],
                node.attributes["type"],
                node.attributes["role"],
            ).filterNotNull()
                .flatMap { value -> value.split(Regex("\\s+")).asSequence() }
                .map { token -> token.lowercase(Locale.ROOT) }
                .toSet()
        return TOC_SEMANTIC_TOKENS.any(semanticTokens::contains)
    }

    private fun navigationLabel(node: EpubMarkupElementNode): String =
        EpubHtmlEntities
            .decode(extractText(node))
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .take(MAX_NAVIGATION_LABEL_LENGTH)

    private fun extractText(node: EpubMarkupElementNode): String {
        val out = StringBuilder()

        fun visit(current: EpubMarkupNode) {
            when (current) {
                is EpubMarkupTextNode -> out.append(current.text)
                is EpubMarkupElementNode -> {
                    if (current.localName() == ORDERED_LIST_TAG) return
                    current.children.forEach(::visit)
                }
            }
        }

        visit(node)
        return out.toString()
    }

    private fun directChildren(
        parent: EpubMarkupElementNode,
        localName: String,
    ): List<EpubMarkupElementNode> =
        parent.children
            .filterIsInstance<EpubMarkupElementNode>()
            .filter { it.localName() == localName }

    private fun findDescendants(
        nodes: List<EpubMarkupNode>,
        localName: String,
    ): List<EpubMarkupElementNode> {
        val matches = mutableListOf<EpubMarkupElementNode>()

        fun visit(node: EpubMarkupNode) {
            if (node !is EpubMarkupElementNode) return
            if (node.localName() == localName) matches += node
            node.children.forEach(::visit)
        }

        nodes.forEach(::visit)
        return matches
    }

    private fun EpubMarkupElementNode.localName(): String = name.substringAfterLast(':')

    private companion object {
        const val MAX_NAVIGATION_DEPTH = 12
        const val MAX_NAVIGATION_ENTRIES = 4_000
        const val MAX_NAVIGATION_LABEL_LENGTH = 240
        const val NAV_TAG = "nav"
        const val ORDERED_LIST_TAG = "ol"
        const val LIST_ITEM_TAG = "li"
        const val ANCHOR_TAG = "a"
        const val SPAN_TAG = "span"
        const val NAV_MAP_TAG = "navmap"
        const val NAV_POINT_TAG = "navpoint"
        const val NAV_LABEL_TAG = "navlabel"
        const val TEXT_TAG = "text"
        const val CONTENT_TAG = "content"
        val TOC_SEMANTIC_TOKENS = setOf("toc", "doc-toc")
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
