package com.kairo.reader.data.books

internal class EpubMarkupParser(private val tokenizer: EpubMarkupTokenizer = EpubMarkupTokenizer(),) {
    companion object {
        private val VOID_ELEMENTS =
            setOf(
                "area",
                "base",
                "br",
                "col",
                "embed",
                "hr",
                "img",
                "input",
                "link",
                "meta",
                "param",
                "source",
                "track",
                "wbr",
            )
    }

    fun parse(input: String): EpubMarkupDocument {
        val document = EpubMarkupDocument()
        val stack = ArrayDeque<EpubMarkupElementNode>()

        fun appendNode(node: EpubMarkupNode) {
            if (stack.isEmpty()) {
                document.children.add(node)
            } else {
                stack.last().children.add(node)
            }
        }

        tokenizer.tokenize(input).forEach { token ->
            when (token) {
                is EpubTextToken -> {
                    if (token.text.isNotEmpty()) {
                        appendNode(EpubMarkupTextNode(token.text))
                    }
                }
                is EpubStartTagToken -> {
                    val element =
                        EpubMarkupElementNode(
                            name = token.name,
                            attributes = token.attributes,
                        )
                    appendNode(element)
                    if (!token.selfClosing && !VOID_ELEMENTS.contains(token.name)) {
                        stack.addLast(element)
                    }
                }
                is EpubEndTagToken -> {
                    popUntilMatchingTag(stack, token.name)
                }
            }
        }

        return document
    }

    private fun popUntilMatchingTag(
        stack: ArrayDeque<EpubMarkupElementNode>,
        targetTag: String,
    ) {
        // A stray end tag with no matching open element must be ignored; popping the whole
        // stack would re-parent the rest of the chapter to the document root (leaking <head>
        // content into plain text and losing paragraph structure).
        val matchIndex = stack.indexOfLast { it.name == targetTag }
        if (matchIndex == -1) return
        while (stack.size > matchIndex) {
            stack.removeAt(stack.lastIndex)
        }
    }
}
