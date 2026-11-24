package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.location.toXmlText
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.newGenericWriter
import nl.adaptivity.xmlutil.xmlStreaming

/**
 * The export writers' view of xmlutil's writer: elements in the document's [namespace] unless named otherwise, text and attribute values
 * passed through [toXmlText] (the writer escapes them, and throws on what XML can't hold), and line breaks placed by hand so a file keeps
 * one point to a line rather than one element to a line.
 */
internal class XmlOutput private constructor(private val writer: XmlWriter, private val namespace: String) {

    fun element(name: String, ns: String = namespace, body: XmlOutput.() -> Unit = {}) {
        writer.startTag(ns, name, null)
        body()
        writer.endTag(ns, name, null)
    }

    /** An element holding [value]'s text, or nothing when it's null. */
    fun leaf(name: String, value: Any?, ns: String = namespace) {
        if (value != null) element(name, ns) { text(value) }
    }

    fun attr(name: String, value: Any) = writer.attribute(null, name, null, value.toString().toXmlText())

    fun text(value: Any) = writer.text(value.toString().toXmlText())

    /** A line break indented for the next child of the open element. */
    fun line() = writer.ignorableWhitespace("\n" + INDENT.repeat(writer.depth))

    /** A line break indented for the open element's end tag. */
    fun endLine() = writer.ignorableWhitespace("\n" + INDENT.repeat(writer.depth - 1))

    companion object {
        private const val INDENT = "  "

        /**
         * A UTF-8 document whose [root] element is in [namespace], the default namespace, which also declares each of [prefixes] (prefix to
         * namespace) for the elements under it.
         */
        fun document(
            namespace: String,
            root: String,
            prefixes: Map<String, String> = emptyMap(),
            body: XmlOutput.() -> Unit,
        ): String {
            val out = StringBuilder()
            val writer = xmlStreaming.newGenericWriter(out, xmlDeclMode = XmlDeclMode.None)
            writer.addTrailingSpaceBeforeEnd = false
            writer.startDocument("1.0", "UTF-8", null)
            val xml = XmlOutput(writer, namespace)
            xml.line()
            writer.startTag(namespace, root, "")
            writer.namespaceAttr("", namespace)
            for ((prefix, uri) in prefixes) writer.namespaceAttr(prefix, uri)
            xml.body()
            xml.endLine()
            writer.endTag(namespace, root, "")
            xml.line()
            writer.endDocument()
            return out.toString()
        }
    }
}
