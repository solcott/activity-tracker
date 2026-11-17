// Builders take every field a test may change, each with a default, so a test names only what it cares about.
@file:Suppress("LongParameterList")

package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic

fun fakeSegmentRecord(
    name: String = "Ridge Trail",
    sport: String = SegmentSportGroup.RIDE,
    line: String = Fixed.POLYLINE,
    source: String = SegmentSource.ATHLETE,
    license: String = "CC0-1.0",
    attribution: String? = null,
    kind: String? = null,
): SegmentRecord =
    SegmentRecord(
        name = name,
        sport = sport,
        line = line,
        source = source,
        license = license,
        createdAt = Fixed.CREATED_AT,
        attribution = attribution,
        kind = kind,
    )

fun fakeSegment(
    rkey: String = "3ksegment1",
    creator: ProfileViewBasic = fakeProfileBasic(),
    record: SegmentRecord = fakeSegmentRecord(),
    distanceMeters: Long = 1_000,
    status: String? = null,
): SegmentView =
    SegmentView(
        uri = Fixed.uri(creator.did, Nsid.FEED_SEGMENT, rkey),
        cid = Fixed.cid(rkey),
        creator = creator,
        record = record,
        distanceMeters = distanceMeters,
        status = status,
    )

fun fakeSegmentBasic(segment: SegmentView = fakeSegment()): SegmentViewBasic =
    SegmentViewBasic(segment.uri, segment.record.name, segment.record.sport, segment.distanceMeters, segment.status, segment.record.kind)

fun fakeEffort(
    segment: SegmentViewBasic = fakeSegmentBasic(),
    activity: AtUri = fakeActivity().uri,
    elapsed: Long = 245,
    startedOn: String = "2026-05-31",
    rank: Int? = null,
    best: Boolean? = null,
    startOffset: Long = 600,
): EffortView = EffortView(segment, activity, startOffset = startOffset, elapsed = elapsed, startedOn = startedOn, best = best, rank = rank)

fun fakeLeaderboardEntry(rank: Int = 1, athlete: ProfileViewBasic = fakeProfileBasic(), elapsed: Long = 245): LeaderboardEntry =
    LeaderboardEntry(rank, athlete, fakeEffort(activity = fakeActivity(author = athlete).uri, elapsed = elapsed, rank = rank))

fun fakeMayorEntry(rank: Int = 1, athlete: ProfileViewBasic = fakeProfileBasic(), efforts: Int = 12): MayorEntry =
    MayorEntry(rank, athlete, efforts, reachedOn = "2026-05-20")

/** A trail or road (segments/segment-paths) published by the importer, along [Fixed.POLYLINE]. */
fun fakePath(
    rkey: String = "anns",
    name: String = "Ann's",
    kind: String = PathKind.TRAIL,
    lengthMeters: Long = 8_690,
    sports: List<String> = listOf(SegmentSportGroup.RIDE),
    groupCount: Int = 1,
    source: String = SegmentSource.OSM,
): PathView =
    PathView(
        uri = Fixed.uri(Did("did:plc:importer"), Nsid.FEED_PATH, rkey),
        cid = Fixed.cid(rkey),
        kind = kind,
        name = name,
        lines = listOf(Fixed.POLYLINE),
        lengthMeters = lengthMeters,
        source = source,
        license = if (source == SegmentSource.OSM) "ODbL-1.0" else "CC0-1.0",
        sports = sports,
        groupCount = groupCount,
    )
