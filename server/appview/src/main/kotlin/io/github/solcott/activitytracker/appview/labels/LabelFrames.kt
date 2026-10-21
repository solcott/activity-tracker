package io.github.solcott.activitytracker.appview.labels

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import java.io.ByteArrayOutputStream
import java.math.BigInteger
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

/**
 * The canonical DAG-CBOR encoding of a label's fields (the bytes labelers sign): definite lengths, the shortest integer heads, and map keys
 * sorted by length, then bytewise. Covers the value types labels hold.
 */
object DagCbor {
    fun encode(value: Any?): ByteArray = ByteArrayOutputStream().also { it.write(value) }.toByteArray()

    private fun ByteArrayOutputStream.write(value: Any?) {
        when (value) {
            null -> write(NULL)
            true -> write(TRUE)
            false -> write(FALSE)
            is Int -> integer(value.toLong())
            is Long -> integer(value)
            is String ->
                value.encodeToByteArray().let {
                    head(MAJOR_TEXT, it.size.toLong())
                    write(it)
                }
            is ByteArray -> {
                head(MAJOR_BYTES, value.size.toLong())
                write(value)
            }
            is List<*> -> {
                head(MAJOR_ARRAY, value.size.toLong())
                value.forEach { write(it) }
            }
            is Map<*, *> -> {
                val entries = value.entries.map { (k, v) -> (k as String).encodeToByteArray() to v }.sortedWith(KEY_ORDER)
                head(MAJOR_MAP, entries.size.toLong())
                entries.forEach { (key, v) ->
                    head(MAJOR_TEXT, key.size.toLong())
                    write(key)
                    write(v)
                }
            }
            else -> throw IllegalArgumentException("Can't encode ${value::class.simpleName} as DAG-CBOR")
        }
    }

    private fun ByteArrayOutputStream.integer(value: Long) = if (value >= 0) head(MAJOR_UINT, value) else head(MAJOR_NEGINT, -1 - value)

    /** A major type with its argument in the fewest bytes. */
    private fun ByteArrayOutputStream.head(major: Int, argument: Long) {
        val bytes =
            when {
                argument < INLINE_LIMIT -> {
                    write((major shl MAJOR_SHIFT) or argument.toInt())
                    return
                }
                argument <= 0xFF -> 1
                argument <= 0xFFFF -> 2
                argument <= 0xFFFFFFFFL -> 4
                else -> 8
            }
        write((major shl MAJOR_SHIFT) or (INLINE_LIMIT + bytes.countTrailingZeroBits()))
        val big = BigInteger.valueOf(argument).toByteArray().takeLast(bytes)
        repeat(bytes - big.size) { write(0) }
        big.forEach { write(it.toInt()) }
    }

    private val KEY_ORDER =
        Comparator<Pair<ByteArray, Any?>> { a, b ->
            val (x, y) = a.first to b.first
            if (x.size != y.size) x.size - y.size
            else x.indices.firstOrNull { x[it] != y[it] }?.let { (x[it].toInt() and 0xFF) - (y[it].toInt() and 0xFF) } ?: 0
        }

    private const val MAJOR_UINT = 0
    private const val MAJOR_NEGINT = 1
    private const val MAJOR_BYTES = 2
    private const val MAJOR_TEXT = 3
    private const val MAJOR_ARRAY = 4
    private const val MAJOR_MAP = 5
    private const val MAJOR_SHIFT = 5

    /** Arguments below 24 sit in the head byte; 24 to 27 mean 1, 2, 4 or 8 bytes follow. */
    private const val INLINE_LIMIT = 24
    private const val FALSE = 0xF4
    private const val TRUE = 0xF5
    private const val NULL = 0xF6
}
