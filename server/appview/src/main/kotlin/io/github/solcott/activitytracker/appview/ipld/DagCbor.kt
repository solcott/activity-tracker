package io.github.solcott.activitytracker.appview.ipld

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORParser
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/**
 * DAG-CBOR, the encoding of labels (what labelers sign) and of repo blocks (commits, tree nodes, records). Values are Kotlin's: maps with
 * text keys, lists, text, [ByteArray]s, [Long]s, booleans, null, and [Link]s (CIDs, tag 42).
 *
 * [encode] writes the canonical form: definite lengths, the shortest integer heads, and map keys sorted by length, then bytewise.
 */
object DagCbor {
    /** A CID link (CBOR tag 42). */
    class Link internal constructor(internal val cid: BlockCid) {
        override fun equals(other: Any?) = other is Link && other.cid == cid

        override fun hashCode() = cid.hashCode()

        override fun toString() = cid.string
    }

    fun encode(value: Any?): ByteArray = ByteArrayOutputStream().also { it.write(value) }.toByteArray()

    /**
     * Decodes one DAG-CBOR value that fills [bytes]. Throws [RepoFormatException] for what DAG-CBOR forbids or the atproto data model
     * lacks: floats, tags other than 42, integers outside 64 bits, duplicate map keys, and nesting deeper than [MAX_DEPTH]. Jackson reads
     * definite and indefinite lengths alike; nothing here hashes re-encoded bytes except a commit, whose signature fails if it wasn't
     * canonical.
     */
    internal fun decode(bytes: ByteArray): Any? =
        try {
            factory.createParser(bytes).use { parser ->
                parser.nextToken() ?: throw RepoFormatException("bad_cbor", "Empty DAG-CBOR")
                val value = (parser as CBORParser).readValue(0)
                if (parser.nextToken() != null) throw RepoFormatException("bad_cbor", "Trailing DAG-CBOR")
                value
            }
        } catch (e: RepoFormatException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Jackson's own parse errors, and ours from arithmetic on its values.
            throw RepoFormatException("bad_cbor", "Malformed DAG-CBOR", e)
        }

    private val factory = CBORFactory()

    @Suppress("ThrowsCount") // Untrusted input: each malformed part is refused with its own reason.
    private fun CBORParser.readValue(depth: Int): Any? {
        if (depth > MAX_DEPTH) throw RepoFormatException("bad_cbor", "DAG-CBOR nested too deeply")
        val tag = currentTag
        if (tag != NO_TAG && !(tag == CID_TAG && currentToken == JsonToken.VALUE_EMBEDDED_OBJECT)) {
            throw RepoFormatException("bad_cbor", "Unsupported CBOR tag $tag")
        }
        return when (currentToken) {
            JsonToken.START_OBJECT -> {
                val map = LinkedHashMap<String, Any?>()
                while (nextToken() != JsonToken.END_OBJECT) {
                    val key = currentName()
                    nextToken()
                    if (key in map) throw RepoFormatException("bad_cbor", "Duplicate map key")
                    map[key] = readValue(depth + 1)
                }
                map
            }
            JsonToken.START_ARRAY -> {
                val list = mutableListOf<Any?>()
                while (nextToken() != JsonToken.END_ARRAY) list += readValue(depth + 1)
                list
            }
            JsonToken.VALUE_STRING -> text
            JsonToken.VALUE_NUMBER_INT ->
                bigIntegerValue.longValueExactOrNull() ?: throw RepoFormatException("bad_cbor", "Integer too large")
            JsonToken.VALUE_TRUE -> true
            JsonToken.VALUE_FALSE -> false
            JsonToken.VALUE_NULL -> null
            JsonToken.VALUE_EMBEDDED_OBJECT -> if (tag == CID_TAG) link(binaryValue) else binaryValue
            JsonToken.VALUE_NUMBER_FLOAT -> throw RepoFormatException("bad_cbor", "Floats aren't allowed")
            else -> throw RepoFormatException("bad_cbor", "Unsupported CBOR value $currentToken")
        }
    }

    /** A tag-42 byte string: the 0x00 identity multibase prefix, then a binary CID. */
    private fun link(bytes: ByteArray): Link {
        if (bytes.isEmpty() || bytes[0] != 0.toByte()) throw RepoFormatException("bad_cid", "CID link without its multibase prefix")
        return Link(BlockCid.of(bytes.copyOfRange(1, bytes.size)))
    }

    private fun BigInteger.longValueExactOrNull(): Long? = runCatching { longValueExact() }.getOrNull()

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
            is Link -> {
                head(MAJOR_TAG, CID_TAG.toLong())
                head(MAJOR_BYTES, value.cid.bytes.size + 1L)
                write(0)
                write(value.cid.bytes)
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

    /** Deep enough for any record the lexicons allow (`AtJson` itself refuses far less). */
    private const val MAX_DEPTH = 64
    private const val NO_TAG = -1
    private const val CID_TAG = 42
    private const val MAJOR_UINT = 0
    private const val MAJOR_NEGINT = 1
    private const val MAJOR_BYTES = 2
    private const val MAJOR_TEXT = 3
    private const val MAJOR_ARRAY = 4
    private const val MAJOR_MAP = 5
    private const val MAJOR_TAG = 6
    private const val MAJOR_SHIFT = 5

    /** Arguments below 24 sit in the head byte; 24 to 27 mean 1, 2, 4 or 8 bytes follow. */
    private const val INLINE_LIMIT = 24
    private const val FALSE = 0xF4
    private const val TRUE = 0xF5
    private const val NULL = 0xF6
}
