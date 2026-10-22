package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.FinishSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.PutSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateRejection
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import java.io.File
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.serialization.json.Json

/** As `extract` writes the file: a null extract timestamp is left out. */
private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** The run's revision: when it started, in UTC, as `yyyyMMddTHHmmssZ`. */
private val REVISION = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/** A candidates file read whole: its region and candidates, in file order. */
internal data class CandidatesFile(val region: String, val candidates: List<SegmentCandidate>)

/** What a finished `stage` run did. */
internal data class StageResult(val region: String, val revision: String, val staged: Int, val rejected: List<SegmentCandidateRejection>)

/**
 * Reads [lines] of a file `extract` wrote. Throws [IllegalArgumentException] for a file of another format version or a candidate of another
 * region than its header's, before anything is uploaded.
 */
internal fun readCandidates(lines: Sequence<String>): CandidatesFile {
    val iterator = lines.filter { it.isNotBlank() }.iterator()
    require(iterator.hasNext()) { "an empty candidates file" }
    val header = json.decodeFromString(CandidatesHeader.serializer(), iterator.next())
    require(header.importerVersion == IMPORTER_VERSION) {
        "a candidates file of format version ${header.importerVersion}; this importer reads version $IMPORTER_VERSION"
    }
    val candidates = mutableListOf<SegmentCandidate>()
    for (line in iterator) {
        val entry = json.decodeFromString(CandidateEntry.serializer(), line)
        require(entry.region == header.region) { "candidate ${entry.id} is in ${entry.region}, not the file's ${header.region}" }
        candidates += SegmentCandidate(entry.id, entry.sport, entry.name, entry.line, entry.source, entry.license)
    }
    return CandidatesFile(header.region, candidates)
}

/**
 * Stages [file] as one run (segments/segment-import): every candidate in batches of [SegmentCandidates.MAX_BATCH] under one revision, then
 * the finish that removes the region's candidates of earlier runs. A failed batch throws before the finish, so a partial run never removes
 * anything; staging the whole file again replaces it.
 */
internal suspend fun stage(clients: Clients, file: CandidatesFile, clock: Clock = Clock.System): StageResult {
    val revision = REVISION.format(clock.now().toJavaInstant())
    val rejected = mutableListOf<SegmentCandidateRejection>()
    clients.asImporter {
        for (batch in file.candidates.chunked(SegmentCandidates.MAX_BATCH)) {
            rejected +=
                clients.appView.procedure(PutSegmentCandidates.method, PutSegmentCandidates.Input(file.region, revision, batch)).rejected
        }
        clients.appView.procedure(FinishSegmentCandidates.method, FinishSegmentCandidates.Input(file.region, revision))
    }
    return StageResult(file.region, revision, file.candidates.size - rejected.size, rejected)
}

/** Reads and stages [path], printing what happened. */
internal suspend fun stageFile(clients: Clients, path: File) {
    val file = path.bufferedReader().useLines { readCandidates(it) }
    val result = stage(clients, file)
    for (r in result.rejected) println("Rejected ${r.id}: ${r.reason}")
    println("Staged ${result.staged} candidates for ${result.region} (revision ${result.revision}), ${result.rejected.size} rejected")
}
