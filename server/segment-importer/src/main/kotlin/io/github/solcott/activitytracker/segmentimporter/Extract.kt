package io.github.solcott.activitytracker.segmentimporter

import java.io.File
import java.io.Writer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The candidates file's format; bump it when what a line holds changes, so a reader can refuse a file it doesn't know. */
internal const val IMPORTER_VERSION = 2

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
)

private val json = Json { explicitNulls = false }

/**
 * Reads [pbf], if given, and each of the [agency] datasets, and writes [region]'s candidates from all of them to [out], logging how many
 * each sport group got, how many OSM candidates of each group agency closures left out, and how many agency features were skipped.
 */
internal fun extract(pbf: File?, agency: List<Pair<AgencyRules, File>>, region: String, out: File) {
    val osm = pbf?.let(OsmReader::read) ?: OsmData(emptyList(), emptyList(), NodeCoords(LongArray(0)))
    val agencyData = agency.takeIf { it.isNotEmpty() }?.let(AgencyReader::read)
    val data = agencyData?.let { osm + it } ?: osm
    val closures = ClosureFilter(data.agencyLines)
    val candidates = candidatesOf(data, closures)
    out.bufferedWriter().use { write(it, region, data.timestamp, candidates) }
    val counts = candidates.groupingBy { it.group }.eachCount().toSortedMap()
    println("Wrote ${candidates.size} candidates for $region to $out: ${counts.entries.joinToString { (g, n) -> "$n $g" }}")
    for ((group, n) in closures.leftOut) println("Left out $n $group candidates along agency closures")
    for ((rules, skipped) in agencyData?.skipped.orEmpty()) println("Skipped $skipped ${rules.kind} features with no feature id or no line")
}

/** Every candidate [data] gives, those [closures] leave out and then duplicates dropped, sorted by id. */
internal fun candidatesOf(data: OsmData, closures: ClosureFilter): List<Candidate> {
    val cutter = CandidateCutter(data)
    return CandidateDedup.dedup(TrailBuilder.build(data).flatMap(cutter::cut).filter(closures::keep)).sortedBy { it.id }
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
            )
        out.appendLine(json.encodeToString(CandidateEntry.serializer(), entry))
    }
}
