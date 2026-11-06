package io.github.solcott.activitytracker.data.export

import kotlinx.serialization.Serializable

/**
 * `manifest.json` in a data export (auth/data-export): whose data it is, what it holds, which file belongs to which activity, and what it
 * couldn't read. [format] goes up when the archive's layout changes, so a later reader (a restore) can tell old archives apart.
 */
@Serializable
data class ExportManifest(
    val format: Int = FORMAT,
    val did: String,
    val handle: String,
    val pds: String,
    /** ISO 8601. */
    val exportedAt: String,
    /** False when the session couldn't read the athlete's spaces: the archive then holds their repo only. */
    val spacesIncluded: Boolean,
    /** Changes on the exporting device that hadn't uploaded when the export started, which the archive can't hold. */
    val unsentChanges: Int,
    val counts: Counts,
    /** Each activity's URI and its file under `activities/`. */
    val activities: Map<String, String>,
    /**
     * Each original file's blob CID (in its `feed.activitySource` record, under `records/self/`) and where it is, unzipped, in
     * `activities/`.
     */
    val originals: Map<String, String>,
    val problems: List<Problem>,
) {
    @Serializable data class Counts(val records: Int, val blobs: Int, val activityFiles: Int)

    /** Something left out: a blob ([kind] `blob`, [key] its CID) or an activity's file (`activity`, its rkey), and why. */
    @Serializable data class Problem(val kind: String, val place: String, val key: String, val reason: String)

    companion object {
        const val FORMAT = 1
    }
}
