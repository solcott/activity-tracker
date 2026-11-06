@file:Suppress("TooManyFunctions") // The dive-site routes, and the filters and views they share.

package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.DiveSiteVisits
import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.excludedFromLeaderboards
import io.github.solcott.activitytracker.appview.db.hidesFromLeaderboards
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.DAILY_DIVE_SITE_LIMIT
import io.github.solcott.activitytracker.appview.index.diveSiteDuplicatesOf
import io.github.solcott.activitytracker.appview.index.diveSitesCreatedSince
import io.github.solcott.activitytracker.appview.index.pointE7
import io.github.solcott.activitytracker.appview.index.resolveHandoff
import io.github.solcott.activitytracker.appview.index.validPositionE7
import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.appview.segments.mayPropose
import io.github.solcott.activitytracker.appview.segments.ridersAmong
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSite
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSites
import io.github.solcott.activitytracker.lexicon.app.DiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.DiveSiteMonth
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewBasic
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewerState
import io.github.solcott.activitytracker.lexicon.app.GetDiveSite
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteRegulars
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSites
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateCheck
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Dive sites (dive-sites/site-records) and what the AppView derives from the visits to them: conditions (dive-sites/site-conditions) and
 * Regulars (dive-sites/site-regulars). A site is public, but an account that isn't served, or a taken-down site record, hides it
 * ([diveSiteServed]). A visit is served only to viewers admitted to its stored visibility and to its activity's row ([visitsVisibleTo]),
 * and a visit's conditions only to those also admitted to its Dive tier, so nothing here reveals a dive the viewer couldn't otherwise see.
 */
@Suppress("LongParameterList") // Its config.
class DiveSiteViews(
    private val db: Database,
    private val avatars: AvatarUrls,
    private val clock: Clock = Clock.System,
    /** The segment importer's account, the only caller of [checkImported] and whose allowance is always whole. */
    private val segmentImporter: Did? = null,
    private val names: SegmentNameRules = SegmentNameRules(),
) {
    /** A site whatever its status, with its names and the viewer's naming flags. `NotFound` when it isn't served. */
    suspend fun site(viewer: Did?, params: GetDiveSite.Params): GetDiveSite.Output = db.tx {
        // A handed-off site's old URI serves its successor (dive-sites/site-handoffs).
        val uri = params.uri.value.takeIf(::siteIndexed) ?: resolveHandoff(params.uri.value, ::siteIndexed) ?: params.uri.value
        val row = DiveSites.selectAll().where { (DiveSites.uri eq uri) and diveSiteServed(viewer) }.firstOrNull() ?: throw notFound()
        val state = viewer?.let {
            DiveSiteViewerState(
                canProposeName = mayPropose(uri, it.value, clock.now().toEpochMilliseconds(), names),
                canVoteOnNames = ridersAmong(uri, listOf(it.value)).isNotEmpty(),
            )
        }
        GetDiveSite.Output(
            diveSiteViews(listOf(row), avatars)
                .single()
                .copy(nameHistory = nameHistory(viewer, uri, avatars), nameProposals = nameProposals(viewer, uri, avatars), viewer = state)
        )
    }

    /**
     * Sites whose centre lies in a box of at most [SearchSegments.MAX_SPAN_E7] a side, oldest indexed first: active ones, and proposed ones
     * the viewer created or dived.
     */
    suspend fun search(viewer: Did?, params: SearchDiveSites.Params): SearchDiveSites.Output {
        requireBox(params.minLat, params.minLng, params.maxLat, params.maxLng)
        val limit = Paging.clamp(params.limit)
        return db.tx {
            val rows =
                DiveSites.selectAll()
                    .where {
                        diveSiteServed(viewer) and
                            unhidden(viewer, DiveSites.authorDid, DiveSites.uri) and
                            (DiveSites.latE7 greaterEq params.minLat) and
                            (DiveSites.latE7 lessEq params.maxLat) and
                            (DiveSites.lngE7 greaterEq params.minLng) and
                            (DiveSites.lngE7 lessEq params.maxLng) and
                            listedTo(viewer) and
                            keysetAfter(DiveSites.indexedAt, DiveSites.uri, params.cursor)
                    }
                    .orderBy(DiveSites.indexedAt to SortOrder.ASC, DiveSites.uri to SortOrder.ASC)
                    .limit(limit)
                    .toList()
            val next = rows.takeIf { it.size == limit }?.last()?.let { cursorOf(it[DiveSites.indexedAt], it[DiveSites.uri]) }
            SearchDiveSites.Output(diveSiteViews(rows, avatars), next)
        }
    }

    /**
     * [viewer]'s check of a centre before publishing it as a site: the live sites it would duplicate, earliest first, and how many more new
     * sites they may create today (always all of them for the segment importer). `InvalidRequest` for a position off the globe.
     */
    suspend fun check(viewer: Did, input: CheckDiveSite.Input): CheckDiveSite.Output {
        if (!validPositionE7(input.lat, input.lng)) invalidRequest("The position is off the globe")
        val now = clock.now().toEpochMilliseconds()
        return db.tx {
            val uris = diveSiteDuplicatesOf(pointE7(input.lat, input.lng))
            val rows = servedSites(viewer, uris)
            val created = if (viewer == segmentImporter) 0 else diveSitesCreatedSince(viewer, now)
            val remaining = (DAILY_DIVE_SITE_LIMIT - created).coerceAtLeast(0).toInt()
            CheckDiveSite.Output(diveSiteViews(uris.mapNotNull { rows[it] }, avatars), remaining)
        }
    }

    /**
     * The segment importer's check of up to [SegmentCandidates.MAX_BATCH] extracted sites (dive-sites/site-import), in the order given: the
     * live sites served to it that each duplicates. Writes nothing. `NotImporter` for anyone else.
     */
    suspend fun checkImported(caller: Did, input: CheckDiveSites.Input): CheckDiveSites.Output {
        if (segmentImporter == null || caller != segmentImporter)
            throw XrpcError(HttpStatusCode.Forbidden.value, SegmentCandidates.NOT_IMPORTER, "Only the segment importer may check sites")
        if (input.sites.size > SegmentCandidates.MAX_BATCH)
            invalidRequest("At most ${SegmentCandidates.MAX_BATCH} sites per call, not ${input.sites.size}")
        for (site in input.sites) {
            if (!SegmentCandidates.ID_PATTERN.matches(site.id)) invalidRequest("An id must be 1 to 64 of a-z, 0-9 and -")
            if (!validPositionE7(site.lat, site.lng)) invalidRequest("Site ${site.id} is off the globe")
        }
        return db.tx {
            CheckDiveSites.Output(
                input.sites.map { site ->
                    val uris = diveSiteDuplicatesOf(pointE7(site.lat, site.lng))
                    val served = servedSites(caller, uris).keys
                    SegmentCandidateCheck(site.id, uris.filter { it in served }.map(::AtUri))
                }
            )
        }
    }

    /**
     * A site's conditions over at most [GetDiveSiteConditions.MAX_DIVES] of the newest visits [viewer] may see whose Dive tier admits them
     * too; none when there are no such visits. `NotFound` when the site isn't served.
     */
    suspend fun conditions(viewer: Did?, params: GetDiveSiteConditions.Params): GetDiveSiteConditions.Output = db.tx {
        requireServedSite(viewer, params.site.value)
        val rows =
            visitsWithActivities()
                .select(DiveSiteVisits.startedOn, DiveSiteVisits.minTemperature, DiveSiteVisits.visibilityCm, DiveSiteVisits.maxDepthCm)
                .where {
                    (DiveSiteVisits.siteUri eq params.site.value) and
                        visitsVisibleTo(viewer) and
                        visibleTo(viewer, DiveSiteVisits.diveTier, DiveSiteVisits.authorDid, DiveSiteVisits.activityUri, null)
                }
                .orderBy(DiveSiteVisits.startedOn to SortOrder.DESC, DiveSiteVisits.activityUri to SortOrder.DESC)
                .limit(GetDiveSiteConditions.MAX_DIVES)
                .map {
                    DiveConditions(
                        it[DiveSiteVisits.startedOn],
                        it[DiveSiteVisits.minTemperature],
                        it[DiveSiteVisits.visibilityCm],
                        it[DiveSiteVisits.maxDepthCm],
                    )
                }
        GetDiveSiteConditions.Output(conditionsOf(rows))
    }

    /**
     * A site's Regulars board: athletes by how many of their visits to it [viewer] may see in the window, most first, then by the date each
     * count was reached, earliest first; leaving out athletes who hide from leaderboards and activities excluded on their segment track.
     * Only an active site has entries. Pages by a `HAVING` keyset on (count, date, athlete) and ranks over the whole board.
     */
    suspend fun regulars(viewer: Did?, params: GetDiveSiteRegulars.Params): GetDiveSiteRegulars.Output {
        val (since, until) = regularsWindow(params.period)
        val following = followingScope(viewer, params.scope)
        return db.tx {
            if (requireServedSite(viewer, params.site.value) != SegmentStatus.ACTIVE) return@tx GetDiveSiteRegulars.Output(emptyList())
            val board = regularsBoard(viewer, params.site.value, since, until, following)
            val limit = Paging.clamp(params.limit)
            val after =
                params.cursor
                    ?.let { cursorParts(it, REGULAR_CURSOR_PARTS) }
                    ?.let { (count, reached, did) ->
                        count.toLongOrNull()?.let { visits ->
                            (VISITS less visits) or
                                ((VISITS eq visits) and (REACHED greater reached)) or
                                ((VISITS eq visits) and (REACHED eq reached) and (DiveSiteVisits.authorDid greater did))
                        }
                    } ?: Op.TRUE
            val page =
                counts(board, after)
                    .orderBy(VISITS to SortOrder.DESC, REACHED to SortOrder.ASC, DiveSiteVisits.authorDid to SortOrder.ASC)
                    .limit(limit)
                    .mapNotNull(::regularRow)
            val entries = regularEntries(board, page)
            val next = page.takeIf { it.size == limit }?.last()?.let { (did, visits, reached) -> cursorOf(visits, reached, did) }
            val own = viewer?.let { me ->
                counts(board and (DiveSiteVisits.authorDid eq me.value), Op.TRUE).firstOrNull()?.let(::regularRow)
            }
            GetDiveSiteRegulars.Output(entries, own?.let { regularEntries(board, listOf(it)).singleOrNull() }, next)
        }
    }

    /** [period]'s first local date and the first after it (each null when unbounded); `InvalidRequest` when unknown. */
    private fun regularsWindow(period: String?): Pair<String?, String?> {
        val chosen = period ?: RegularsPeriod.ALL_TIME
        if (chosen !in RegularsPeriod.all) invalidRequest("Unknown period $chosen")
        return mayorWindow(chosen, today())
    }

    /** The visits a Regulars board counts. */
    private fun regularsBoard(viewer: Did?, site: String, since: String?, until: String?, following: Boolean): Op<Boolean> {
        return (DiveSiteVisits.siteUri eq site) and
            visitsVisibleTo(viewer) and
            not(hidesFromLeaderboards(DiveSiteVisits.authorDid)) and
            not(excludedFromLeaderboards()) and
            (since?.let { DiveSiteVisits.startedOn greaterEq it } ?: Op.TRUE) and
            (until?.let { DiveSiteVisits.startedOn less it } ?: Op.TRUE) and
            followedOrOwn(DiveSiteVisits.authorDid, viewer, following)
    }

    private fun counts(board: Op<Boolean>, having: Op<Boolean>): Query =
        visitsWithActivities()
            .join(SegmentTracks, JoinType.INNER, DiveSiteVisits.trackUri, SegmentTracks.uri)
            .select(DiveSiteVisits.authorDid, VISITS, REACHED)
            .where { board }
            .groupBy(DiveSiteVisits.authorDid)
            .having { having }

    private fun regularRow(row: ResultRow): Triple<String, Long, String>? =
        row[REACHED]?.let { Triple(row[DiveSiteVisits.authorDid], row[VISITS], it) }

    /** Entries for [page], ranked over the whole board: equal counts reached on one date share a rank, and the next skips. */
    private fun JdbcTransaction.regularEntries(board: Op<Boolean>, page: List<Triple<String, Long, String>>): List<DiveSiteRegularEntry> {
        val athletes = basicProfiles(page.map { it.first }, avatars)
        return page.map { (did, visits, reached) ->
            val ahead = counts(board, (VISITS greater visits) or ((VISITS eq visits) and (REACHED less reached))).count()
            DiveSiteRegularEntry(
                rank = ahead.toInt() + 1,
                athlete = athletes[did] ?: ProfileViewBasic(Did(did), Handle(UNKNOWN_HANDLE)),
                visits = visits.toInt(),
                reachedOn = reached,
            )
        }
    }

    /** Sites search lists to [viewer]: active ones, and proposed ones they created or dived. */
    private fun listedTo(viewer: Did?): Op<Boolean> {
        val active = DiveSites.status eq SegmentStatus.ACTIVE
        if (viewer == null) return active
        val dived =
            exists(
                DiveSiteVisits.select(DiveSiteVisits.activityUri).where {
                    (DiveSiteVisits.siteUri eq DiveSites.uri) and (DiveSiteVisits.authorDid eq viewer.value)
                }
            )
        return active or ((DiveSites.status eq SegmentStatus.PROPOSED) and ((DiveSites.authorDid eq viewer.value) or dived))
    }

    private fun JdbcTransaction.requireServedSite(viewer: Did?, site: String): String =
        DiveSites.select(DiveSites.status).where { (DiveSites.uri eq site) and diveSiteServed(viewer) }.firstOrNull()?.get(DiveSites.status)
            ?: throw notFound()

    private fun JdbcTransaction.servedSites(viewer: Did?, uris: List<String>): Map<String, ResultRow> =
        if (uris.isEmpty()) emptyMap()
        else DiveSites.selectAll().where { (DiveSites.uri inList uris) and diveSiteServed(viewer) }.associateBy { it[DiveSites.uri] }

    private fun today(): LocalDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds()), ZoneOffset.UTC)

    private fun requireBox(minLat: Int, minLng: Int, maxLat: Int, maxLng: Int) {
        if (!validPositionE7(minLat, minLng) || !validPositionE7(maxLat, maxLng)) invalidRequest("The box is outside the globe")
        if (minLat > maxLat || minLng > maxLng) invalidRequest("The box's minimum is above its maximum")
        if (abs(maxLat - minLat) > SearchSegments.MAX_SPAN_E7 || abs(maxLng - minLng) > SearchSegments.MAX_SPAN_E7)
            invalidRequest("The box is wider than one degree")
    }

    private companion object {
        val VISITS = DiveSiteVisits.activityUri.count()
        val REACHED = DiveSiteVisits.startedOn.max()
        const val REGULAR_CURSOR_PARTS = 3
    }
}

/** One visit's conditions, as [conditionsOf] reads them. */
internal data class DiveConditions(val startedOn: String, val minTemperature: Long?, val visibility: Long?, val maxDepth: Long?)

/**
 * The conditions over [dives]: the lowest, median and highest temperature, the median visibility, the median and greatest depth, each from
 * the dives that record it, and the median temperature and visibility of each calendar month with dives. Null without dives.
 */
internal fun conditionsOf(dives: List<DiveConditions>): DiveSiteConditions? {
    if (dives.isEmpty()) return null
    val temperatures = dives.mapNotNull { it.minTemperature }
    val depths = dives.mapNotNull { it.maxDepth }
    val months =
        dives
            .groupBy { it.startedOn.substring(MONTH_START, MONTH_END).toInt() }
            .toSortedMap()
            .map { (month, its) ->
                DiveSiteMonth(
                    month = month,
                    dives = its.size,
                    medianTemperature = median(its.mapNotNull { it.minTemperature }),
                    medianVisibility = median(its.mapNotNull { it.visibility }),
                )
            }
    return DiveSiteConditions(
        dives = dives.size,
        minTemperature = temperatures.minOrNull(),
        medianTemperature = median(temperatures),
        maxTemperature = temperatures.maxOrNull(),
        medianVisibility = median(dives.mapNotNull { it.visibility }),
        medianMaxDepth = median(depths),
        maxDepth = depths.maxOrNull(),
        months = months,
    )
}

/** The median of [values], the mean of the middle two rounded down for an even count; null when empty. */
internal fun median(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else Math.floorDiv(sorted[mid - 1] + sorted[mid], 2L)
}

private const val MONTH_START = 5
private const val MONTH_END = 7

/** Sites served to [viewer]: not of an account that isn't served, nor taken down. */
internal fun diveSiteServed(viewer: Did?): Op<Boolean> = served(viewer, DiveSites.authorDid, DiveSites.uri, DiveSites.cid)

/** Visits joined to their activity. */
internal fun visitsWithActivities() = DiveSiteVisits.join(Activities, JoinType.INNER, DiveSiteVisits.activityUri, Activities.uri)

/**
 * Visits over [visitsWithActivities] that [viewer] may see: admitted to the visit's tier and to its activity's row, on a site served to
 * them.
 */
internal fun visitsVisibleTo(viewer: Did?): Op<Boolean> =
    visibleTo(viewer, DiveSiteVisits.visibility, DiveSiteVisits.authorDid, DiveSiteVisits.activityUri, null) and
        visibleTo(viewer) and
        exists(DiveSites.select(DiveSites.uri).where { (DiveSites.uri eq DiveSiteVisits.siteUri) and diveSiteServed(viewer) })

/** Each of [activities]' visited site that [viewer] may see the visit to, as a brief view (`activityView.diveSite`). */
internal fun JdbcTransaction.diveSitesOf(activities: List<String>, viewer: Did?): Map<String, DiveSiteViewBasic> {
    if (activities.isEmpty()) return emptyMap()
    return visitsWithActivities()
        .join(DiveSites, JoinType.INNER, DiveSiteVisits.siteUri, DiveSites.uri)
        .select(DiveSiteVisits.activityUri, DiveSites.uri, DiveSites.currentName, DiveSites.status)
        .where { (DiveSiteVisits.activityUri inList activities) and visitsVisibleTo(viewer) }
        .associate {
            it[DiveSiteVisits.activityUri] to DiveSiteViewBasic(AtUri(it[DiveSites.uri]), it[DiveSites.currentName], it[DiveSites.status])
        }
}

/** A page of the activity rows of [site]'s visits [viewer] may see, newest first, keyed on (started_at, uri). */
internal fun JdbcTransaction.diveSiteDiveRows(viewer: Did?, site: String, limit: Int, cursor: String?): List<ResultRow> =
    visitsWithActivities()
        .selectAll()
        .where {
            (DiveSiteVisits.siteUri eq site) and visitsVisibleTo(viewer) and keysetBefore(Activities.startedAt, Activities.uri, cursor)
        }
        .orderBy(Activities.startedAt to SortOrder.DESC, Activities.uri to SortOrder.DESC)
        .limit(limit)
        .toList()

/** Whether [site] is served to [viewer]; `NotFound` otherwise. */
internal fun JdbcTransaction.requireDiveSite(viewer: Did?, site: String) {
    if (DiveSites.select(DiveSites.uri).where { (DiveSites.uri eq site) and diveSiteServed(viewer) }.empty()) throw notFound()
}

internal fun JdbcTransaction.diveSiteViews(rows: List<ResultRow>, avatars: AvatarUrls): List<DiveSiteView> {
    val creators = basicProfiles(rows.map { it[DiveSites.authorDid] }, avatars)
    return rows.map {
        val author = it[DiveSites.authorDid]
        DiveSiteView(
            uri = AtUri(it[DiveSites.uri]),
            cid = Cid(it[DiveSites.cid]),
            creator = creators[author] ?: ProfileViewBasic(Did(author), Handle(UNKNOWN_HANDLE)),
            record = AtJson.decodeFromString(DiveSiteRecord.serializer(), it[DiveSites.record]),
            status = it[DiveSites.status],
            duplicateOf = it[DiveSites.duplicateOf]?.let(::AtUri),
            name = it[DiveSites.currentName],
        )
    }
}

private fun notFound() = XrpcError(HttpStatusCode.BadRequest.value, "NotFound", "Dive site not found")

private fun invalidRequest(message: String): Nothing = throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", message)

/** Whether a dive site is indexed at [uri], whatever its status or labels. */
private fun JdbcTransaction.siteIndexed(uri: String): Boolean = DiveSites.select(DiveSites.uri).where { DiveSites.uri eq uri }.any()
