package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.visibility.mapIsPublic
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailsView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class PublicMapTest {
    private val at = Instant.parse("2026-09-30T10:00:00Z")
    private val uri = AtUri("at://did:plc:alice/app.activitytracker.feed.activity/3kride")

    private fun view(visibility: String?, polyline: String?, details: List<DetailsView>? = null) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyride"),
            author = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
            record = ActivityRecord(sport = Sport.RIDE, startedAt = at, elapsedTime = 3600, createdAt = at, polyline = polyline),
            likeCount = 0,
            commentCount = 0,
            indexedAt = at,
            visibility = visibility,
            details = details,
        )

    @Test
    fun aPublicActivitysMapIsPublicUnlessItsMapCategoryIsNarrower() {
        assertTrue(mapIsPublic(view(Visibility.PUBLIC, polyline = "_p~iF~ps|U_ulLnnqC")))
        assertTrue(mapIsPublic(view(null, polyline = "_p~iF~ps|U_ulLnnqC")), "an activity with no visibility is public")
        val followersMap = DetailsView(Visibility.FOLLOWERS, ActivityDetailsRecord(activity = uri, createdAt = at, polyline = "_p~iF~ps|U"))
        assertFalse(mapIsPublic(view(Visibility.PUBLIC, polyline = null, details = listOf(followersMap))))
    }

    @Test
    fun aNonPublicActivitysMapIsNeverPublic() {
        assertFalse(mapIsPublic(view(Visibility.FOLLOWERS, polyline = "_p~iF~ps|U_ulLnnqC")))
        assertFalse(mapIsPublic(view(Visibility.SELF, polyline = "_p~iF~ps|U_ulLnnqC")))
    }
}
