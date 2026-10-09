package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A member of `app.activitytracker.feed.activity#media`, an open union: [Image] today, and any member a newer client writes decodes as
 * [Unknown] and re-encodes untouched, so editing a record never drops it.
 */
@Immutable
@Serializable(with = ActivityMediaSerializer::class)
sealed interface ActivityMedia {
    /** `app.activitytracker.feed.activity#image` */
    @Immutable
    @Serializable
    data class Image(
        /** WebP or JPEG, at most [MAX_BYTES]. */
        val image: BlobRef,
        val alt: String? = null,
        val aspectRatio: AspectRatio? = null,
        @SerialName("\$type") val type: String = TYPE,
    ) : ActivityMedia {
        companion object {
            const val TYPE = "${Nsid.FEED_ACTIVITY}#image"

            /** The lexicon's `maxSize`. */
            const val MAX_BYTES = 2_000_000L
            const val MAX_ALT_LENGTH = 3000
        }
    }

    /** A member this client doesn't know, kept as it was written. */
    @Immutable data class Unknown(val json: JsonObject) : ActivityMedia

    companion object {
        /** The lexicon's `maxLength` for `media`. */
        const val MAX = 10
    }
}

/**
 * A member of `app.activitytracker.feed.defs#activityView.media`, an open union like [ActivityMedia]: [Image] today, [Unknown] for a kind
 * this client can't show.
 */
@Immutable
@Serializable(with = ActivityMediaViewSerializer::class)
sealed interface ActivityMediaView {
    /** `app.activitytracker.feed.defs#imageView` */
    @Immutable
    @Serializable
    data class Image(
        /** URL of the photo as uploaded. */
        val fullsize: String,
        val alt: String? = null,
        val aspectRatio: AspectRatio? = null,
        @SerialName("\$type") val type: String = TYPE,
    ) : ActivityMediaView {
        companion object {
            const val TYPE = "${Nsid.FEED_DEFS}#imageView"
        }
    }

    @Immutable data class Unknown(val json: JsonObject) : ActivityMediaView
}

/** `app.activitytracker.feed.activity#aspectRatio`: width and height in any unit, both at least 1. */
@Immutable @Serializable data class AspectRatio(val width: Int, val height: Int)

internal object ActivityMediaSerializer : OpenUnionSerializer<ActivityMedia>("ActivityMedia") {
    override fun memberFor(type: String?) = ActivityMedia.Image.serializer().takeIf { type == ActivityMedia.Image.TYPE }

    override fun unknown(json: JsonObject) = ActivityMedia.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: ActivityMedia) =
        when (value) {
            is ActivityMedia.Image -> encoder.encodeSerializableValue(ActivityMedia.Image.serializer(), value)
            is ActivityMedia.Unknown -> encoder.encodeJsonElement(value.json)
        }
}

internal object ActivityMediaViewSerializer : OpenUnionSerializer<ActivityMediaView>("ActivityMediaView") {
    override fun memberFor(type: String?) = ActivityMediaView.Image.serializer().takeIf { type == ActivityMediaView.Image.TYPE }

    override fun unknown(json: JsonObject) = ActivityMediaView.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: ActivityMediaView) =
        when (value) {
            is ActivityMediaView.Image -> encoder.encodeSerializableValue(ActivityMediaView.Image.serializer(), value)
            is ActivityMediaView.Unknown -> encoder.encodeJsonElement(value.json)
        }
}

/**
 * An open lexicon union, picked by `$type`. Each known member is a `@Serializable` class that writes its own `$type`; anything else decodes
 * through [unknown] and re-encodes as the same JSON. JSON only, which is all lexicon data uses (`AtJson`).
 */
internal abstract class OpenUnionSerializer<T : Any>(name: String) : KSerializer<T> {
    override val descriptor: SerialDescriptor = SerialDescriptor(name, JsonObject.serializer().descriptor)

    /** The known member whose `$type` is [type], or null. */
    protected abstract fun memberFor(type: String?): KSerializer<out T>?

    protected abstract fun unknown(json: JsonObject): T

    protected abstract fun encode(encoder: JsonEncoder, value: T)

    override fun deserialize(decoder: Decoder): T {
        val json = decoder as? JsonDecoder ?: throw SerializationException("$descriptor decodes only from JSON")
        val element = json.decodeJsonElement() as? JsonObject ?: throw SerializationException("$descriptor must be an object")
        val type = (element["\$type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val member = memberFor(type) ?: return unknown(element)
        return json.json.decodeFromJsonElement(member, element)
    }

    override fun serialize(encoder: Encoder, value: T) =
        encode(encoder as? JsonEncoder ?: throw SerializationException("$descriptor encodes only to JSON"), value)
}
