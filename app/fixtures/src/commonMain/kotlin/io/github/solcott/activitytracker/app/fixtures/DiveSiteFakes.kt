// Builders take every field a test may change, each with a default, so a test names only what it cares about.
@file:Suppress("LongParameterList")

package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.DiveSiteMonth
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewBasic
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentSource

fun fakeDiveSiteRecord(
    name: String = "Blue Hole",
    lat: Int = 274_920_000,
    lng: Int = 343_850_000,
    radius: Int = DiveSiteRecord.DEFAULT_RADIUS,
    source: String = SegmentSource.ATHLETE,
    license: String = "CC0-1.0",
): DiveSiteRecord = DiveSiteRecord(name, lat, lng, radius, source, license, Fixed.CREATED_AT)

fun fakeDiveSite(
    rkey: String = "3ksite1",
    creator: ProfileViewBasic = fakeProfileBasic(),
    record: DiveSiteRecord = fakeDiveSiteRecord(),
    status: String? = null,
    name: String? = null,
    viewer: DiveSiteViewerState? = DiveSiteViewerState(),
): DiveSiteView =
    DiveSiteView(
        uri = Fixed.uri(creator.did, Nsid.FEED_DIVE_SITE, rkey),
        cid = Fixed.cid(rkey),
        creator = creator,
        record = record,
        status = status,
        name = name,
        viewer = viewer,
    )

fun fakeDiveSiteBasic(site: DiveSiteView = fakeDiveSite()): DiveSiteViewBasic = DiveSiteViewBasic(site.uri, site.currentName, site.status)

fun fakeRegularEntry(rank: Int = 1, athlete: ProfileViewBasic = fakeProfileBasic(), visits: Int = 12): DiveSiteRegularEntry =
    DiveSiteRegularEntry(rank, athlete, visits, "2026-05-20")

/** Conditions over 42 dives in two months: 24 to 29 °C, 25 m visibility, 18 m median and 31 m greatest depth. */
fun fakeDiveSiteConditions(): DiveSiteConditions =
    DiveSiteConditions(
        dives = 42,
        minTemperature = 240,
        medianTemperature = 265,
        maxTemperature = 290,
        medianVisibility = 2_500,
        medianMaxDepth = 1_800,
        maxDepth = 3_100,
        months =
            listOf(
                DiveSiteMonth(month = 5, dives = 20, medianTemperature = 250, medianVisibility = 2_000),
                DiveSiteMonth(month = 8, dives = 22, medianTemperature = 280, medianVisibility = 3_000),
            ),
    )
