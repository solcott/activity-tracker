@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.activitytracker.data.auth

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AES-256-GCM with a non-extractable WebCrypto key persisted in IndexedDB. Script on this origin can use the key while the page is open,
 * but can never read its bytes, so stolen storage alone can't be decrypted.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class WebCryptoSecretCipher : SecretCipher {
    private val mutex = Mutex()
    private var key: JsAny? = null

    private suspend fun key(): JsAny = mutex.withLock { key ?: loadOrCreateKey().await<JsAny>().also { key = it } }

    override suspend fun encrypt(plaintext: String): String = encrypt(key(), plaintext).await<JsString>().toString()

    override suspend fun decrypt(ciphertext: String): String = decrypt(key(), ciphertext).await<JsString>().toString()
}

/**
 * Reads the key from IndexedDB, creating it on first use. `add` (not `put`) so a concurrent tab's key wins instead of being overwritten.
 */
private fun loadOrCreateKey(): Promise<JsAny> =
    js(
        """
(async () => {
  const db = await new Promise((resolve, reject) => {
    const req = indexedDB.open('activity-tracker-keys', 1);
    req.onupgradeneeded = () => req.result.createObjectStore('keys');
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  const run = (mode, op) => new Promise((resolve, reject) => {
    const req = op(db.transaction('keys', mode).objectStore('keys'));
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  try {
    let key = await run('readonly', (s) => s.get('oauth_store'));
    if (!key) {
      const fresh = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
      try {
        await run('readwrite', (s) => s.add(fresh, 'oauth_store'));
        key = fresh;
      } catch (e) {
        key = await run('readonly', (s) => s.get('oauth_store'));
        if (!key) throw e;
      }
    }
    return key;
  } finally {
    db.close();
  }
})()
"""
    )

/** Returns base64(iv || ciphertext || tag). */
@Suppress("UnusedParameter") // Used by the JS body.
private fun encrypt(key: JsAny, plaintext: String): Promise<JsString> =
    js(
        """
(async () => {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(plaintext)));
  const out = new Uint8Array(iv.length + ct.length);
  out.set(iv);
  out.set(ct, iv.length);
  let bin = '';
  for (const b of out) bin += String.fromCharCode(b);
  return btoa(bin);
})()
"""
    )

@Suppress("UnusedParameter") // Used by the JS body.
private fun decrypt(key: JsAny, ciphertext: String): Promise<JsString> =
    js(
        """
(async () => {
  const bytes = Uint8Array.from(atob(ciphertext), (c) => c.charCodeAt(0));
  const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: bytes.subarray(0, 12) }, key, bytes.subarray(12));
  return new TextDecoder().decode(pt);
})()
"""
    )
