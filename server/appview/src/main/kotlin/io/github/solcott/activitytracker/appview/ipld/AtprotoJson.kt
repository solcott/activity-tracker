package io.github.solcott.activitytracker.appview.ipld

import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A decoded DAG-CBOR value in the atproto data model's JSON form, as Jetstream delivers records: a CID link as `{"$link": "<cid>"}` and
 * bytes as `{"$bytes": "<base64, unpadded>"}`, so the indexer reads a record from a repo exactly as one from the firehose.
 */
internal fun atprotoJson(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is DagCbor.Link -> JsonObject(mapOf("\$link" to JsonPrimitive(value.cid.string)))
        is ByteArray -> JsonObject(mapOf("\$bytes" to JsonPrimitive(Base64.getEncoder().withoutPadding().encodeToString(value))))
        is List<*> -> JsonArray(value.map(::atprotoJson))
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k as String to atprotoJson(v) })
        else -> throw IllegalArgumentException("Not a DAG-CBOR value: ${value::class.simpleName}")
    }
