package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentClimb
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.terrain.TerrainTiles
import java.io.File
import java.io.Writer
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The candidates file's format; bump it when what a line holds changes, so a reader can refuse a file it doesn't know. */
internal const val IMPORTER_VERSION = 7

/** The licence of OSM-derived lines, on every candidate and on the segments published from them. */
internal const val LICENSE_OSM = "ODbL-1.0"

/** The licence of agency trail data, which US agencies publish in the public domain. */
internal const val LICENSE_AGENCY = "CC0-1.0"

/** The first line of a candidates file. */
@Serializable
internal data class CandidatesHeader(
    val region: String,
    /** When the OSM extract's data was current, from its header; null when it doesn't say or there's no OSM extract. */
    val extractTimestamp: Instant?,
    val importerVersion: Int,
)

/** Each later line: one candidate segment (segments/trail-candidates). */
@Serializable
internal data class CandidateEntry(
    val id: String,
    val region: String,
    val sport: String,
    val name: String,
    /** Encoded as a `feed.segment` line: a polyline at precision 6. */
    val line: String,
    val lengthMeters: Long,
    val source: String,
    val license: String,
    /** Who to credit for an agency candidate's data. */
    val attribution: String? = null,
    /** The OSM ways an OSM candidate follows. */
    val osmWays: List<Long>? = null,
    /** The agency features an agency candidate follows, as `<kind>:<feature key>`. */
    val agencyFeatures: List<String>? = null,
    /** A `feed.segment` kind; null for a run. */
    val kind: String? = null,
    /** Its sport group may travel it only this way (segments/segment-direction); null when it goes both ways. */
    val oneWay: Boolean? = null,
    /**
     * A `feed.segment#climb` value: whether it climbs or descends as its trail does, by terrain (segments/trail-candidates); null for none.
     */
    val climb: String? = null,
    /** What its ways say about its trail (segments/trail-attributes); null when nothing is known. */
    val trail: TrailAttributes? = null,
)

private val json = Json { explicitNulls = false }

/**
 * Reads [pbf], if given, and each of the [agency] datasets, and writes [region]'s candidates from all of them to [out], logging how many
 * each sport group got, how many OSM candidates of each group agency closures left out, how many agency features were skipped, and when
 * [words] has no list for the region's language.
 */
@Suppress("LongParameterList") // The inputs, the region and the two outputs.
internal fun extract(
    pbf: File?,
    agency: List<AgencyDataset>,
    region: String,
    out: File,
    words: GenericWords = GenericWords.ENGLISH,
    paths: File? = null,
    terrain: TerrainTiles? = null,
    refBridgeM: Double = TrailBuilder.DEFAULT_REF_BRIDGE_M,
) {
    val osm = pbf?.let { OsmReader.read(it) } ?: OsmData(emptyList(), emptyList(), NodeCoords(LongArray(0)))
    val agencyData = agency.takeIf { it.isNotEmpty() }?.let(AgencyReader::read)
    // Ways take the access, operator and website tags of their route relations once, before trails are built (segments/trail-attributes).
    val data = RouteInheritance.applied(agencyData?.let { osm + it } ?: osm)
    val closures = ClosureFilter(data.agencyLines)
    val candidates = candidatesOf(data, closures, words, terrain?.let(::climbByTerrain), refBridgeM)
    out.bufferedWriter().use { write(it, region, data.timestamp, candidates) }
    val counts = candidates.groupingBy { it.group }.eachCount().toSortedMap()
    println("Wrote ${candidates.size} candidates for $region to $out: ${counts.entries.joinToString { (g, n) -> "$n $g" }}")
    for ((group, n) in closures.leftOut) println("Left out $n $group candidates along agency closures")
    for ((rules, skipped) in agencyData?.skipped.orEmpty()) println("Skipped $skipped ${rules.kind} features with no feature id or no line")
    paths?.let { file ->
        val entries = pathsOf(data, words, refBridgeM, candidates)
        file.bufferedWriter().use { writePaths(it, region, entries) }
        val kinds = entries.groupingBy { it.kind }.eachCount().toSortedMap()
        println("Wrote ${entries.size} paths for $region to $file: ${kinds.entries.joinToString { (k, n) -> "$n $k" }}")
    }
    if (!words.known) println("No generic words for the language '${words.language}': names are kept as their source has them")
}

/**
 * Whether a line clearly climbs or descends by [terrain] (segments/segment-direction), counted as the AppView counts terrain elevation:
 * sampled, smoothed and counted along its whole line; null when the tiles don't cover all of it.
 */
internal fun climbByTerrain(terrain: TerrainTiles): (List<GeoPoint>) -> SegmentClimb? = { points ->
    terrain.terrainOf(points)?.let { SegmentClimb.of(it.gain, it.loss, SegmentGeometry.lengthMeters(points).roundToLong()) }
}

/** Every candidate [data] gives, named without [words], those [closures] leave out and then duplicates dropped, sorted by id. */
internal fun candidatesOf(
    data: OsmData,
    closures: ClosureFilter,
    words: GenericWords = GenericWords.ENGLISH,
    climbOf: ((List<GeoPoint>) -> SegmentClimb?)? = null,
    refBridgeM: Double = TrailBuilder.DEFAULT_REF_BRIDGE_M,
): List<Candidate> {
    val cutter = CandidateCutter(data, words, climbOf)
    val trails = RouteClimbs.inherit(TrailBuilder.build(data, words, refBridgeM).flatMap(cutter::cut))
    return CandidateDedup.dedup((trails + LiftCandidates.of(data)).filter(closures::keep)).sortedBy { it.id }
}

/**
 * [data]'s trail paths and road paths (segments/path-import), sorted by id; a road and a trail never share an id. Trail paths list the
 * sections of the kept [candidates] on them.
 */
internal fun pathsOf(
    data: OsmData,
    words: GenericWords = GenericWords.ENGLISH,
    refBridgeM: Double = TrailBuilder.DEFAULT_REF_BRIDGE_M,
    candidates: List<Candidate> = emptyList(),
): List<PathEntry> {
    val agencyKeys = data.ways.mapNotNull { way -> way.tags[AgencyRules.KEY]?.let { way.id to it } }.toMap()
    val ways = data.ways.associateBy { it.id }
    return (TrailPaths.of(TrailBuilder.build(data, words, refBridgeM), agencyKeys, candidates, ways) + RoadPaths.of(data))
        .distinctBy { it.id }
        .sortedBy { it.id }
}

/** The candidates file: a header line, then one line per candidate, in the order given. */
internal fun write(out: Writer, region: String, timestamp: Instant?, candidates: List<Candidate>) {
    out.appendLine(json.encodeToString(CandidatesHeader.serializer(), CandidatesHeader(region, timestamp, IMPORTER_VERSION)))
    for (c in candidates) {
        val entry =
            CandidateEntry(
                c.id,
                region,
                c.group,
                c.name,
                c.line,
                c.lengthMeters,
                c.source,
                c.license,
                c.attribution,
                c.osmWays.ifEmpty { null },
                c.agencyFeatures.ifEmpty { null },
                c.segmentKind.takeIf { it != SegmentKind.RUN },
                true.takeIf { c.oneWay },
                c.climb?.word,
                c.trail,
            )
        out.appendLine(json.encodeToString(CandidateEntry.serializer(), entry))
    }
}
