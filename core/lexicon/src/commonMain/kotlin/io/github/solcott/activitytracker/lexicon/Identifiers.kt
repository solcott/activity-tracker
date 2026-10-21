package io.github.solcott.activitytracker.lexicon

import androidx.compose.runtime.Immutable
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@JvmInline
value class Did(val value: String) {
    init {
        require(value.startsWith("did:")) { "Not a DID: $value" }
    }

    val method: String
        get() = value.substringAfter("did:").substringBefore(':')

    override fun toString(): String = value
}

@Immutable
@Serializable
@JvmInline
value class Handle(val value: String) {
    override fun toString(): String = value
}

@Immutable
@Serializable
@JvmInline
value class Cid(val value: String) {
    override fun toString(): String = value
}

/** `at://<authority>/<collection>/<rkey>` */
@Immutable
@Serializable
@JvmInline
value class AtUri(val value: String) {
    init {
        require(value.startsWith(PREFIX)) { "Not an AT-URI: $value" }
    }

    private val parts: List<String>
        get() = value.removePrefix(PREFIX).split('/')

    val authority: String
        get() = parts[0]

    val collection: String?
        get() = parts.getOrNull(1)

    val rkey: String?
        get() = parts.getOrNull(2)

    override fun toString(): String = value

    companion object {
        private const val PREFIX = "at://"

        fun of(authority: Did, collection: String, rkey: String) = AtUri("$PREFIX$authority/$collection/$rkey")

        /** [value] as an AT-URI, or null when it isn't one (typed text, say). */
        fun parseOrNull(value: String): AtUri? = if (value.startsWith(PREFIX)) AtUri(value) else null
    }
}

/** A handle or a DID, as accepted by `at-identifier` params. */
fun String.isDid(): Boolean = startsWith("did:")
