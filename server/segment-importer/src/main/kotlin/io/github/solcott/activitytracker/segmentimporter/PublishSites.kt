package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSites
import io.github.solcott.activitytracker.lexicon.app.DiveSitePosition
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import java.io.File
import kotlin.time.Clock
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.json.JsonElement

/**
 * Publishes a sites file's [sites] (dive-sites/site-import): each one `importer.checkDiveSites` finds no live site within 50 m of becomes a
 * `feed.diveSite` in the service account's repo, keyed by its id, through [Clients.writer]. Each page of [SegmentCandidates.MAX_BATCH] is
 * checked in one call. A key the repo already holds is never written again, so a published site is never doubled, moved or renamed, and
 * nothing is ever deleted; the importer's own live site is the duplicate the check finds for it, which counts as already published. Stops
 * at the first failed call, keeping what it did; running again carries on.
 */
internal suspend fun publishSites(clients: Clients, sites: List<SiteEntry>, clock: Clock = Clock.System): PublishResult =
    publishPages(clients, Nsid.FEED_DIVE_SITE, sites.chunked(SegmentCandidates.MAX_BATCH).asFlow(), { checkSites(clients, it) }) {
        siteRecord(it, clock)
    }

/**
 * Each of [page]'s sites with the live sites it duplicates (the importer's own for it when already published); one call for the whole page.
 */
private suspend fun checkSites(clients: Clients, page: List<SiteEntry>): List<Checked<SiteEntry>> {
    val checks =
        clients
            .asImporter {
                clients.appView.procedure(CheckDiveSites.method, CheckDiveSites.Input(page.map { DiveSitePosition(it.id, it.lat, it.lon) }))
            }
            .sites
            .associateBy { it.id }
    // Sites aren't staged, so a site the check leaves out duplicates nothing and is published.
    return page.map { Checked(it.id, it, checks[it.id]?.duplicates.orEmpty()) }
}

/** [site] as a `feed.diveSite` record created now. */
private fun siteRecord(site: SiteEntry, clock: Clock): JsonElement =
    AtJson.encodeToJsonElement(
        DiveSiteRecord.serializer(),
        DiveSiteRecord(site.name, site.lat, site.lon, site.radius, site.source, site.license, clock.now()),
    )

/** Publishes the sites in [file], printing what happened; throws what stopped it, after the summary. */
internal suspend fun publishSitesFile(clients: Clients, file: File) {
    val sites = file.bufferedReader().useLines { readSites(it) }
    publishSites(clients, sites.sites).report(sites.region, "dive sites", liveWhat = "site", countGone = false)
}
