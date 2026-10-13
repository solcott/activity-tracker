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

/** The payload of a compact JWS, decoded but not verified: for reading claims of a token another party verifies. */
internal fun jwtPayload(token: String): JsonObject {
    val payload = token.split('.').getOrNull(1) ?: throw IllegalArgumentException("Not a compact JWS")
    return AtJson.decodeFromString(JsonObject.serializer(), Base64Url.decode(payload).decodeToString())
}
