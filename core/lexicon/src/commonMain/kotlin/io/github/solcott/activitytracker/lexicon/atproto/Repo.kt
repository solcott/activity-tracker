package io.github.solcott.activitytracker.lexicon.atproto

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.NoOutput
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** `com.atproto.repo.strongRef` */
@Serializable data class StrongRef(val uri: AtUri, val cid: Cid)

/** A blob reference as stored inside records. */
@Serializable
data class BlobRef(
    @SerialName($$"$type") val type: String = "blob",
    val ref: Link,
    val mimeType: String,
    val size: Long,
) {
    @Serializable data class Link(@SerialName($$"$link") val link: String)
}

object CreateRecord {
    @Serializable
    data class Input(
        val repo: Did,
        val collection: String,
        val record: JsonElement,
        val rkey: String? = null,
        val validate: Boolean? = null,
    )

    @Serializable data class Output(val uri: AtUri, val cid: Cid)

    val method = XrpcProcedure("com.atproto.repo.createRecord", Input.serializer(), Output.serializer())
}

object PutRecord {
    @Serializable
    data class Input(
        val repo: Did,
        val collection: String,
        val rkey: String,
        val record: JsonElement,
        /** Our lexicons are unknown to most PDSes; `false` skips schema validation there. */
        val validate: Boolean? = false,
    )

    @Serializable data class Output(val uri: AtUri, val cid: Cid)

    val method = XrpcProcedure("com.atproto.repo.putRecord", Input.serializer(), Output.serializer())
}

object DeleteRecord {
    @Serializable data class Input(val repo: Did, val collection: String, val rkey: String)

    val method = XrpcProcedure("com.atproto.repo.deleteRecord", Input.serializer(), JsonObject.serializer())
}

/** `com.atproto.repo.applyWrites`, with the `#create` and `#delete` writes this app uses. A PDS takes at most [MAX_WRITES] per call. */
object ApplyWrites {
    const val MAX_WRITES = 200

    @Serializable
    data class Input(
        val repo: Did,
        val writes: List<Write>,
        /** Our lexicons are unknown to most PDSes; `false` skips schema validation there. */
        val validate: Boolean? = false,
    )

    /** One write; build it with [create] or [delete]. */
    @Serializable
    data class Write(
        @SerialName($$"$type") val type: String,
        val collection: String,
        val rkey: String? = null,
        val value: JsonElement? = null,
    )

    fun create(collection: String, rkey: String, value: JsonElement) = Write("com.atproto.repo.applyWrites#create", collection, rkey, value)

    fun delete(collection: String, rkey: String) = Write("com.atproto.repo.applyWrites#delete", collection, rkey)

    val method = XrpcProcedure("com.atproto.repo.applyWrites", Input.serializer(), JsonObject.serializer())
}

object GetRecord {
    @Serializable data class Params(val repo: String, val collection: String, val rkey: String)

    @Serializable data class Output(val uri: AtUri, val cid: Cid? = null, val value: JsonObject)

    val method = XrpcQuery("com.atproto.repo.getRecord", Params.serializer(), Output.serializer())
}

object ListRecords {
    @Serializable
    data class Params(
        val repo: String,
        val collection: String,
        val limit: Int? = null,
        val cursor: String? = null,
    )

    @Serializable data class Record(val uri: AtUri, val cid: Cid, val value: JsonObject)

    @Serializable data class Output(val records: List<Record>, val cursor: String? = null)

    val method = XrpcQuery("com.atproto.repo.listRecords", Params.serializer(), Output.serializer())
}

object UploadBlob {
    const val NSID = "com.atproto.repo.uploadBlob"

    @Serializable data class Output(val blob: BlobRef)
}

/** Returns the blob's bytes (any content type), not JSON: here, the signed-in athlete's own blobs in their repo. */
object SyncGetBlob {
    @Serializable data class Params(val did: Did, val cid: Cid)

    val method = XrpcQuery("com.atproto.sync.getBlob", Params.serializer(), NoOutput.serializer())
}
