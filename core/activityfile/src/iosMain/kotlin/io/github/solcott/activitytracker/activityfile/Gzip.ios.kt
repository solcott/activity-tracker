package io.github.solcott.activitytracker.activityfile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.memset
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/** zlib's window bits for a gzip (not zlib or raw deflate) stream: the maximum window, 15, plus 16. */
private const val GZIP_WINDOW_BITS = 15 + 16

/** zlib's default memory level for deflate. */
private const val DEFLATE_MEM_LEVEL = 8

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    memset(stream.ptr, 0, sizeOf<z_stream>().convert())
    if (inflateInit2_(stream.ptr, GZIP_WINDOW_BITS, ZLIB_VERSION, sizeOf<z_stream>().convert()) != Z_OK) {
        activityFileError("Couldn't start gzip decompression")
    }
    try {
        stream.pump(bytes, maxBytes) {
            val status = inflate(stream.ptr, Z_NO_FLUSH)
            // Z_BUF_ERROR here means the input ran out before the end of the stream: a truncated (or empty) file.
            if (status != Z_OK && status != Z_STREAM_END) activityFileError("Corrupt or truncated gzip file")
            status == Z_STREAM_END
        }
    } finally {
        inflateEnd(stream.ptr)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun gzip(bytes: ByteArray): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    memset(stream.ptr, 0, sizeOf<z_stream>().convert())
    val started =
        deflateInit2_(
            stream.ptr,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            GZIP_WINDOW_BITS,
            DEFLATE_MEM_LEVEL,
            Z_DEFAULT_STRATEGY,
            ZLIB_VERSION,
            sizeOf<z_stream>().convert(),
        )
    check(started == Z_OK) { "Couldn't start gzip compression" }
    try {
        stream.pump(bytes, maxBytes = Int.MAX_VALUE) {
            val status = deflate(stream.ptr, Z_FINISH)
            check(status == Z_OK || status == Z_STREAM_END) { "gzip compression failed ($status)" }
            status == Z_STREAM_END
        }
    } finally {
        deflateEnd(stream.ptr)
    }
}

/**
 * Feeds [bytes] to this started stream and runs [step] (one `inflate` or `deflate` call, true once the stream has ended) a chunk of output
 * at a time, failing once the output passes [maxBytes]. Returns the output in one array.
 */
@OptIn(ExperimentalForeignApi::class)
private inline fun z_stream.pump(bytes: ByteArray, maxBytes: Int, step: () -> Boolean): ByteArray {
    val parts = mutableListOf<ByteArray>()
    var total = 0
    // An empty array can't be pinned at index 0, so pin one placeholder byte with no input available instead.
    val input = if (bytes.isEmpty()) ByteArray(1) else bytes
    input.usePinned { pinned ->
        next_in = pinned.addressOf(0).reinterpret()
        avail_in = bytes.size.convert()
        val chunk = ByteArray(CHUNK_BYTES)
        chunk.usePinned { output ->
            do {
                next_out = output.addressOf(0).reinterpret()
                avail_out = CHUNK_BYTES.convert()
                val ended = step()
                val produced = CHUNK_BYTES - avail_out.toInt()
                total += produced
                if (total > maxBytes) tooLargeError(maxBytes)
                parts += chunk.copyOf(produced)
            } while (!ended)
        }
    }
    return concat(parts, total)
}

/** [parts], [total] bytes in all, as one array. */
private fun concat(parts: List<ByteArray>, total: Int): ByteArray =
    ByteArray(total).also { result ->
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
    }
