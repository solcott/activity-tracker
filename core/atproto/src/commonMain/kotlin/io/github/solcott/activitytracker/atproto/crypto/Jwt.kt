package io.github.solcott.activitytracker.atproto.crypto

import io.github.solcott.activitytracker.lexicon.AtJson
import kotlinx.serialization.json.JsonObject

/** Builds a compact ES256 JWS signed with [key]. */
internal suspend fun signJwt(header: JsonObject, payload: JsonObject, key: DpopKey): String {
    val signingInput =
        listOf(header, payload).joinToString(".") { AtJson.encodeToString(JsonObject.serializer(), it).encodeToByteArray().base64Url() }
    val signature = key.sign(signingInput.encodeToByteArray())
    return "$signingInput.${signature.base64Url()}"
}
