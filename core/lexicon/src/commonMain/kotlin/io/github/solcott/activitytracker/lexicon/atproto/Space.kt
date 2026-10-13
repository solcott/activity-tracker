package io.github.solcott.activitytracker.lexicon.atproto

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.NoOutput
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.OpenUnionSerializer
import kotlin.jvm.JvmInline
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject

/*
 * atproto Spaces (permissioned repos), mirrored from the `com.atproto.space.*` and `com.atproto.simplespace.*` lexicons in the PDS image
 * pinned in docker/pds.Dockerfile. The protocol is an alpha that changes weekly, so every Spaces wire type lives in this file; bump it
 * together with the image.
 */

/** `space-ref`: `at://{authority}/space/{type}/{skey}`, one space of a type under its authority. */
@Serializable
@JvmInline
value class SpaceUri(val value: String) {
    init {
        val parts = value.removePrefix(PREFIX).split('/')
        require(value.startsWith(PREFIX) && parts.size == PARTS && parts[1] == SEGMENT && parts.none(String::isEmpty)) {
            "Not a space URI: $value"
        }
        require(parts[0].startsWith("did:")) { "A space's authority must be a DID: $value" }
    }

    private val parts: List<String>
        get() = value.removePrefix(PREFIX).split('/')

    val authority: Did
        get() = Did(parts[0])

    /** The space type's NSID. */
    val type: String
        get() = parts[2]

    /** The space key, which tells apart spaces of one type under one authority. */
    val skey: String
        get() = parts[3]

    override fun toString(): String = value

    companion object {
        private const val PREFIX = "at://"
        private const val SEGMENT = "space"

        /** Authority, `space`, type and key. */
        private const val PARTS = 4

        fun of(authority: Did, type: String, skey: String) = SpaceUri("$PREFIX$authority/$SEGMENT/$type/$skey")
    }
}

/**
 * Error names the Spaces methods and their auth layer return, as they appear in [io.github.solcott.activitytracker.lexicon.XrpcErrorBody].
 */
object SpaceErrors {
    /** The authority refused this user. */
    const val USER_NOT_AUTHORIZED = "UserNotAuthorized"

    /** The authority refused this app. */
    const val APP_NOT_AUTHORIZED = "AppNotAuthorized"

    /** The authority refused without saying whether the user or the app failed. */
    const val NOT_AUTHORIZED = "NotAuthorized"

    /** The credential has expired; get a new one. */
    const val JWT_EXPIRED = "JwtExpired"

    /** The authority revoked the credential; get a new one. */
    const val CREDENTIAL_REVOKED = "CredentialRevoked"

    /** The OAuth session wasn't granted the `space:` scope this call needs. */
    const val SCOPE_MISSING = "ScopeMissingError"

    const val SPACE_ALREADY_EXISTS = "SpaceAlreadyExists"
    const val SPACE_NOT_FOUND = "SpaceNotFound"
    const val RECORD_NOT_FOUND = "RecordNotFound"
    const val BLOB_NOT_FOUND = "BlobNotFound"

    /** Errors meaning the viewer may not read the space. */
    val denials = setOf(USER_NOT_AUTHORIZED, APP_NOT_AUTHORIZED, NOT_AUTHORIZED)
}

/**
 * A member of `com.atproto.simplespace.createSpace#readPolicy`/`#writePolicy`: how the authority decides which users may read a space or
 * write to it. An open union, so a policy a newer PDS reports decodes as [Unknown].
 */
@Serializable(with = SpacePolicySerializer::class)
sealed interface SpacePolicy {
    /** `#publicPolicy`: any signed-in user. */
    @Serializable
    data class Public(@SerialName("\$type") val type: String = TYPE) : SpacePolicy {
        companion object {
            const val TYPE = "com.atproto.simplespace.defs#publicPolicy"
        }
    }

    /** `#memberListPolicy`: users the authority added with `simplespace.putMember`. */
    @Serializable
    data class MemberList(@SerialName("\$type") val type: String = TYPE) : SpacePolicy {
        companion object {
            const val TYPE = "com.atproto.simplespace.defs#memberListPolicy"
        }
    }

    /** `#managingAppPolicy`: the PDS asks [managingApp] through `simplespace.checkUserAccess`. */
    @Serializable
    data class ManagingApp(
        /** Service identifier of the managing app: a DID plus an optional service fragment (`did:web:host#activitytracker_space`). */
        val managingApp: String,
        @SerialName("\$type") val type: String = TYPE,
    ) : SpacePolicy {
        companion object {
            const val TYPE = "com.atproto.simplespace.defs#managingAppPolicy"
        }
    }

    data class Unknown(val json: JsonObject) : SpacePolicy
}

/** A member of `com.atproto.simplespace.createSpace#appAccess`: which apps may use a space. An open union. */
@Serializable(with = SpaceAppAccessSerializer::class)
sealed interface SpaceAppAccess {
    /** `#open`: any app, no client attestation. Our native clients are public clients, so this is the only one they can use. */
    @Serializable
    data class Open(@SerialName("\$type") val type: String = TYPE) : SpaceAppAccess {
        companion object {
            const val TYPE = "com.atproto.simplespace.defs#open"
        }
    }

    /** `#allowList`: only the attested OAuth client IDs in [allowed]. */
    @Serializable
    data class AllowList(val allowed: List<String>, @SerialName("\$type") val type: String = TYPE) : SpaceAppAccess {
        companion object {
            const val TYPE = "com.atproto.simplespace.defs#allowList"
        }
    }

    data class Unknown(val json: JsonObject) : SpaceAppAccess
}

internal object SpacePolicySerializer : OpenUnionSerializer<SpacePolicy>("SpacePolicy") {
    override fun memberFor(type: String?) =
        when (type) {
            SpacePolicy.Public.TYPE -> SpacePolicy.Public.serializer()
            SpacePolicy.MemberList.TYPE -> SpacePolicy.MemberList.serializer()
            SpacePolicy.ManagingApp.TYPE -> SpacePolicy.ManagingApp.serializer()
            else -> null
        }

    override fun unknown(json: JsonObject) = SpacePolicy.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: SpacePolicy) =
        when (value) {
            is SpacePolicy.Public -> encoder.encodeSerializableValue(SpacePolicy.Public.serializer(), value)
            is SpacePolicy.MemberList -> encoder.encodeSerializableValue(SpacePolicy.MemberList.serializer(), value)
            is SpacePolicy.ManagingApp -> encoder.encodeSerializableValue(SpacePolicy.ManagingApp.serializer(), value)
            is SpacePolicy.Unknown -> encoder.encodeJsonElement(value.json)
        }
}

internal object SpaceAppAccessSerializer : OpenUnionSerializer<SpaceAppAccess>("SpaceAppAccess") {
    override fun memberFor(type: String?) =
        when (type) {
            SpaceAppAccess.Open.TYPE -> SpaceAppAccess.Open.serializer()
            SpaceAppAccess.AllowList.TYPE -> SpaceAppAccess.AllowList.serializer()
            else -> null
        }

    override fun unknown(json: JsonObject) = SpaceAppAccess.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: SpaceAppAccess) =
        when (value) {
            is SpaceAppAccess.Open -> encoder.encodeSerializableValue(SpaceAppAccess.Open.serializer(), value)
            is SpaceAppAccess.AllowList -> encoder.encodeSerializableValue(SpaceAppAccess.AllowList.serializer(), value)
            is SpaceAppAccess.Unknown -> encoder.encodeJsonElement(value.json)
        }
}

/** `com.atproto.space.*`: credentials and reads and writes of space repos. */
object Space {
    /** On the viewer's own PDS: a 60 s, single-use token naming the viewer, to exchange at the space's authority. */
    object GetDelegationToken {
        @Serializable data class Params(val space: SpaceUri)

        @Serializable data class Output(val token: String)

        val method = XrpcQuery("com.atproto.space.getDelegationToken", Params.serializer(), Output.serializer())
    }

    /**
     * On the authority's PDS, with the delegation token as `Bearer` and a signed request: a credential for reading the space. The output
     * carries no expiry; it's the credential JWT's `exp`.
     */
    object GetSpaceCredential {
        @Serializable data class Input(val space: SpaceUri, val clientAttestation: String? = null)

        @Serializable data class Output(val credential: String)

        val method = XrpcProcedure("com.atproto.space.getSpaceCredential", Input.serializer(), Output.serializer())
    }

    object GetRecord {
        @Serializable data class Params(val space: SpaceUri, val repo: Did, val collection: String, val rkey: String)

        @Serializable data class Output(val uri: AtUri, val cid: Cid, val value: JsonObject)

        val method = XrpcQuery("com.atproto.space.getRecord", Params.serializer(), Output.serializer())
    }

    object ListRecords {
        @Serializable
        data class Params(
            val space: SpaceUri,
            val repo: Did,
            /** Omitted lists every collection. */
            val collection: String? = null,
            /** 1–1000; the PDS defaults to 50. */
            val limit: Int? = null,
            val cursor: String? = null,
            val reverse: Boolean? = null,
        )

        @Serializable data class Record(val collection: String, val rkey: String, val cid: Cid, val value: JsonObject? = null)

        @Serializable data class Output(val records: List<Record>, val cursor: String? = null)

        val method = XrpcQuery("com.atproto.space.listRecords", Params.serializer(), Output.serializer())
    }

    /** Returns the blob's bytes (any content type), not JSON. */
    object GetBlob {
        @Serializable data class Params(val space: SpaceUri, val repo: Did, val cid: Cid)

        val method = XrpcQuery("com.atproto.space.getBlob", Params.serializer(), NoOutput.serializer())
    }

    object PutRecord {
        @Serializable
        data class Input(
            val space: SpaceUri,
            /** The writer's own DID: each member writes to their own repo in the space. */
            val repo: Did,
            val collection: String,
            val rkey: String,
            val record: JsonElement,
            /** Our lexicons are unknown to most PDSes; `false` skips schema validation there. */
            val validate: Boolean? = false,
        )

        @Serializable data class Output(val uri: AtUri, val cid: Cid)

        val method = XrpcProcedure("com.atproto.space.putRecord", Input.serializer(), Output.serializer())
    }

    object DeleteRecord {
        @Serializable data class Input(val space: SpaceUri, val repo: Did, val collection: String, val rkey: String)

        val method = XrpcProcedure("com.atproto.space.deleteRecord", Input.serializer(), JsonObject.serializer())
    }
}

/** `com.atproto.simplespace.*`: the PDS's built-in space authority, on the owner's PDS. */
object SimpleSpace {
    object CreateSpace {
        @Serializable
        data class Input(
            /** The space type's NSID. */
            val spaceType: String,
            /** Omitted gets a TID. */
            val skey: String? = null,
            val readPolicy: SpacePolicy,
            val writePolicy: SpacePolicy,
            val appAccess: SpaceAppAccess,
        )

        @Serializable data class Output(val uri: SpaceUri)

        val method = XrpcProcedure("com.atproto.simplespace.createSpace", Input.serializer(), Output.serializer())
    }

    object GetSpace {
        @Serializable data class Params(val space: SpaceUri)

        @Serializable
        data class Output(val uri: SpaceUri, val readPolicy: SpacePolicy, val writePolicy: SpacePolicy, val appAccess: SpaceAppAccess)

        val method = XrpcQuery("com.atproto.simplespace.getSpace", Params.serializer(), Output.serializer())
    }

    /**
     * Served by the managing app, not the PDS: the space authority's PDS asks whether [Params.user] may read or write a space whose policy
     * for that access is `#managingAppPolicy`. Authenticated with a service-auth token from the authority, addressed to the managing app.
     */
    object CheckUserAccess {
        @Serializable
        data class Params(
            val space: SpaceUri,
            val user: Did,
            /** [READ] or [WRITE]; an open set. */
            val access: String,
            /** The attested OAuth client, when the viewer's client presented an attestation; never sent for writes. */
            val clientId: String? = null,
        )

        @Serializable data class Output(val authorized: Boolean)

        const val READ = "read"
        const val WRITE = "write"

        val method = XrpcQuery("com.atproto.simplespace.checkUserAccess", Params.serializer(), Output.serializer())
    }
}
