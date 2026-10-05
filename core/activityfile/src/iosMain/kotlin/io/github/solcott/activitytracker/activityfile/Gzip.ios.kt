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
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/** zlib's window bits for a gzip (not zlib or raw deflate) stream: the maximum window, 15, plus 16. */
private const val GZIP_WINDOW_BITS = 15 + 16

private const val CHUNK_BYTES = 64 * 1024

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    memset(stream.ptr, 0, sizeOf<z_stream>().convert())
    if (inflateInit2_(stream.ptr, GZIP_WINDOW_BITS, ZLIB_VERSION, sizeOf<z_stream>().convert()) != Z_OK) {
        activityFileError("Couldn't start gzip decompression")
    }
    val out = mutableListOf<ByteArray>()
    var total = 0
    try {
        bytes.usePinned { input ->
            stream.next_in = input.addressOf(0).reinterpret()
            stream.avail_in = bytes.size.convert()
            val chunk = ByteArray(CHUNK_BYTES)
            chunk.usePinned { output ->
                var status: Int
                do {
                    stream.next_out = output.addressOf(0).reinterpret()
                    stream.avail_out = CHUNK_BYTES.convert()
                    status = inflate(stream.ptr, Z_NO_FLUSH)
                    // Z_BUF_ERROR here means the input ran out before the end of the stream: a truncated file.
                    if (status != Z_OK && status != Z_STREAM_END) activityFileError("Corrupt or truncated gzip file")
                    val produced = CHUNK_BYTES - stream.avail_out.toInt()
                    total += produced
                    if (total > maxBytes) tooLargeError(maxBytes)
                    out += chunk.copyOf(produced)
                } while (status != Z_STREAM_END)
            }
        }
    } finally {
        inflateEnd(stream.ptr)
    }
    ByteArray(total).also { result ->
        var offset = 0
        for (part in out) {
            part.copyInto(result, offset)
            offset += part.size
        }
    }
}
