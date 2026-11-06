package io.github.solcott.activitytracker.activityfile

import no.synth.kmpzlib.Inflater
import no.synth.kmpzlib.ZlibDataException
import no.synth.kmpzlib.ZlibFormat

/**
 * Raw deflate (RFC 1951) decompressed as it's read, in common code with kmp-zlib: a zip member inside another zip arrives as a stream,
 * which the platforms' inflaters can't all take a piece at a time (the browser's `DecompressionStream` needs the whole input to say where
 * output ends). Takes from [input]'s buffer only the bytes the deflate data uses, so a zip read as a stream finds the next header after it.
 * Fails with [ActivityFileException] on corrupt or truncated data.
 */
internal class InflaterInput(private val input: BufferedInput) : ByteInput {
    private val inflater = Inflater(ZlibFormat.RAW)

    override suspend fun read(into: ByteArray, offset: Int, length: Int): Int {
        var produced = 0
        while (length > 0 && produced == 0 && !inflater.isFinished) produced = inflateSome(into, offset, length)
        return if (length > 0 && produced == 0) -1 else produced
    }

    /** Feeds the inflater what's buffered, reading more first if none is; returns how many bytes it wrote into [into]. */
    private suspend fun inflateSome(into: ByteArray, offset: Int, length: Int): Int {
        val more = input.ensure(1)
        var produced = 0
        var consumed = 0
        input.consume { buffer, from, count ->
            val result =
                try {
                    inflater.inflate(buffer, from, count, into, offset, length)
                } catch (_: ZlibDataException) {
                    activityFileError("Corrupt compressed data")
                }
            produced = result.bytesProduced
            consumed = result.bytesConsumed
            consumed
        }
        // No output and nothing more to feed it: the data stopped before its end.
        val starved = !more || consumed == 0
        if (produced == 0 && starved && !inflater.isFinished) activityFileError("Truncated compressed data")
        return produced
    }
}
