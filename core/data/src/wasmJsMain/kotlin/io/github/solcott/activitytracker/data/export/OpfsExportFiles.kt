package io.github.solcott.activitytracker.data.export

import kotlin.js.JsException
import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * [ExportFiles] in the browser's origin private file system (OPFS), under `exports/`: private to this origin, never shown to the user, and
 * on disk rather than in memory, so a large export fits. Every call is a promise; bytes cross between Wasm and JS in bulk
 * ([toUint8ArrayBulk]). A write the origin's quota refuses is a [NoSpaceException].
 */
class OpfsExportFiles : ExportFiles {
    override suspend fun write(exportId: String, path: String, bytes: ByteArray) {
        try {
            opfsWrite(fullPath(exportId, path), toUint8ArrayBulk(bytes)).await<JsAny?>()
        } catch (e: JsException) {
            if (isQuotaError(e)) throw NoSpaceException(bytes.size.toLong(), e)
            throw e
        }
    }

    override suspend fun read(exportId: String, path: String): ByteArray? =
        opfsRead(fullPath(exportId, path)).await<JsAny?>()?.let(::byteArrayOfBulk)

    override suspend fun list(exportId: String): List<String> {
        val paths = opfsList("$ROOT/$exportId").await<JsArray<JsString>>()
        return List(paths.length) { paths[it].toString() }
    }

    override suspend fun delete(exportId: String) {
        opfsDelete(ROOT, exportId).await<JsAny?>()
    }

    override suspend fun exportIds(): List<String> {
        val names = opfsChildren(ROOT).await<JsArray<JsString>>()
        return List(names.length) { names[it].toString() }
    }

    override suspend fun freeBytes(): Long? = storageFree().await<JsNumber?>()?.toDouble()?.toLong()

    private fun fullPath(exportId: String, path: String): String {
        require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Not a relative path: $path" }
        return "$ROOT/$exportId/$path"
    }

    private fun isQuotaError(e: JsException): Boolean = "QuotaExceeded" in (e.message ?: "")

    private companion object {
        const val ROOT = "exports"
    }
}

// Kotlin/Wasm's js() bodies can't share JS code, so each walks the folders itself.
@Suppress("UnusedParameter") // Used by the JS body.
private fun opfsWrite(path: String, bytes: JsAny): Promise<JsAny?> =
    js(
        """(async () => {
  const parts = path.split('/'); const name = parts.pop();
  let dir = await navigator.storage.getDirectory();
  for (const part of parts) dir = await dir.getDirectoryHandle(part, { create: true });
  const file = await dir.getFileHandle(name, { create: true });
  const writable = await file.createWritable();
  try { await writable.write(bytes); await writable.close(); } catch (e) { await writable.abort(); throw e; }
  return null;
})()""",
    )

@Suppress("UnusedParameter") // Used by the JS body.
private fun opfsRead(path: String): Promise<JsAny?> =
    js(
        """(async () => {
  const parts = path.split('/'); const name = parts.pop();
  try {
    let dir = await navigator.storage.getDirectory();
    for (const part of parts) dir = await dir.getDirectoryHandle(part);
    const file = await (await dir.getFileHandle(name)).getFile();
    return new Uint8Array(await file.arrayBuffer());
  } catch (e) { if (e.name === 'NotFoundError') return null; throw e; }
})()""",
    )

@Suppress("UnusedParameter") // Used by the JS body.
private fun opfsList(path: String): Promise<JsArray<JsString>> =
    js(
        """(async () => {
  const out = [];
  let dir = await navigator.storage.getDirectory();
  try { for (const part of path.split('/')) dir = await dir.getDirectoryHandle(part); } catch (e) { if (e.name === 'NotFoundError') return out; throw e; }
  const walk = async (d, prefix) => {
    for await (const [name, handle] of d.entries()) {
      const rel = prefix ? prefix + '/' + name : name;
      if (handle.kind === 'directory') await walk(handle, rel); else out.push(rel);
    }
  };
  await walk(dir, '');
  return out;
})()""",
    )

@Suppress("UnusedParameter") // Used by the JS body.
private fun opfsChildren(path: String): Promise<JsArray<JsString>> =
    js(
        """(async () => {
  const out = [];
  let dir = await navigator.storage.getDirectory();
  try { for (const part of path.split('/')) dir = await dir.getDirectoryHandle(part); } catch (e) { if (e.name === 'NotFoundError') return out; throw e; }
  for await (const name of dir.keys()) out.push(name);
  return out;
})()""",
    )

@Suppress("UnusedParameter") // Used by the JS body.
private fun opfsDelete(path: String, name: String): Promise<JsAny?> =
    js(
        """(async () => {
  try {
    let dir = await navigator.storage.getDirectory();
    for (const part of path.split('/')) dir = await dir.getDirectoryHandle(part);
    await dir.removeEntry(name, { recursive: true });
  } catch (e) { if (e.name !== 'NotFoundError') throw e; }
  return null;
})()""",
    )

private fun storageFree(): Promise<JsNumber?> =
    js("navigator.storage.estimate().then(e => (e.quota != null && e.usage != null) ? e.quota - e.usage : null)")
