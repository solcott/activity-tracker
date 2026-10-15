package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.NewPhoto
import io.github.solcott.activitytracker.data.activity.mediaViews
import io.github.solcott.activitytracker.data.activity.photoCid
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ActivityPhotosTest {
    private val photo = NewPhoto(byteArrayOf(1, 2, 3), "image/webp", width = 4, height = 3)

    private fun record(vararg cids: String) =
        ActivityRecord(
            sport = "run",
            startedAt = Instant.parse("2026-10-01T07:00:00Z"),
            elapsedTime = 1800,
            createdAt = Instant.parse("2026-10-01T08:00:00Z"),
            media = cids.map { ActivityMedia.Image(BlobRef(ref = BlobRef.Link(it), mimeType = "image/webp", size = 10)) },
        )

    private fun urls(views: List<ActivityMediaView>?) = views.orEmpty().map { (it as ActivityMediaView.Image).fullsize }

    private val ownBlobUrl = { cid: String -> "https://pds/xrpc/com.atproto.sync.getBlob?did=did%3Aplc%3Aalice&cid=$cid" }

    @Test
    fun photoCidIsTheLastPathSegmentWhateverTheQuery() {
        assertEquals("bafk1", photoCid("https://appview/img/media/did:plc:alice/bafk1"))
        assertEquals("bafk1", photoCid("https://appview/img/media/did:plc:alice/bafk1?viewer=did%3Aplc%3Abob&exp=1&sig=a%2Fb"))
        assertEquals("bafk1", photoCid(HeldPhotos.url("bafk1")))
    }

    @Test
    fun aNonPublicPhotoShowsFromTheAppViewThenHeldBytesButNeverThePds() {
        val held = HeldPhotos().apply { hold("bafknew", photo) }
        val signed = ActivityMediaView.Image("https://appview/img/media/did:plc:alice/bafkold?viewer=did%3Aplc%3Aalice&sig=x")

        val views = mediaViews(record("bafkold", "bafknew", "bafklost"), listOf(signed), Visibility.FOLLOWERS, held, ownBlobUrl)

        assertEquals(listOf(signed.fullsize, HeldPhotos.url("bafknew"), HeldPhotos.url("bafklost")), urls(views))
    }

    @Test
    fun aPublicPhotoFallsBackToThePdsOnlyWithoutHeldBytes() {
        val held = HeldPhotos().apply { hold("bafknew", photo) }

        val views = mediaViews(record("bafknew", "bafklost"), served = null, Visibility.PUBLIC, held, ownBlobUrl)

        assertEquals(listOf(HeldPhotos.url("bafknew"), ownBlobUrl("bafklost")), urls(views))
    }

    @Test
    fun aServedPhotoWinsAndItsBytesAreDropped() {
        val held = HeldPhotos().apply { hold("bafknew", photo) }
        val served = ActivityMediaView.Image("https://appview/img/media/did:plc:alice/bafknew?sig=x")

        assertEquals(listOf(served), mediaViews(record("bafknew"), listOf(served), Visibility.SELF, held, ownBlobUrl))
        assertNull(held["bafknew"])
    }

    @Test
    fun aHeldUrlFromAnEarlierRewriteIsNotTakenAsServed() {
        val held = HeldPhotos().apply { hold("bafknew", photo) }
        val earlier = listOf(ActivityMediaView.Image(HeldPhotos.url("bafknew")))

        mediaViews(record("bafknew"), earlier, Visibility.MUTUALS, held, ownBlobUrl)

        assertEquals(photo, held["bafknew"])
    }
}
