package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ActivityMediaSerializationTest {
    private val photo =
        ActivityMedia.Image(
            image = BlobRef(ref = BlobRef.Link("bafkphoto"), mimeType = "image/webp", size = 120_000),
            aspectRatio = AspectRatio(4, 3),
        )

    @Test
    fun imageCarriesItsType() {
        val json = AtJson.encodeToJsonElement(ActivityMedia.serializer(), photo).jsonObject
        assertEquals("app.activitytracker.feed.activity#image", json["\$type"]?.jsonPrimitive?.content)
        assertEquals("bafkphoto", json["image"]?.jsonObject?.get("ref")?.jsonObject?.get("\$link")?.jsonPrimitive?.content)
        assertEquals(photo, AtJson.decodeFromJsonElement(ActivityMedia.serializer(), json))
    }

    @Test
    fun anUnknownMemberSurvivesARoundTrip() {
        val video = """{"${'$'}type":"app.activitytracker.feed.activity#video","video":{"size":1},"futureField":[1,2]}"""
        val json =
            """
            {"${'$'}type":"${Nsid.FEED_ACTIVITY}","sport":"dive","startedAt":"2026-09-30T14:00:00Z",
             "elapsedTime":1800,"createdAt":"2026-09-30T14:31:00Z",
             "media":[$video,${AtJson.encodeToString(ActivityMedia.serializer(), photo)}]}
            """
                .trimIndent()
        val record = AtJson.decodeFromString(ActivityRecord.serializer(), json)
        val (unknown, image) = record.media.orEmpty()
        assertIs<ActivityMedia.Unknown>(unknown)
        assertEquals(photo, image)

        val media = AtJson.encodeToJsonElement(ActivityRecord.serializer(), record).jsonObject.getValue("media").jsonArray
        assertEquals(AtJson.parseToJsonElement(video), media[0])
    }

    @Test
    fun viewsDecodeImagesAndKeepTheRest() {
        val json =
            """
            [{"${'$'}type":"app.activitytracker.feed.defs#imageView","fullsize":"https://av/img/media/did/cid","aspectRatio":{"width":3,"height":4}},
             {"${'$'}type":"app.activitytracker.feed.defs#videoView","playlist":"x"},
             {"fullsize":"no type"}]
            """
                .trimIndent()
        val (image, video, untyped) =
            AtJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ActivityMediaView.serializer()), json)
        assertEquals(ActivityMediaView.Image(fullsize = "https://av/img/media/did/cid", aspectRatio = AspectRatio(3, 4)), image)
        assertIs<ActivityMediaView.Unknown>(video)
        assertIs<JsonObject>((untyped as ActivityMediaView.Unknown).json)
    }
}
