package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDetail
import io.github.solcott.activitytracker.lexicon.app.TrailDetailInfo
import io.github.solcott.activitytracker.lexicon.app.TrailDetailSuggestionRecord
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteRecord
import io.github.solcott.activitytracker.lexicon.app.TrailDog
import io.github.solcott.activitytracker.lexicon.app.TrailSource
import io.github.solcott.activitytracker.lexicon.app.TrailSources
import io.github.solcott.activitytracker.lexicon.app.TrailSurface
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `feed.trailDetailSuggestion`, `feed.trailDetailVote`, `#trailSources` and the [TrailDetail] rules (segments/trail-detail-suggestions).
 */
class TrailDetailSerializationTest {
    private val alice = Did("did:plc:alice")
    private val segment = AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, "osm-123")
    private val at = Instant.parse("2026-11-23T12:00:00Z")

    @Test
    fun suggestionRoundTrips() {
        val record = TrailDetailSuggestionRecord(segment, TrailDetail.DOG, TrailAttributes(dog = TrailDog.NO), at)
        val json = AtJson.encodeToJsonElement(TrailDetailSuggestionRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_TRAIL_DETAIL_SUGGESTION, json["\$type"]?.jsonPrimitive?.content)
        assertEquals("no", json["value"]?.jsonObject?.get("dog")?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(TrailDetailSuggestionRecord.serializer(), json))
    }

    @Test
    fun voteAndConfirmationRoundTrip() {
        val vote =
            TrailDetailVoteRecord(
                direction = TrailDetailVoteDirection.UP,
                createdAt = at,
                subject = StrongRef(AtUri.of(alice, Nsid.FEED_TRAIL_DETAIL_SUGGESTION, "3l2abcdefgh22"), Cid("bafy")),
            )
        val confirmation =
            TrailDetailVoteRecord(
                direction = TrailDetailVoteDirection.STILL,
                createdAt = at,
                segment = segment,
                detail = TrailDetail.DOG,
                value = TrailAttributes(dog = TrailDog.LEASHED),
            )
        listOf(vote, confirmation).forEach { record ->
            val json = AtJson.encodeToJsonElement(TrailDetailVoteRecord.serializer(), record).jsonObject
            assertEquals(Nsid.FEED_TRAIL_DETAIL_VOTE, json["\$type"]?.jsonPrimitive?.content)
            assertEquals(record, AtJson.decodeFromJsonElement(TrailDetailVoteRecord.serializer(), json))
        }
        assertFalse(AtJson.encodeToJsonElement(TrailDetailVoteRecord.serializer(), vote).jsonObject.containsKey("segment"))
    }

    @Test
    fun onlySuggestionsComeFromTheFirehose() {
        assertTrue(Nsid.FEED_TRAIL_DETAIL_SUGGESTION in Nsid.indexedCollections)
        assertFalse(Nsid.FEED_TRAIL_DETAIL_VOTE in Nsid.indexedCollections)
        assertTrue(Nsid.FEED_TRAIL_DETAIL_VOTE in AthleteSpaceDeclaration.collections)
    }

    @Test
    fun sourcesRoundTripAndDropUnknownValues() {
        val trail = TrailAttributes(widthCm = 50, sources = TrailSources(width = TrailSource.USFS))
        assertEquals(
            trail,
            AtJson.decodeFromString(TrailAttributes.serializer(), AtJson.encodeToString(TrailAttributes.serializer(), trail)),
        )
        assertFalse(trail.isEmpty)
        assertTrue(TrailAttributes(sources = TrailSources(width = TrailSource.OSM)).isEmpty)
        assertEquals(TrailSources(dog = TrailSource.OSM), TrailSources.of(mapOf(TrailDetail.DOG to "osm", TrailDetail.LIT to "x")))
        assertNull(TrailSources.of(mapOf(TrailDetail.LIT to TrailSource.COMMUNITY)))
        assertEquals(TrailSource.OSM, TrailSources(dog = TrailSource.OSM).of(TrailDetail.DOG))
    }

    @Test
    fun valueOfTakesOneDetailAndWidthAsItsKind() {
        val trail = TrailAttributes(dog = TrailDog.YES, widthCm = 120, openFrom = "06-15", openUntil = "10-15", mtbScale = 2)
        assertEquals(TrailAttributes(dog = TrailDog.YES), TrailDetail.valueOf(trail, TrailDetail.DOG))
        assertEquals(TrailAttributes(widthCm = TrailDetail.SINGLETRACK_CM), TrailDetail.valueOf(trail, TrailDetail.WIDTH))
        assertEquals(TrailAttributes(openFrom = "06-15", openUntil = "10-15"), TrailDetail.valueOf(trail, TrailDetail.SEASON))
        assertNull(TrailDetail.valueOf(trail, TrailDetail.LIT))
        assertEquals(
            listOf(TrailDetail.MTB_SCALE, TrailDetail.WIDTH, TrailDetail.DOG, TrailDetail.SEASON),
            TrailDetail.detailsOf(trail),
        )
    }

    @Test
    fun replaceSwapsOnlyThatDetail() {
        val trail = TrailAttributes(dog = TrailDog.YES, seasons = listOf("winter"), lit = "yes")
        val swapped = TrailDetail.replace(trail, TrailDetail.SEASON, TrailAttributes(openFrom = "05-01", openUntil = "10-31"))
        assertEquals(TrailAttributes(dog = TrailDog.YES, lit = "yes", openFrom = "05-01", openUntil = "10-31"), swapped)
        assertEquals(TrailAttributes(lit = "yes", seasons = listOf("winter")), TrailDetail.replace(trail, TrailDetail.DOG, null))
    }

    @Test
    fun suggestionsMustBeOneSuggestableDetailInItsVocabulary() {
        assertTrue(TrailDetail.isSuggestion(TrailDetail.DOG, TrailAttributes(dog = TrailDog.NO)))
        assertTrue(TrailDetail.isSuggestion(TrailDetail.SURFACE, TrailAttributes(surfaces = listOf(TrailSurface("gravel", 100)))))
        assertTrue(TrailDetail.isSuggestion(TrailDetail.WIDTH, TrailAttributes(widthCm = TrailDetail.DOUBLETRACK_CM)))
        assertTrue(TrailDetail.isSuggestion(TrailDetail.SEASON, TrailAttributes(seasonal = true)))
        assertFalse(TrailDetail.isSuggestion(TrailDetail.DOG, TrailAttributes(dog = "sometimes")))
        assertFalse(TrailDetail.isSuggestion(TrailDetail.DOG, TrailAttributes(dog = TrailDog.NO, lit = "yes")))
        assertFalse(TrailDetail.isSuggestion(TrailDetail.WEBSITE, TrailAttributes(website = "https://example.com")))
        assertFalse(TrailDetail.isSuggestion(TrailDetail.WIDTH, TrailAttributes(widthCm = 120)))
        assertFalse(TrailDetail.isSuggestion(TrailDetail.SURFACE, TrailAttributes(surfaces = listOf(TrailSurface("gravel", 70)))))
        assertFalse(TrailDetail.isSuggestion(TrailDetail.SEASON, TrailAttributes(seasonal = true, seasons = listOf("winter"))))
        assertFalse(TrailDetail.isSuggestion(TrailDetail.SEASON, TrailAttributes(openFrom = "02-30", openUntil = "10-31")))
    }

    @Test
    fun detailInfoDefaults() {
        val info = AtJson.decodeFromString(TrailDetailInfo.serializer(), """{"detail":"dog","source":"community","riders":9}""")
        assertEquals(TrailDetailInfo(TrailDetail.DOG, TrailSource.COMMUNITY, riders = 9), info)
    }
}
