package io.github.solcott.activitytracker.appview.labels

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import io.github.solcott.activitytracker.appview.ipld.DagCbor
import kotlin.time.Instant

/**
 * One `com.atproto.label.defs#label` as a labeler streamed it. [fields] is every field as decoded (strings, integers, booleans, byte
 * strings), so the signed bytes can be rebuilt exactly, unknown fields included.
 */
class Label(val fields: Map<String, Any?>) {
    val src: String
        get() = string("src")

    val uri: String
        get() = string("uri")

    val value: String
        get() = string("val")

    val cid: String?
        get() = fields["cid"] as? String

    val neg: Boolean
        get() = fields["neg"] == true

    val cts: Instant
        get() = Instant.parse(string("cts"))

    val exp: Instant?
        get() = (fields["exp"] as? String)?.let(Instant::parse)

    val sig: ByteArray?
        get() = fields["sig"] as? ByteArray

    /** The bytes [sig] signs: the label without `sig`, as canonical DAG-CBOR. */
    fun signedBytes(): ByteArray = DagCbor.encode(fields - "sig")

    private fun string(key: String): String = requireNotNull(fields[key] as? String) { "Label without $key" }

    override fun toString() = "Label($src $value on $uri${if (neg) ", negated" else ""})"
}

/** A `subscribeLabels` message: a DAG-CBOR header, then its body. */
sealed interface LabelFrame {
    data class Labels(val seq: Long, val labels: List<Label>) : LabelFrame

    data class Info(val name: String, val message: String?) : LabelFrame

    /** A message type this AppView doesn't know: skipped. */
    data class Unknown(val type: String?) : LabelFrame

    companion object {
        private val factory = CBORFactory()

        /**
         * Decodes one binary websocket message. An error frame (`op: -1`) throws [StreamError], since the labeler closes the stream after
         * it; anything else malformed throws [IllegalArgumentException].
         */
        fun decode(bytes: ByteArray): LabelFrame =
            factory.createParser(bytes).use { parser ->
                val header = requireNotNull(parser.readValue() as? Map<*, *>) { "Frame header isn't a map" }
                val body = requireNotNull(parser.readValue() as? Map<*, *>) { "Frame body isn't a map" }
                when ((header["op"] as? Long) ?: 0L) {
                    -1L -> throw StreamError(body["error"] as? String ?: "Unknown", body["message"] as? String)
                    1L ->
                        when (val type = header["t"] as? String) {
                            "#labels" -> labels(body)
                            "#info" -> Info((body["name"] as? String).orEmpty(), body["message"] as? String)
                            else -> Unknown(type)
                        }
                    else -> throw IllegalArgumentException("Unknown frame op ${header["op"]}")
                }
            }

        private fun labels(body: Map<*, *>): Labels {
            val seq = requireNotNull(body["seq"] as? Long) { "#labels without seq" }
            val labels =
                (body["labels"] as? List<*>).orEmpty().mapNotNull { label ->
                    (label as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v }?.let(::Label)
                }
            return Labels(seq, labels)
        }

        /** Reads the next value: maps, arrays, text, byte strings, integers, booleans and null. CIDs (tag 42) never occur in labels. */
        private fun JsonParser.readValue(): Any? {
            val token = requireNotNull(currentToken ?: nextToken()) { "Truncated frame" }
            val value: Any? =
                when (token) {
                    JsonToken.START_OBJECT -> {
                        val map = LinkedHashMap<String, Any?>()
                        while (nextToken() != JsonToken.END_OBJECT) {
                            val key = requireNotNull(currentName()) { "Map key isn't text" }
                            nextToken()
                            map[key] = readValue()
                        }
                        map
                    }
                    JsonToken.START_ARRAY -> {
                        val list = mutableListOf<Any?>()
                        while (nextToken() != JsonToken.END_ARRAY) list += readValue()
                        list
                    }
                    JsonToken.VALUE_STRING -> text
                    JsonToken.VALUE_NUMBER_INT -> bigIntegerValue.longValueExact()
                    JsonToken.VALUE_TRUE -> true
                    JsonToken.VALUE_FALSE -> false
                    JsonToken.VALUE_NULL -> null
                    JsonToken.VALUE_EMBEDDED_OBJECT -> binaryValue
                    else -> throw IllegalArgumentException("Unsupported CBOR value $token")
                }
            clearCurrentToken()
            return value
        }
    }

    class StreamError(val error: String, message: String?) : Exception("Labeler stream error $error: $message")
}
