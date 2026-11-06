package io.github.solcott.activitytracker.data.export

import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * Downloads [archive] in the browser without holding it in memory (auth/data-export): the zip streams into a file in the origin private
 * file system, chunk by chunk, and the browser downloads that file, which it keeps on disk. The file is deleted once the download has
 * started. Throws [NoSpaceException] when the origin's quota can't hold it.
 */
suspend fun downloadArchive(archive: ExportArchive) {
    val writer = openOutput(archive.name).await<JsAny>()
    try {
        archive.writeTo { chunk -> writeChunk(writer, toUint8ArrayBulk(chunk)).await<JsAny?>() }
        closeOutput(writer).await<JsAny?>()
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) { // Any failure drops the half-written file, then goes on up.
        abortOutput(writer).await<JsAny?>()
        if ("QuotaExceeded" in (e.message ?: "")) throw NoSpaceException(cause = e)
        throw e
    }
    downloadOutput(archive.name).await<JsAny?>()
}

// The zip's OPFS file: `export-downloads/<name>`, apart from the staged exports.
@Suppress("UnusedParameter") // Used by the JS body.
private fun openOutput(name: String): Promise<JsAny> =
    js(
        """(async () => {
  const root = await navigator.storage.getDirectory();
  const dir = await root.getDirectoryHandle('export-downloads', { create: true });
  const file = await dir.getFileHandle(name, { create: true });
  return await file.createWritable();
})()"""
    )

@Suppress("UnusedParameter") // Used by the JS body.
private fun writeChunk(writer: JsAny, chunk: JsAny): Promise<JsAny?> = js("writer.write(chunk).then(() => null)")

@Suppress("UnusedParameter") // Used by the JS body.
private fun closeOutput(writer: JsAny): Promise<JsAny?> = js("writer.close().then(() => null)")

@Suppress("UnusedParameter") // Used by the JS body.
private fun abortOutput(writer: JsAny): Promise<JsAny?> = js("writer.abort().then(() => null, () => null)")

// The File is backed by disk, so the object URL streams it; the OPFS entry goes once the browser has the download, a minute later.
@Suppress("UnusedParameter") // Used by the JS body.
private fun downloadOutput(name: String): Promise<JsAny?> =
    js(
        """(async () => {
  const root = await navigator.storage.getDirectory();
  const dir = await root.getDirectoryHandle('export-downloads');
  const file = await (await dir.getFileHandle(name)).getFile();
  const url = URL.createObjectURL(file);
  const a = document.createElement('a');
  a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => { URL.revokeObjectURL(url); dir.removeEntry(name).catch(() => {}); }, 60000);
  return null;
})()"""
    )
