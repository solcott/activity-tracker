package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.lexicon.AtJson
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The `network.bsky.jetstream.subscribeEvents` messages we consume (only the fields we use). Each websocket frame is a proposal-0015
 * envelope, `{"$type":"message","payload":{...}}` or a terminal `{"$type":"error",...}`; see [decodeFrame].
 */
sealed interface JetstreamEvent {
    val did: String

    /** When Jetstream witnessed the event, the portable resume position (`?cursor=<unix-µs>`). Older servers only send `time`. */
    val witnessedAt: String?
    val time: String

    val witnessedAtUs: Long
        get() = Instant.parse(witnessedAt ?: time).let { it.epochSeconds * 1_000_000 + it.nanosecondsOfSecond / 1_000 }

    @Serializable
    data class Commit(
        override val did: String,
        override val time: String,
        override val witnessedAt: String? = null,
        val operation: String,
        val collection: String,
        val rkey: String,
        val record: JsonObject? = null,
        val cid: String? = null,
    ) : JetstreamEvent

    /** Wraps the upstream `com.atproto.sync.subscribeRepos#identity`. */
    @Serializable
    data class Identity(
        override val did: String,
        override val time: String,
        override val witnessedAt: String? = null,
        val identity: Body,
    ) : JetstreamEvent {
        @Serializable data class Body(val did: String, val handle: String? = null)
    }

    /** Wraps the upstream `com.atproto.sync.subscribeRepos#account`. */
    @Serializable
    data class Account(
        override val did: String,
        override val time: String,
        override val witnessedAt: String? = null,
        val account: Body,
    ) : JetstreamEvent {
        @Serializable data class Body(val did: String, val active: Boolean, val status: String? = null)
    }

    class StreamError(error: String, message: String?) : Exception("$error: $message")

    companion object {
        private const val PREFIX = "network.bsky.jetstream.subscribeEvents#"

        /** Kinds we subscribe to; `#sync` and the seq-less `#info` are skipped. */
        val kinds = listOf("commit", "identity", "account")

        /** Decodes one frame; null for message types we skip. Throws [StreamError] on a terminal error frame. */
        fun decodeFrame(text: String): JetstreamEvent? {
            val frame = AtJson.decodeFromString(Frame.serializer(), text)
            if (frame.type == "error") throw StreamError(frame.error ?: "Unknown", frame.message)
            val payload = frame.payload?.takeIf { frame.type == "message" }
            val serializer =
                when (payload?.get("\$type")?.jsonPrimitive?.content?.removePrefix(PREFIX)) {
                    "commit" -> Commit.serializer()
                    "identity" -> Identity.serializer()
                    "account" -> Account.serializer()
                    else -> null
                }
            return if (payload != null && serializer != null) AtJson.decodeFromJsonElement(serializer, payload) else null
        }
    }

    @Serializable
    private data class Frame(
        @SerialName("\$type") val type: String,
        val payload: JsonObject? = null,
        val error: String? = null,
        val message: String? = null,
    )
}
