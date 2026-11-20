@file:Suppress("MatchingDeclarationName") // The publish-paths command; PublishPathsResult is only what it returns.

package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLines
import io.github.solcott.activitytracker.lexicon.app.PathRecord
import io.github.solcott.activitytracker.lexicon.app.PathSection
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.PathCandidate
import io.github.solcott.activitytracker.location.PathOverlap
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.toE7
import java.io.File
import kotlin.time.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a `publish-paths` run did: paths published new ([published]), rewritten with a changed name, line, sports or sections or newly
 * listed by the region ([refreshed]), deleted as no longer listed by any region's file ([removed]), and kept for the other regions listing
 * them but no longer this one ([released]), and how many of the region's segments lie on none of its paths ([unpathed]); [stoppedBy] is the
 * error that ended the run early, if one did.
 */
internal data class PublishPathsResult(
    val published: List<String> = emptyList(),
    val refreshed: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val released: List<String> = emptyList(),
    val unpathed: Int = 0,
    val stoppedBy: Exception? = null,
)

/** A page of the repo's paths; listRecords allows up to 100. */
private const val PAGE = 100

/** A tile a little under the AppView's 1 degree limit, in 1e-7 degrees. */
private const val TILE_E7 = SearchSegments.MAX_SPAN_E7 - 1_000

/** `feed.path#regions`. */
private const val REGIONS = "regions"

/** How `feed.path#sections` reads. */
private val SECTIONS = ListSerializer(PathSection.serializer())

/**
 * Publishes the [paths] of [region]'s file that the region's segments lie on (segments/path-import): asks the AppView, tile by tile over
 * the paths' box, for every live segment's line and sport group, and publishes as a `feed.path` each path one of them lies on
 * ([PathOverlap], the AppView's own test, among the file's paths) that the repo doesn't hold yet. Each record names the regions whose files
 * list it (`regions`), so ownership lives with the record: a path the repo holds is rewritten, under its key, when its name, line or sports
 * or sections changed or [region] isn't among its regions yet (every one with `rewrite`, so an AppView that missed them indexes them
 * again), and one [region] listed that its file no longer holds loses [region], deleted once no region is left. A path published before
 * records named their regions is never deleted. Stops at the first failed call, keeping what it did; running again carries on.
 */
internal suspend fun publishPaths(
    clients: Clients,
    paths: List<PathEntry>,
    region: String,
    clock: Clock = Clock.System,
    rewrite: Boolean = false,
): PublishPathsResult {
    val published = mutableListOf<String>()
    val refreshed = mutableListOf<String>()
    val removed = mutableListOf<String>()
    val released = mutableListOf<String>()
    var unpathed = 0
    val stoppedBy = stopOnError {
        val repo = clients.session.did()
        val held = heldPaths(clients, repo.value)
        val inFile = paths.mapTo(mutableSetOf()) { it.id }

        val gone = held.filter { (id, path) -> id !in inFile && region in path.regions.orEmpty() }
        val (orphaned, shared) = gone.entries.partition { (_, path) -> path.regions.orEmpty().all { it == region } }
        clients.writer.delete(repo, Nsid.FEED_PATH, orphaned.map { it.key })
        removed += orphaned.map { it.key }
        clients.writer.update(
            repo,
            shared.map { (id, path) -> Draft(Nsid.FEED_PATH, id, path.withRegions(path.regions.orEmpty() - region)) },
        )
        released += shared.map { it.key }

        val needs = pathsNeeded(clients, paths, repo.value)
        unpathed = needs.unpathed
        val publishable = paths.map { it.withSectionsOf(needs.importerKeys) }
        val changed = publishable.mapNotNull { path ->
            val old = held[path.id] ?: return@mapNotNull null
            val stale = old.name != path.name || old.lines != path.lines || old.sports != path.sports || old.sections != path.sections
            val unlisted = region !in old.regions.orEmpty()
            if (!rewrite && !stale && !unlisted) return@mapNotNull null
            Draft(Nsid.FEED_PATH, path.id, pathRecord(path, clock, regionsWith(old.regions, region)))
        }
        clients.writer.update(repo, changed)
        refreshed += changed.map { it.rkey }

        val drafts =
            publishable
                .filter { it.id in needs.paths && it.id !in held }
                .map { Draft(Nsid.FEED_PATH, it.id, pathRecord(it, clock, listOf(region))) }
        clients.writer.create(repo, drafts) { draft, created -> if (created) published += draft.rkey }
    }
    return PublishPathsResult(published, refreshed, removed, released, unpathed, stoppedBy)
}

/** [regions] with [region] added, sorted, at most [PathRecord.MAX_REGIONS] (the rest dropped, never [region]). */
private fun regionsWith(regions: List<String>?, region: String): List<String> =
    (listOf(region) + regions.orEmpty().filter { it != region }.sorted()).take(PathRecord.MAX_REGIONS).sorted()

/**
 * The ids of the [paths] the region's live segments lie on, how many of those segments lie on none ([unpathed]), and the record keys of
 * those segments in the importer's own repo ([importerKeys]).
 */
private class PathNeeds(val paths: Set<String>, val unpathed: Int, val importerKeys: Set<String>)

/**
 * This path with only the sections naming segments the importer has published ([published], their record keys), listing only those
 * (segments/path-import); none when no section names one.
 */
internal fun PathEntry.withSectionsOf(published: Set<String>): PathEntry =
    copy(
        sections =
            sections
                ?.map { section -> section.copy(segments = section.segments.filter { it in published }) }
                ?.filter { it.segments.isNotEmpty() }
                ?.ifEmpty { null },
    )

/**
 * Asks the AppView, tile by tile over [paths]' box, for every live segment's line and sport group, and tests each against [paths], the
 * segments in the importer's own [repo] by the stricter test the AppView holds them to.
 */
private suspend fun pathsNeeded(clients: Clients, paths: List<PathEntry>, repo: String): PathNeeds {
    val index = PathIndex(paths)
    val needed = linkedSetOf<String>()
    val importerKeys = mutableSetOf<String>()
    var unpathed = 0
    // Tiles share their edges, so a segment crossing one comes back from each tile it touches: each is tested once.
    val seen = mutableSetOf<String>()
    for (tile in tilesOf(paths)) {
        var cursor: String? = null
        do {
            val page = clients.asImporter { clients.appView.query(GetSegmentLines.method, tile.copy(cursor = cursor)) }
            for (segment in page.segments.filter { seen.add(it.uri.value) }) {
                if (segment.uri.authority == repo) segment.uri.rkey?.let(importerKeys::add)
                val line = SegmentGeometry.decodePolyline(segment.line) ?: continue
                val on = index.pathsUnder(line, segment.sport, imported = segment.uri.authority == repo)
                if (on.isEmpty()) unpathed++ else needed += on
            }
            cursor = page.cursor?.takeIf { page.segments.isNotEmpty() }
        } while (cursor != null)
    }
    return PathNeeds(needed, unpathed, importerKeys)
}

/** A published path's name, line pieces, sport groups, regions and sections, and its [record] as the repo holds it. */
private data class HeldPath(
    val name: String?,
    val lines: List<String>,
    val sports: List<String>?,
    val regions: List<String>?,
    val sections: List<PathSection>?,
    val record: JsonObject,
) {
    /** The record as held, listing only [regions]. */
    fun withRegions(regions: List<String>): JsonObject = JsonObject(record + (REGIONS to JsonArray(regions.map(::JsonPrimitive))))
}

/** The service account's published paths, by key. */
private suspend fun heldPaths(clients: Clients, repo: String): Map<String, HeldPath> {
    val held = mutableMapOf<String, HeldPath>()
    var cursor: String? = null
    do {
        val page = clients.pds.query(ListRecords.method, ListRecords.Params(repo, Nsid.FEED_PATH, PAGE, cursor))
        for (record in page.records) {
            val rkey = record.uri.rkey ?: continue
            val name = (record.value["name"] as? JsonPrimitive)?.content
            fun strings(key: String) = runCatching { record.value.getValue(key).jsonArray.map { it.jsonPrimitive.content } }.getOrNull()
            val sections =
                record.value["sections"]?.let { runCatching { AtJson.decodeFromJsonElement(SECTIONS, it) }.getOrNull() }?.ifEmpty { null }
            held[rkey] = HeldPath(name, strings("lines").orEmpty(), strings("sports"), strings(REGIONS), sections, record.value)
        }
        cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
    } while (cursor != null)
    return held
}

/** [path] as the `feed.path` record published for it, listed by [regions]. */
internal fun pathRecord(path: PathEntry, clock: Clock, regions: List<String>? = null): JsonObject =
    AtJson.encodeToJsonElement(
            PathRecord.serializer(),
            PathRecord(
                name = path.name,
                kind = path.kind,
                lines = path.lines,
                source = path.source,
                license = path.license,
                createdAt = clock.now(),
                attribution = path.attribution,
                sports = path.sports,
                regions = regions,
                sections = path.sections,
            ),
        )
        .let { it as JsonObject }

/** The tiles covering every path in [paths], each a little under 1 degree a side, as `getSegmentLines` boxes. */
internal fun tilesOf(paths: List<PathEntry>): List<GetSegmentLines.Params> {
    val points = paths.flatMap { path -> path.lines.flatMap { SegmentGeometry.decodePolyline(it).orEmpty() } }
    if (points.isEmpty()) return emptyList()
    val minLat = points.minOf { it.lat }.toE7()
    val maxLat = points.maxOf { it.lat }.toE7()
    val minLng = points.minOf { it.lng }.toE7()
    val maxLng = points.maxOf { it.lng }.toE7()
    return buildList {
        for (south in minLat..maxLat step TILE_E7) {
            for (west in minLng..maxLng step TILE_E7) {
                add(GetSegmentLines.Params(south, west, minOf(south + TILE_E7, maxLat), minOf(west + TILE_E7, maxLng)))
            }
        }
    }
}

/** [paths] by the cells their pieces pass through, to find the ones a segment may lie on without testing every path. */
internal class PathIndex(paths: List<PathEntry>) {
    private val candidates: Map<String, PathCandidate<String>> = paths.associate { path ->
        path.id to
            PathCandidate(
                path.id,
                path.lines.mapNotNull { SegmentGeometry.decodePolyline(it)?.takeIf { p -> p.size >= 2 } },
                path.sports.orEmpty().toSet(),
            )
    }

    private val byCell: Map<Long, List<String>> =
        candidates.values
            .flatMap { candidate -> candidate.pieces.flatMap { SegmentCells.cellsOf(it) }.distinct().map { it to candidate.key } }
            .groupBy({ it.first }, { it.second })

    /** The ids of the paths [line], of the sport group [sport] and [imported] or not, lies on, as the AppView decides it among them. */
    fun pathsUnder(line: List<GeoPoint>, sport: String?, imported: Boolean = false): Set<String> =
        PathOverlap.pathsUnder(
            line,
            sport,
            SegmentCells.cellsOf(line).flatMap { byCell[it].orEmpty() }.distinct().map(candidates::getValue),
            imported,
        )
}

/** Reads [path] and publishes the paths it holds that segments need, printing what happened; throws what stopped it, after the summary. */
internal suspend fun publishPathsFile(clients: Clients, path: File, rewrite: Boolean = false) {
    val file = path.bufferedReader().useLines { readPaths(it) }
    val result = publishPaths(clients, file.paths, file.region, rewrite = rewrite)
    println(
        "Published ${result.published.size} of ${file.region}'s ${file.paths.size} paths, refreshed ${result.refreshed.size}, " +
            "removed ${result.removed.size} and left ${result.released.size} to the other regions listing them; " +
            "${result.unpathed} segments there lie on none of them",
    )
    result.stoppedBy?.let { throw it }
}
