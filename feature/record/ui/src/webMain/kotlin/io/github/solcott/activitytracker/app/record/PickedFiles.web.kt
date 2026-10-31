@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.ByteArrayImportSource
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.WebFile
import io.github.vinceglb.filekit.name
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsException
import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.files.Blob

internal actual fun PlatformFile.toImportSource(): ImportSource =
    when (val file = webFile) {
        is WebFile.FileWrapper -> BlobSource(file.file.name, file.file)
        // A file from the origin's own storage, which a picker never gives: nothing to read.
        else -> ByteArrayImportSource(name, ByteArray(0))
    }

/** A picked file read by slicing its blob, so the browser never loads it whole. */
private class BlobSource(override val name: String, private val blob: Blob) : ImportSource {
    override val size: Long = blob.size.toDouble().toLong()

    override suspend fun read(offset: Long, length: Int): ByteArray {
        val end = minOf(size, offset + length)
        if (offset >= end) return ByteArray(0)
        val bytes =
            try {
                slice(blob, offset.toDouble(), end.toDouble()).await()
            } catch (e: JsException) {
                throw ActivityFileException("Couldn't read $name", e)
            }
        return ByteArray(bytes.length) { bytes[it] }
    }
}

@Suppress("UnusedParameter") // Used by the JS body.
private fun slice(blob: Blob, start: Double, end: Double): Promise<Int8Array> =
    js("blob.slice(start, end).arrayBuffer().then(b => new Int8Array(b))")
