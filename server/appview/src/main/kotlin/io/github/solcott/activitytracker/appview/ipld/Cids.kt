package io.github.solcott.activitytracker.appview.ipld

import io.github.solcott.activitytracker.atproto.crypto.base32
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.security.MessageDigest

/**
 * A CIDv1 as atproto repos use them: version 1, a codec (`dag-cbor` for commits, tree nodes and records), and a SHA-256 multihash. Anything
 * else (a CIDv0, another hash, a short digest) is refused when read, so every block a repo names can be checked against its bytes.
 */
internal class BlockCid private constructor(val bytes: ByteArray, val codec: Long) {
    /** The multibase `b` (lowercase base32) string atproto writes CIDs as, and Jetstream delivers. */
    val string: String = "b" + bytes.base32()

    /** Whether [block] hashes to this CID's digest. */
    fun matches(block: ByteArray): Boolean =
        MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(block), bytes.copyOfRange(bytes.size - SHA256_LENGTH, bytes.size))

    override fun equals(other: Any?) = other is BlockCid && other.string == string

    override fun hashCode() = string.hashCode()

    override fun toString() = string

    companion object {
        const val DAG_CBOR = 0x71L
        const val RAW = 0x55L
        private const val SHA256 = 0x12L
        private const val SHA256_LENGTH = 32

        /** Reads one binary CID from [input]; throws [RepoFormatException] for anything but a SHA-256 CIDv1. */
        @Suppress("ThrowsCount") // Untrusted input: each malformed part is refused with its own reason.
        fun read(input: InputStream): BlockCid {
            val out = ByteArrayOutputStream()
            val version = Varint.read(input, out)
            if (version != 1L) throw RepoFormatException("bad_cid", "Not a CIDv1")
            val codec = Varint.read(input, out)
            val hash = Varint.read(input, out)
            val length = Varint.read(input, out)
            if (hash != SHA256 || length != SHA256_LENGTH.toLong()) throw RepoFormatException("bad_cid", "Not a SHA-256 CID")
            val digest = input.readNBytes(SHA256_LENGTH)
            if (digest.size != SHA256_LENGTH) throw RepoFormatException("truncated", "Truncated CID")
            out.write(digest)
            return BlockCid(out.toByteArray(), codec)
        }

        /** The CID of [block] under [codec], as a repo names its blocks. */
        fun ofBlock(block: ByteArray, codec: Long = DAG_CBOR): BlockCid {
            val out = ByteArrayOutputStream()
            out.write(1)
            Varint.write(out, codec)
            Varint.write(out, SHA256)
            Varint.write(out, SHA256_LENGTH.toLong())
            out.write(MessageDigest.getInstance("SHA-256").digest(block))
            return BlockCid(out.toByteArray(), codec)
        }

        /** A CID from its binary form (a DAG-CBOR link's bytes after the 0x00 multibase prefix), which must hold nothing else. */
        fun of(bytes: ByteArray): BlockCid {
            val input = bytes.inputStream()
            val cid = read(input)
            if (input.read() != -1) throw RepoFormatException("bad_cid", "Trailing bytes after a CID")
            return cid
        }
    }
}

/** Unsigned LEB128 varints, as CAR files frame their sections and CIDs encode their fields. */
internal object Varint {
    private const val MAX_BYTES = 9
    private const val SEVEN_BITS = 0x7F

    /** Payload bits in each varint byte. */
    private const val BITS_PER_BYTE = 7
    private const val CONTINUES = 0x80

    /** Reads one varint from [input], copying its bytes to [copy] if given. Returns null at a clean end of stream (no bytes at all). */
    @Suppress("ThrowsCount") // Untrusted input: each malformed part is refused with its own reason.
    fun readOrEnd(input: InputStream, copy: ByteArrayOutputStream? = null): Long? {
        var value = 0L
        for (i in 0 until MAX_BYTES) {
            val byte = input.read()
            if (byte == -1) {
                if (i == 0) return null
                throw RepoFormatException("truncated", "Truncated varint")
            }
            copy?.write(byte)
            value = value or ((byte and SEVEN_BITS).toLong() shl (BITS_PER_BYTE * i))
            if (byte and CONTINUES == 0) {
                // Minimal encoding only: a trailing zero byte would let two encodings name one value.
                if (byte == 0 && i > 0) throw RepoFormatException("bad_varint", "Varint not minimally encoded")
                return value
            }
        }
        throw RepoFormatException("bad_varint", "Varint too long")
    }

    fun write(out: ByteArrayOutputStream, value: Long) {
        var rest = value
        while (rest >= CONTINUES) {
            out.write(((rest and SEVEN_BITS.toLong()) or CONTINUES.toLong()).toInt())
            rest = rest ushr BITS_PER_BYTE
        }
        out.write(rest.toInt())
    }

    fun read(input: InputStream, copy: ByteArrayOutputStream? = null): Long =
        readOrEnd(input, copy) ?: throw RepoFormatException("truncated", "Truncated varint", EOFException())
}

/**
 * A repo that can't be read as one: [code] is a short, stable reason the run records, [message] says what was wrong (never record data).
 */
internal open class RepoFormatException(val code: String, message: String, cause: Throwable? = null) : Exception(message, cause)

/** A configured limit was reached: the run stops, having changed nothing. */
internal class RepoLimitException(code: String, message: String) : RepoFormatException(code, message)
