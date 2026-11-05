@file:Suppress("MatchingDeclarationName") // The publish-sites command; PublishSitesResult is only what it returns.

package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSites
import io.github.solcott.activitytracker.lexicon.app.DiveSitePosition
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateCheck
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import io.github.solcott.activitytracker.lexicon.atproto.CreateRecord
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** What a `publish-sites` run did; [stoppedBy] is the error that ended the run early, if one did. */
internal data class PublishSitesResult(
    val published: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val present: List<String> = emptyList(),
    val stoppedBy: Exception? = null,
)

/**
 * Publishes a sites file's [sites] (dive-sites/site-import): each one `importer.checkDiveSites` finds no live site within 50 m of becomes a
 * `feed.diveSite` in the service account's repo, keyed by its id. Each page of [SegmentCandidates.MAX_BATCH] is checked in one call. A key
 * the repo already holds is never written again, so a published site is never doubled, moved or renamed, and nothing is ever deleted; the
 * importer's own live site is the duplicate the check finds for it, which counts as already published. Stops at the first failed call,
 * keeping what it did; running again carries on.
 */
internal suspend fun publishSites(clients: Clients, sites: List<SiteEntry>, clock: Clock = Clock.System): PublishSitesResult {
    val published = mutableListOf<String>()
    val duplicates = mutableListOf<String>()
    val present = mutableListOf<String>()
    val stoppedBy =
        try {
            val repo = clients.session.did()
            for (page in sites.chunked(SegmentCandidates.MAX_BATCH)) {
                val checks = checkSites(clients, page)
                for (site in page) {
                    val found = checks[site.id]?.duplicates.orEmpty()
                    when {
                        AtUri.of(repo, Nsid.FEED_DIVE_SITE, site.id) in found -> present += site.id
                        found.isNotEmpty() -> duplicates += site.id
                        exists(clients, repo, site.id, Nsid.FEED_DIVE_SITE) -> present += site.id
                        createSite(clients, repo, site, clock) -> published += site.id
                        else -> present += site.id
                    }
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            e
        }
    return PublishSitesResult(published, duplicates, present, stoppedBy)
}

/** The live sites each of [page]'s sites duplicates, by id; one call for the whole page. */
private suspend fun checkSites(clients: Clients, page: List<SiteEntry>): Map<String, SegmentCandidateCheck> =
    clients
        .asImporter {
            clients.appView.procedure(CheckDiveSites.method, CheckDiveSites.Input(page.map { DiveSitePosition(it.id, it.lat, it.lon) }))
        }
        .sites
        .associateBy { it.id }

/**
 * Creates [site]'s record; false when a concurrent run created it first (`createRecord` refuses a key that exists, and a re-check finds
 * it).
 */
private suspend fun createSite(clients: Clients, repo: Did, site: SiteEntry, clock: Clock): Boolean {
    val record = DiveSiteRecord(site.name, site.lat, site.lon, site.radius, site.source, site.license, clock.now())
    return try {
        clients.pds.procedure(
            CreateRecord.method,
            CreateRecord.Input(repo, Nsid.FEED_DIVE_SITE, AtJson.encodeToJsonElement(DiveSiteRecord.serializer(), record), site.id, false),
        )
        true
    } catch (e: XrpcException) {
        if (e.status != CLIENT_ERROR || !exists(clients, repo, site.id, Nsid.FEED_DIVE_SITE)) throw e
        false
    }
}

/** Publishes the sites in [file], printing what happened; throws what stopped it, after the summary. */
internal suspend fun publishSitesFile(clients: Clients, file: File) {
    val sites = file.bufferedReader().useLines { readSites(it) }
    val result = publishSites(clients, sites.sites)
    for (id in result.duplicates) println("Duplicate of a live site, not published: $id")
    println(
        "Published ${result.published.size} dive sites for ${sites.region}; ${result.duplicates.size} duplicates, " +
            "${result.present.size} already published"
    )
    result.stoppedBy?.let { throw it }
}
