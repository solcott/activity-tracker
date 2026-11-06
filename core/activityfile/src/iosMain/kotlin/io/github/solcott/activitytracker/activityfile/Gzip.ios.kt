package io.github.solcott.activitytracker.activityfile

import no.synth.kmpzlib.Deflater
import no.synth.kmpzlib.ZlibFormat

internal actual suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray {
    val input = BufferedInput(SourceInput(ByteArrayImportSource("gzip", bytes), 0, bytes.size.toLong()))
    return InflaterInput(input, ZlibFormat.GZIP).readAll(maxBytes)
}

internal actual suspend fun gzip(bytes: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, ZlibFormat.GZIP)
    var out = ByteArray(maxOf(CHUNK_BYTES, bytes.size / 2))
    var size = 0
    var consumed = 0
    while (!deflater.isFinished) {
        if (size == out.size) out = out.copyOf(out.size * 2)
        val result = deflater.deflate(bytes, consumed, bytes.size - consumed, out, size, out.size - size, finish = true)
        consumed += result.bytesConsumed
        size += result.bytesProduced
    }
    return out.copyOf(size)
}
