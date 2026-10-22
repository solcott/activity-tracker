package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import java.io.File
import java.io.Writer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The candidates file's format; bump it when what a line holds changes, so a reader can refuse a file it doesn't know. */
internal const val IMPORTER_VERSION = 1

/** The licence of OSM-derived lines, on every candidate and on the segments published from them. */
internal const val LICENSE_OSM = "ODbL-1.0"

/** The first line of a candidates file. */
@Serializable
internal data class CandidatesHeader(
    val region: String,
    /** When the extract's data was current, from its header; null when it doesn't say. */
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
    val osmWays: List<Long>,
)

private val json = Json { explicitNulls = false }

/** Reads [pbf] and writes [region]'s candidates to [out], logging how many each sport group got. */
internal fun extract(pbf: File, region: String, out: File) {
    val data = OsmReader.read(pbf)
    val candidates = candidatesOf(data)
    out.bufferedWriter().use { write(it, region, data.timestamp, candidates) }
    val counts = candidates.groupingBy { it.group }.eachCount().toSortedMap()
    println("Wrote ${candidates.size} candidates for $region to $out: ${counts.entries.joinToString { (g, n) -> "$n $g" }}")
}

/** Every candidate [data] gives, duplicates dropped, sorted by id. */
internal fun candidatesOf(data: OsmData): List<Candidate> {
    val cutter = CandidateCutter(data)
    return CandidateDedup.dedup(TrailBuilder.build(data).flatMap(cutter::cut)).sortedBy { it.id }
}

/** The candidates file: a header line, then one line per candidate, in the order given. */
internal fun write(out: Writer, region: String, timestamp: Instant?, candidates: List<Candidate>) {
    out.appendLine(json.encodeToString(CandidatesHeader.serializer(), CandidatesHeader(region, timestamp, IMPORTER_VERSION)))
    for (c in candidates) {
        val entry = CandidateEntry(c.id, region, c.group, c.name, c.line, c.lengthMeters, SegmentSource.OSM, LICENSE_OSM, c.osmWays)
        out.appendLine(json.encodeToString(CandidateEntry.serializer(), entry))
    }
}
