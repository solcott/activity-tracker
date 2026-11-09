package io.github.solcott.activitytracker.data.export

import io.github.solcott.activitytracker.lexicon.Cid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class BlobRefsTest {
    private fun blob(cid: String, mime: String = "application/octet-stream", size: Long = 10) =
        """{"${'$'}type":"blob","ref":{"${'$'}link":"$cid"},"mimeType":"$mime","size":$size}"""

    @Test
    fun streamsRecordNamesItsDataBlob() {
        val record =
            Json.parseToJsonElement(
                """{"${'$'}type":"app.activitytracker.feed.activityStreams","data":${blob("bafy1", "application/gzip", 1234)}}""",
            )
        assertEquals(listOf(BlobMention(Cid("bafy1"), "application/gzip", 1234)), blobMentions(record))
    }

    @Test
    fun sourceRecordNamesItsFile() {
        val record =
            Json.parseToJsonElement(
                """{"${'$'}type":"app.activitytracker.feed.activitySource","file":${blob("bafysrc")},"fileName":"ride.fit"}""",
            )
        assertEquals(listOf(Cid("bafysrc")), blobMentions(record).map { it.cid })
    }

    @Test
    fun profileAvatarAndBannerNestedInArraysAndObjects() {
        val record =
            Json.parseToJsonElement("""{"avatar":${blob("bafyA")},"photos":[{"image":${blob("bafyB")}},{"image":${blob("bafyA")}}]}""")
        assertEquals(listOf(Cid("bafyA"), Cid("bafyB")), blobMentions(record).map { it.cid })
    }

    @Test
    fun recordWithoutBlobsAndMalformedRefs() {
        assertEquals(
            emptyList(),
            blobMentions(Json.parseToJsonElement("""{"${'$'}type":"app.activitytracker.feed.like","subject":{"uri":"at://x"}}""")),
        )
        assertEquals(
            emptyList(),
            blobMentions(
                Json.parseToJsonElement("""{"a":{"${'$'}type":"blob","ref":"bafy"},"b":{"${'$'}type":"blob","ref":{"${'$'}link":3}}}"""),
            ),
        )
    }
}
