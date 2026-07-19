package com.kairo.reader.data.books

import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

internal object SafeXml {
    fun parse(bytes: ByteArray): Document {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                disableXInclude { enabled -> isXIncludeAware = enabled }
                isExpandEntityReferences = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setOptionalSecurityAttribute(ACCESS_EXTERNAL_DTD, "")
                setOptionalSecurityAttribute(ACCESS_EXTERNAL_SCHEMA, "")
            }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        return ByteArrayInputStream(bytes).use(builder::parse)
    }

    internal fun disableXInclude(setEnabled: (Boolean) -> Unit) {
        // XInclude is disabled by default. Some Android XML providers throw an
        // "Unknown version 0.0" exception even when explicitly setting it to false.
        val failure = runCatching { setEnabled(false) }.exceptionOrNull()
        if (failure != null && failure !is UnsupportedOperationException) throw failure
    }

    private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"

    private fun DocumentBuilderFactory.setOptionalSecurityAttribute(
        name: String,
        value: String,
    ) {
        // Android's XML provider varies by API level. DOCTYPE and entity features above are
        // mandatory; these JAXP properties add defense in depth where the provider supports them.
        runCatching { setAttribute(name, value) }
    }
}

internal fun Element.localNameValue(): String =
    localName ?: nodeName.substringAfterLast(':', nodeName)

internal fun Element.directChildElements(): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = childNodes
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node is Element) result += node
    }
    return result
}

internal fun Element.directChildrenNamed(localName: String): List<Element> =
    directChildElements().filter { child -> child.localNameValue().equals(localName, ignoreCase = true) }

internal fun Element.firstDirectChildNamed(localName: String): Element? =
    directChildrenNamed(localName).firstOrNull()

internal fun Element.descendantsNamed(localName: String): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = getElementsByTagName("*")
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node is Element && node.localNameValue().equals(localName, ignoreCase = true)) {
            result += node
        }
    }
    return result
}

internal fun Element.attributeByLocalName(localName: String): String? {
    val attributes = attributes
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        val resolvedName = attribute.localName ?: attribute.nodeName.substringAfterLast(':', attribute.nodeName)
        if (resolvedName.equals(localName, ignoreCase = true)) {
            return attribute.nodeValue?.takeIf(String::isNotBlank)
        }
    }
    return null
}

internal fun Node.normalizedText(): String? =
    textContent
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
