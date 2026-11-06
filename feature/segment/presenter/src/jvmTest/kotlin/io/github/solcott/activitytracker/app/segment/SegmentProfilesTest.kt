package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ElevationProfile
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** A segment's profile as its screen shows it (segments/segment-elevation): high and low from its heights, the AppView's max grade. */
class SegmentProfilesTest {
    private fun output(profile: ElevationProfile?) =
        GetSegment.Output(
            SegmentView(
                uri = AtUri("at://did:plc:alice/${Nsid.FEED_SEGMENT}/3ks"),
                cid = Cid("bafysegment"),
                creator = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
                record = SegmentRecord("Longview", "ride", "_p~iF~ps|U", "athlete", "CC0-1.0", Instant.parse("2026-10-01T00:00:00Z")),
                distanceMeters = 5_200,
            ),
            profile,
        )

    @Test
    fun highLowAndMaxGrade() {
        val profile = assertNotNull(segmentProfileOf(output(ElevationProfile(listOf(16_160, 17_000, 18_353, 17_500), maxGrade = 162))))
        assertEquals(1835.3, profile.highM)
        assertEquals(1616.0, profile.lowM)
        assertEquals(16.2, profile.maxGradePercent)
    }

    @Test
    fun aProfileWithoutAMaxGradeStillHasHighAndLow() {
        val profile = assertNotNull(segmentProfileOf(output(ElevationProfile(listOf(10_000, 10_600, 10_300)))))
        assertEquals(1060.0, profile.highM)
        assertEquals(1000.0, profile.lowM)
        assertNull(profile.maxGradePercent)
    }

    @Test
    fun noProfileNoExtremes() {
        assertNull(segmentProfileOf(output(null)))
    }
}
