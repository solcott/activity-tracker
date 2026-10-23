// Builders take every field a test may change, each with a default, so a test names only what it cares about.
@file:Suppress("LongParameterList")

package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Instant

fun fakeProfileBasic(
    did: Did = Fixed.ALICE,
    handle: String = handleOf(did),
    displayName: String? = displayNameOf(did),
    avatar: String? = null,
): ProfileViewBasic = ProfileViewBasic(did, Handle(handle), displayName, avatar)

fun fakeProfile(
    did: Did = Fixed.ALICE,
    handle: String = handleOf(did),
    displayName: String? = displayNameOf(did),
    description: String? = "Trail runner and weekend rider.",
    avatar: String? = null,
    details: ProfileDetails? = ProfileDetails(location = Location(locality = "Boulder", region = "CO", country = "US")),
    activitiesCount: Long = 42,
    followersCount: Long = 12,
    followsCount: Long = 8,
    followPolicy: String? = null,
): ProfileView =
    ProfileView(
        did = did,
        handle = Handle(handle),
        displayName = displayName,
        description = description,
        avatar = avatar,
        details = details,
        followPolicy = followPolicy,
        activitiesCount = activitiesCount,
        followersCount = followersCount,
        followsCount = followsCount,
    )

fun fakeActivityRecord(
    sport: String = Sport.RIDE,
    title: String? = "Morning Ride",
    startedAt: Instant = Fixed.STARTED_AT,
    elapsedTime: Long = 3_900,
    movingTime: Long? = 3_600,
    distance: Long? = 32_400,
    elevationGain: Long? = 410,
    polyline: String? = Fixed.POLYLINE,
    description: String? = null,
    utcOffset: Int? = MDT_OFFSET_MINUTES,
): ActivityRecord =
    ActivityRecord(
        sport = sport,
        startedAt = startedAt,
        elapsedTime = elapsedTime,
        createdAt = Fixed.CREATED_AT,
        title = title,
        description = description,
        movingTime = movingTime,
        distance = distance,
        elevationGain = elevationGain,
        avgSpeed = distance?.let { d -> movingTime?.let { t -> d * MM_PER_M / t } },
        polyline = polyline,
        utcOffset = utcOffset,
    )

fun fakeActivity(
    rkey: String = "3kactivity1",
    author: ProfileViewBasic = fakeProfileBasic(),
    record: ActivityRecord = fakeActivityRecord(),
    likeCount: Long = 3,
    commentCount: Long = 1,
    visibility: String? = Visibility.PUBLIC,
): ActivityView =
    ActivityView(
        uri = Fixed.uri(author.did, Nsid.FEED_ACTIVITY, rkey),
        cid = Fixed.cid(rkey),
        author = author,
        record = record,
        likeCount = likeCount,
        commentCount = commentCount,
        indexedAt = Fixed.CREATED_AT,
        visibility = visibility,
    )

fun fakeComment(
    rkey: String = "3kcomment1",
    author: ProfileViewBasic = fakeProfileBasic(Fixed.BOB),
    text: String = "Great ride!",
): CommentView =
    CommentView(
        uri = Fixed.uri(author.did, Nsid.FEED_COMMENT, rkey),
        cid = Fixed.cid(rkey),
        author = author,
        text = text,
        createdAt = Fixed.CREATED_AT,
    )

fun fakeGearItem(
    name: String = "Gravel bike",
    type: String = GearType.BIKE,
    brand: String? = "Rivendell",
    model: String? = "Appaloosa",
    sports: List<String>? = listOf(Sport.RIDE),
    weight: Int? = 11_200,
): GearItemRecord =
    GearItemRecord(name = name, type = type, createdAt = Fixed.CREATED_AT, brand = brand, model = model, sports = sports, weight = weight)

private fun handleOf(did: Did): String = "${did.value.substringAfterLast(':')}.test"

private fun displayNameOf(did: Did): String = did.value.substringAfterLast(':').replaceFirstChar { it.uppercase() }

private const val MM_PER_M = 1000

/** Mountain Daylight Time, where the sample route is. */
private const val MDT_OFFSET_MINUTES = -6 * 60
