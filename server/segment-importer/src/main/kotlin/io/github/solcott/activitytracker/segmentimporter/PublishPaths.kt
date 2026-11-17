@file:Suppress("MatchingDeclarationName") // The publish-paths command; PublishPathsResult is only what it returns.

package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLines
import io.github.solcott.activitytracker.lexicon.app.PathRecord
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.PathCandidate
import io.github.solcott.activitytracker.location.PathOverlap
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a `publish-paths` run did: paths published new ([published]), rewritten with a changed name, line or sports ([refreshed]), and
 * deleted as gone from the region's file ([removed]), and how many of the region's segments lie on none of its paths ([unpathed]);
 * [stoppedBy] is the error that ended the run early, if one did.
 */
internal data class PublishPathsResult(
    val published: List<String> = emptyList(),
    val refreshed: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val unpathed: Int = 0,
    val stoppedBy: Exception? = null,
)

/** A page of the repo's paths; listRecords allows up to 100. */
private const val PAGE = 100

/** A tile a little under the AppView's 1 degree limit, in 1e-7 degrees. */
private const val TILE_E7 = SearchSegments.MAX_SPAN_E7 - 1_000

private const val E7 = 1e7

/**
 * Publishes the [paths] that the region's segments lie on (segments/path-import): asks the AppView, tile by tile over the paths' box, for
 * every live segment's line and sport group, and publishes as a `feed.path` each path one of them lies on ([PathOverlap], the AppView's own
 * test, among the file's paths) that the repo doesn't hold yet. A path the repo holds is rewritten, under its key, when its name, line or
 * sports changed (every one with `rewrite`, so an AppView that missed them indexes them again), and one lying wholly inside the file's area
 * that the file no longer holds is deleted. Stops at the first failed call, keeping what it did; running again carries on.
 */
internal suspend fun publishPaths(
    clients: Clients,
    paths: List<PathEntry>,
    clock: Clock = Clock.System,
    rewrite: Boolean = false,
): PublishPathsResult {
    val published = mutableListOf<String>()
    val refreshed = mutableListOf<String>()
    val removed = mutableListOf<String>()
    var unpathed = 0
    val stoppedBy = stopOnError {
        val repo = clients.session.did()
        val held = heldPaths(clients, repo.value)
        val inFile = paths.mapTo(mutableSetOf()) { it.id }
        val area = areaOf(paths)
        val stale = held.filter { (id, path) -> id !in inFile && area != null && path.pieces.all { piece -> piece.all(area::holds) } }.keys
        clients.writer.delete(repo, Nsid.FEED_PATH, stale.toList())
        removed += stale

        val changed = paths.filter { path ->
            held[path.id]?.let { rewrite || it.name != path.name || it.lines != path.lines || it.sports != path.sports } == true
        }
        clients.writer.update(repo, changed.map { Draft(Nsid.FEED_PATH, it.id, pathRecord(it, clock)) })
        refreshed += changed.map { it.id }

        val needs = pathsNeeded(clients, paths)
        unpathed = needs.unpathed
        val drafts = paths.filter { it.id in needs.paths && it.id !in held }.map { Draft(Nsid.FEED_PATH, it.id, pathRecord(it, clock)) }
        clients.writer.create(repo, drafts) { draft, created -> if (created) published += draft.rkey }
    }
    return PublishPathsResult(published, refreshed, removed, unpathed, stoppedBy)
}

/** The ids of the [paths] the region's live segments lie on, and how many of those segments lie on none ([unpathed]). */
private class PathNeeds(val paths: Set<String>, val unpathed: Int)

/** Asks the AppView, tile by tile over [paths]' box, for every live segment's line and sport group, and tests each against [paths]. */
private suspend fun pathsNeeded(clients: Clients, paths: List<PathEntry>): PathNeeds {
    val index = PathIndex(paths)
    val needed = linkedSetOf<String>()
    var unpathed = 0
    for (tile in tilesOf(paths)) {
        var cursor: String? = null
        do {
            val page = clients.asImporter { clients.appView.query(GetSegmentLines.method, tile.copy(cursor = cursor)) }
            for (segment in page.segments) {
                val line = SegmentGeometry.decodePolyline(segment.line) ?: continue
                val on = index.pathsUnder(line, segment.sport)
                if (on.isEmpty()) unpathed++ else needed += on
            }
            cursor = page.cursor?.takeIf { page.segments.isNotEmpty() }
        } while (cursor != null)
    }
    return PathNeeds(needed, unpathed)
}

/** A published path's name, line pieces and sport groups, as the repo holds them. */
private data class HeldPath(val name: String?, val lines: List<String>, val sports: List<String>?) {
    val pieces: List<List<GeoPoint>>
        get() = lines.mapNotNull(SegmentGeometry::decodePolyline)
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
            held[rkey] = HeldPath(name, strings("lines").orEmpty(), strings("sports"))
        }
        cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
    } while (cursor != null)
    return held
}

/** The box [paths] cover, in degrees; null when they have no points. */
private data class Area(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double) {
    fun holds(point: GeoPoint) = point.lat in minLat..maxLat && point.lng in minLng..maxLng
}

private fun areaOf(paths: List<PathEntry>): Area? {
    val points = paths.flatMap { path -> path.lines.flatMap { SegmentGeometry.decodePolyline(it).orEmpty() } }
    if (points.isEmpty()) return null
    return Area(points.minOf { it.lat }, points.minOf { it.lng }, points.maxOf { it.lat }, points.maxOf { it.lng })
}

/** [path] as the `feed.path` record published for it. */
internal fun pathRecord(path: PathEntry, clock: Clock): JsonObject =
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
            ),
        )
        .let { it as JsonObject }

/** The tiles covering every path in [paths], each a little under 1 degree a side, as `getSegmentLines` boxes. */
internal fun tilesOf(paths: List<PathEntry>): List<GetSegmentLines.Params> {
    val points = paths.flatMap { path -> path.lines.flatMap { SegmentGeometry.decodePolyline(it).orEmpty() } }
    if (points.isEmpty()) return emptyList()
    val minLat = (points.minOf { it.lat } * E7).roundToInt()
    val maxLat = (points.maxOf { it.lat } * E7).roundToInt()
    val minLng = (points.minOf { it.lng } * E7).roundToInt()
    val maxLng = (points.maxOf { it.lng } * E7).roundToInt()
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

    /** The ids of the paths [line], of the sport group [sport], lies on, as the AppView decides it among the same paths. */
    fun pathsUnder(line: List<GeoPoint>, sport: String?): Set<String> =
        PathOverlap.pathsUnder(
            line,
            sport,
            SegmentCells.cellsOf(line).flatMap { byCell[it].orEmpty() }.distinct().map(candidates::getValue),
        )
}

/** Reads [path] and publishes the paths it holds that segments need, printing what happened; throws what stopped it, after the summary. */
internal suspend fun publishPathsFile(clients: Clients, path: File, rewrite: Boolean = false) {
    val file = path.bufferedReader().useLines { readPaths(it) }
    val result = publishPaths(clients, file.paths, rewrite = rewrite)
    println(
        "Published ${result.published.size} of ${file.region}'s ${file.paths.size} paths, refreshed ${result.refreshed.size} and " +
            "removed ${result.removed.size}; " +
            "${result.unpathed} segments there lie on none of them",
    )
    result.stoppedBy?.let { throw it }
}
