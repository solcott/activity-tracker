package io.github.solcott.activitytracker.data.auth

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** The desktop secret key's one entry in the OS credential store. Calls block, so make them off the main thread. */
interface DesktopKeyStore {
    /** The stored value, or null when there's none, there's no store, or the store can't be read. */
    fun read(): String?

    /** Throws [KeyStoreRefusedException] when there's no store or it refuses [value]. */
    fun write(value: String)
}

class KeyStoreRefusedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The macOS Keychain or Windows Credential Manager, through java-keyring, under the names the iOS Keychain item uses. Linux has none:
 * java-keyring's Secret Service backend is built on dbus-java 4, and filekit pins dbus-java 5, which removed the calls it makes.
 */
@ContributesBinding(AppScope::class)
@Inject
class KeyringKeyStore : DesktopKeyStore {
    private val keyring: Result<Keyring> by lazy {
        val os = System.getProperty("os.name").lowercase()
        if ("mac" in os || "win" in os) {
            // A backend that fails to link throws an Error rather than BackendNotSupportedException, so take any failure.
            runCatching { Keyring.create() }
        } else {
            Result.failure(UnsupportedOperationException("No supported credential store on $os"))
        }
    }

    override fun read(): String? =
        keyring.getOrNull()?.let {
            // java-keyring reports a missing entry and a failed read alike.
            try {
                it.getPassword(SERVICE, ACCOUNT)
            } catch (_: PasswordAccessException) {
                null
            }
        }

    override fun write(value: String) {
        val keyring = keyring.getOrElse { throw KeyStoreRefusedException("No credential store: $it", it) }
        try {
            keyring.setPassword(SERVICE, ACCOUNT, value)
        } catch (e: PasswordAccessException) {
            throw KeyStoreRefusedException("The credential store refused the key: ${e.message}", e)
        }
    }

    private companion object {
        const val SERVICE = "io.github.solcott.activitytracker"
        const val ACCOUNT = "oauth_store_key"
    }
}
