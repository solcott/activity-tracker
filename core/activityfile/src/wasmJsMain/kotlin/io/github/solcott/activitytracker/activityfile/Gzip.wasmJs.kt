package io.github.solcott.activitytracker.activityfile

import kotlin.js.JsException
import kotlin.js.Promise
import kotlinx.coroutines.await

internal actual suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray {
    val result =
        try {
            decompress(bytes.toUint8Array(), maxBytes).await<JsAny?>()
        } catch (e: JsException) {
            activityFileError("Corrupt or truncated gzip file", e)
        }
    return uint8ArrayToByteArray(result ?: tooLargeError(maxBytes))
}

// The browser's streaming gzip inflater. Reads chunk by chunk so it can stop at [max] bytes (resolving null) instead of inflating a gzip
// bomb whole; rejects for corrupt or truncated input.
@Suppress("UnusedParameter") // Used by the JS body.
private fun decompress(bytes: JsAny, max: Int): Promise<JsAny?> =
    js(
        """(async () => {
  const reader = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip')).getReader();
  const parts = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.length;
    if (total > max) { await reader.cancel(); return null; }
    parts.push(value);
  }
  const out = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) { out.set(part, offset); offset += part.length; }
  return out;
})()"""
    )
