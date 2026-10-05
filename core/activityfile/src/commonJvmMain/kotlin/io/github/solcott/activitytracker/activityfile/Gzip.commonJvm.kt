package io.github.solcott.activitytracker.activityfile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

internal actual suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray =
    try {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(CHUNK_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > maxBytes) tooLargeError(maxBytes)
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    } catch (e: IOException) {
        activityFileError("Corrupt or truncated gzip file", e)
    }

private const val CHUNK_BYTES = 64 * 1024
