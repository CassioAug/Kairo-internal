package com.kairo.reader.data.books

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.Charset
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

internal object SafeXml {
    fun parse(bytes: ByteArray): Document {
        rejectForbiddenDeclarations(bytes)
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                disableXInclude { enabled -> isXIncludeAware = enabled }
                isExpandEntityReferences = false
                OPTIONAL_SECURITY_FEATURES.forEach { feature ->
                    setOptionalSecurityFeature { setFeature(feature.name, feature.enabled) }
                }
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

    internal fun setOptionalSecurityFeature(setFeature: () -> Unit) {
        val failure = runCatching(setFeature).exceptionOrNull()
        if (failure != null && failure !is ParserConfigurationException) throw failure
    }

    private fun rejectForbiddenDeclarations(bytes: ByteArray) {
        val xml = String(bytes, bytes.detectXmlScanCharset())
        require(!FORBIDDEN_XML_DECLARATION.containsMatchIn(xml)) {
            "XML DOCTYPE and entity declarations are not supported"
        }
    }

    private fun ByteArray.detectXmlScanCharset(): Charset =
        when {
            startsWith(UTF_32_BE_BOM) -> UTF_32_BE
            startsWith(UTF_32_LE_BOM) -> UTF_32_LE
            startsWith(UTF_16_BE_BOM) -> Charsets.UTF_16BE
            startsWith(UTF_16_LE_BOM) -> Charsets.UTF_16LE
            startsWith(UTF_32_BE_PREFIX) -> UTF_32_BE
            startsWith(UTF_32_LE_PREFIX) -> UTF_32_LE
            startsWith(UTF_16_BE_PREFIX) -> Charsets.UTF_16BE
            startsWith(UTF_16_LE_PREFIX) -> Charsets.UTF_16LE
            else -> Charsets.UTF_8
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

    private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"

    private data class XmlSecurityFeature(val name: String, val enabled: Boolean,)

    private val OPTIONAL_SECURITY_FEATURES =
        listOf(
            XmlSecurityFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true),
            XmlSecurityFeature("http://apache.org/xml/features/disallow-doctype-decl", true),
            XmlSecurityFeature("http://xml.org/sax/features/external-general-entities", false),
            XmlSecurityFeature("http://xml.org/sax/features/external-parameter-entities", false),
            XmlSecurityFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false),
        )
    private val FORBIDDEN_XML_DECLARATION = Regex("<!\\s*(?:DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE)
    private val UTF_32_BE = Charset.forName("UTF-32BE")
    private val UTF_32_LE = Charset.forName("UTF-32LE")

    // XML encoding signatures are fixed protocol representations; their raw bytes are clearest here.
    @Suppress("MagicNumber")
    private val UTF_32_BE_BOM = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())

    @Suppress("MagicNumber")
    private val UTF_32_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)

    @Suppress("MagicNumber")
    private val UTF_16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    @Suppress("MagicNumber")
    private val UTF_16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())

    @Suppress("MagicNumber")
    private val UTF_32_BE_PREFIX = byteArrayOf(0x00, 0x00, 0x00, 0x3C)

    @Suppress("MagicNumber")
    private val UTF_32_LE_PREFIX = byteArrayOf(0x3C, 0x00, 0x00, 0x00)

    @Suppress("MagicNumber")
    private val UTF_16_BE_PREFIX = byteArrayOf(0x00, 0x3C)

    @Suppress("MagicNumber")
    private val UTF_16_LE_PREFIX = byteArrayOf(0x3C, 0x00)

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
