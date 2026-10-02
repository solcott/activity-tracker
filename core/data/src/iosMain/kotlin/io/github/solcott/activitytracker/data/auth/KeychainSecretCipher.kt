package io.github.solcott.activitytracker.data.auth

import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * AES-256-GCM with a key stored in the Keychain. The item is this-device-only, so it's never synced or restored to another device. It
 * becomes readable after the first unlock, so background work can still use the session.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class KeychainSecretCipher : SecretCipher by AesGcmSecretCipher(::loadOrCreateKey)

private const val SERVICE = "io.github.solcott.activitytracker"
private const val ACCOUNT = "oauth_store_key"
private const val KEY_BYTES = 32

@OptIn(ExperimentalForeignApi::class)
private fun loadOrCreateKey(): ByteArray =
    readKey()
        ?: CryptographyRandom.nextBytes(KEY_BYTES).also { key ->
            val status =
                withKeyQuery(
                    kSecValueData to key.toNSData(),
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                ) {
                    SecItemAdd(it, null)
                }
            check(status == errSecSuccess) { "Keychain add failed: $status" }
        }

@OptIn(ExperimentalForeignApi::class)
private fun readKey(): ByteArray? = memScoped {
    val result = alloc<CFTypeRefVar>()
    val status = withKeyQuery(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { SecItemCopyMatching(it, result.ptr) }
    when (status) {
        errSecSuccess -> (CFBridgingRelease(result.value) as NSData).toByteArray()
        errSecItemNotFound -> null
        else -> error("Keychain read failed: $status")
    }
}

/**
 * Runs [block] with a generic-password query for our key plus [extra] attributes. Values are CF types or Objective-C objects (bridged
 * here).
 */
@OptIn(ExperimentalForeignApi::class)
private fun <T> withKeyQuery(vararg extra: Pair<CFStringRef?, Any?>, block: (CFDictionaryRef?) -> T): T {
    val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    try {
        (listOf(kSecClass to kSecClassGenericPassword, kSecAttrService to SERVICE, kSecAttrAccount to ACCOUNT) + extra).forEach { (k, v) ->
            if (v is CFTypeRef) {
                CFDictionaryAddValue(query, k, v)
            } else {
                // The dictionary retains the value, so drop our bridging reference right away.
                val bridged = CFBridgingRetain(v)
                CFDictionaryAddValue(query, k, bridged)
                CFRelease(bridged)
            }
        }
        return block(query)
    } finally {
        CFRelease(query)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray =
    ByteArray(length.toInt()).also { bytes -> if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) } }
