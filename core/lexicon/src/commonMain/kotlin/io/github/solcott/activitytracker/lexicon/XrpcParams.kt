package io.github.solcott.activitytracker.lexicon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Encodes/decodes XRPC query params (flat primitives and arrays) to/from query-string pairs. */
object XrpcParams {
    fun <P> encode(serializer: KSerializer<P>, params: P): List<Pair<String, String>> {
        val obj = AtJson.encodeToJsonElement(serializer, params) as? JsonObject ?: return emptyList()
        return obj.flatMap { (key, value) ->
            when (value) {
                is JsonNull -> emptyList()
                is JsonPrimitive -> listOf(key to value.content)
                is JsonArray -> value.map { key to (it as JsonPrimitive).content }
                is JsonObject -> error("XRPC params must be flat; '$key' is an object")
            }
        }
    }

    /**
     * [arrayKeys] lists params declared as arrays, so a single occurrence still decodes to a list. Values stay strings; [AtJson] is lenient
     * so numbers and booleans decode from them.
     */
    fun <P> decode(
        serializer: KSerializer<P>,
        query: Map<String, List<String>>,
        arrayKeys: Set<String> = emptySet(),
    ): P {
        val obj =
            JsonObject(
                query.mapValues { (key, values) ->
                    if (key in arrayKeys || values.size > 1) {
                        JsonArray(values.map(::JsonPrimitive))
                    } else {
                        JsonPrimitive(values.first())
                    }
                }
            )
        return AtJson.decodeFromJsonElement(serializer, obj.jsonObject)
    }
}
