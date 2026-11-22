package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.GetPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** `feed.getPath#pathProfile` (segments/path-profile), as `AtJson` writes and reads it. */
class PathProfileSerializationTest {
    private val profile =
        GetPath.PathProfile(
            pieces = listOf(GetPath.PieceProfile(piece = 0, heights = listOf(15_000, 15_420, 15_100), lengthMeters = 100, maxGrade = 84)),
            gain = 42,
            loss = 32,
            spans = listOf(GetPath.SegmentSpan(AtUri("at://did:plc:a/app.activitytracker.feed.segment/1"), 0, 20, 80)),
        )

    @Test
    fun roundTrips() {
        val json = AtJson.encodeToString(GetPath.PathProfile.serializer(), profile)
        assertEquals(profile, AtJson.decodeFromString(GetPath.PathProfile.serializer(), json))
    }

    @Test
    fun writesTheLexiconsNames() {
        val json = AtJson.encodeToJsonElement(GetPath.PathProfile.serializer(), profile).jsonObject
        val piece = json.getValue("pieces").jsonArray[0].jsonObject
        assertEquals(100L, piece.getValue("lengthMeters").jsonPrimitive.long)
        assertEquals(84, piece.getValue("maxGrade").jsonPrimitive.int)
        val span = json.getValue("spans").jsonArray[0].jsonObject
        assertEquals(20L, span.getValue("startMeters").jsonPrimitive.long)
        assertEquals(80L, span.getValue("endMeters").jsonPrimitive.long)
    }
}
