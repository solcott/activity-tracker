@file:Suppress("MatchingDeclarationName") // blobMentions; BlobMention is only what it returns.

package io.github.solcott.activitytracker.data.export

import io.github.solcott.activitytracker.lexicon.Cid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A blob a record names: its CID and, when the ref says, its MIME type and size in bytes. */
data class BlobMention(val cid: Cid, val mimeType: String?, val size: Long?)

/**
 * Every blob [record] names, anywhere in it: each `{"$type": "blob", "ref": {"$link": cid}}` object, in the order found, each CID once. Any
 * client can write the athlete's records, so a malformed ref is skipped rather than failing the export.
 */
fun blobMentions(record: JsonElement): List<BlobMention> {
    val found = LinkedHashMap<String, BlobMention>()
    fun walk(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                blobOf(element)?.let { found.getOrPut(it.cid.value) { it } }
                element.values.forEach(::walk)
            }
            is JsonArray -> element.forEach(::walk)
            is JsonPrimitive -> Unit
        }
    }
    walk(record)
    return found.values.toList()
}

private fun blobOf(obj: JsonObject): BlobMention? {
    val isBlob = (obj["\$type"] as? JsonPrimitive)?.content == "blob"
    val link = ((obj["ref"] as? JsonObject)?.get("\$link") as? JsonPrimitive)?.takeIf { isBlob && it.isString }?.content ?: return null
    val mimeType = (obj["mimeType"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val size = (obj["size"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull()
    return BlobMention(Cid(link), mimeType, size)
}
