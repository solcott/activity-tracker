package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.db.DiveSiteVisits
import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.SegmentMatcher
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Shared setup for the dive-site tests: sites and dives laid out in meters east and north of the segment tests' origin. */
open class DiveSiteTestBase : SegmentTestBase() {
    protected fun siteUri(rkey: String, did: String = alice) = "at://$did/${Nsid.FEED_DIVE_SITE}/$rkey"

    @Suppress("LongParameterList")
    protected fun siteJson(
        east: Double = 0.0,
        north: Double = 0.0,
        radius: Int = 100,
        name: String = "Blue Hole",
        source: String = "athlete",
    ): String {
        val p = at(east, north)
        return """{"name":"$name","lat":${(p.lat * 1e7).roundToInt()},"lng":${(p.lng * 1e7).roundToInt()},"radius":$radius,""" +
            """"source":"$source","license":"CC0-1.0","createdAt":"2026-10-01T00:00:00Z"}"""
    }

    @Suppress("LongParameterList")
    protected fun site(
        rkey: String,
        east: Double = 0.0,
        north: Double = 0.0,
        radius: Int = 100,
        did: String = alice,
        name: String = "Blue Hole",
    ) = commit(did, Nsid.FEED_DIVE_SITE, rkey, siteJson(east, north, radius, name))

    /** [did]'s dive [rkey] at [tier], with [dive] as its summary's JSON fields (no summary when empty) and [extra] as the record's. */
    @Suppress("LongParameterList")
    protected fun dive(
        rkey: String,
        tier: String = Visibility.PUBLIC,
        did: String = alice,
        subSport: String? = null,
        dive: String = """"maxDepth":1800,"minTemperature":260,"visibility":1500""",
        extra: String = "",
        startedAt: String = "2026-08-05T09:00:00Z",
    ) =
        activity(
            rkey,
            tier,
            sport = "dive",
            subSport = subSport,
            did = did,
            extra = (if (dive.isEmpty()) "" else """"dive":{$dive},""") + extra,
            startedAt = startedAt,
        )

    /** [did]'s dive [rkey] with matching on and a segment track whose entry is [east], [north]: one surface fix. */
    @Suppress("LongParameterList")
    protected fun diveAt(
        rkey: String,
        east: Double = 0.0,
        north: Double = 0.0,
        did: String = alice,
        tier: String = Visibility.PUBLIC,
        extra: String = "",
        dive: String = """"maxDepth":1800,"minTemperature":260,"visibility":1500""",
        startedAt: String = "2026-08-05T09:00:00Z",
    ) {
        matching(true, did)
        dive(rkey, tier, did, dive = dive, extra = extra, startedAt = startedAt)
        track(rkey, listOf(listOf(east to north)), did)
    }

    protected fun siteStatus(uri: String): String? = runBlocking {
        db.tx { DiveSites.selectAll().where { DiveSites.uri eq uri }.firstOrNull()?.get(DiveSites.status) }
    }

    protected fun duplicateOf(uri: String): String? = runBlocking {
        db.tx { DiveSites.selectAll().where { DiveSites.uri eq uri }.firstOrNull()?.get(DiveSites.duplicateOf) }
    }

    protected fun setSiteStatus(uri: String, status: String) = runBlocking {
        db.tx { DiveSites.update({ DiveSites.uri eq uri }) { it[DiveSites.status] = status } }
    }

    protected data class Visit(val activity: String, val site: String, val visibility: String, val diveTier: String)

    protected fun visits(): List<Visit> = runBlocking {
        db.tx {
            DiveSiteVisits.selectAll().orderBy(DiveSiteVisits.activityUri to SortOrder.ASC).map {
                Visit(
                    it[DiveSiteVisits.activityUri],
                    it[DiveSiteVisits.siteUri],
                    it[DiveSiteVisits.visibility],
                    it[DiveSiteVisits.diveTier],
                )
            }
        }
    }

    /** A matcher whose clock is [ahead] of now, needing [athletes] distinct divers to promote a site. */
    protected fun matcherAt(ahead: Duration = Duration.ZERO, athletes: Int = SegmentMatcher.DEFAULT_ACTIVE_ATHLETES) =
        SegmentMatcher(
            db,
            indexer.segmentJobs,
            SpaceBlobs { _, _, cid -> blobs[cid]?.let { XrpcBytes(it, "application/json") } },
            clock =
                object : Clock {
                    override fun now() = Clock.System.now() + ahead
                },
            activeAthletes = athletes,
        )
}
