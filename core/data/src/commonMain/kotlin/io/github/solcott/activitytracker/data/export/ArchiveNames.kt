package io.github.solcott.activitytracker.data.export

import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import kotlinx.datetime.toLocalDateTime

/**
 * Names the activity files of one data export (auth/data-export): `<yyyy-mm-dd>-<title>.fit` for a rebuilt file, and
 * `<yyyy-mm-dd>-<original name>` for an original, the date local to the activity's stored offset (else this device's zone). Each name is
 * unique within the export, case-insensitively since most file systems the zip lands on fold case: a clash gets `-2`, `-3`… before its
 * extension. [taken] holds names already given (by an export being resumed), so a resumed export never reuses one.
 */
class ArchiveNames(taken: Collection<String> = emptyList()) {
    private val used = taken.mapTo(HashSet()) { it.lowercase() }

    /** A name for [record]'s rebuilt file with [extension]. */
    fun rebuilt(record: ActivityRecord, extension: String): String =
        claim(
            "${datePrefix(record)}-${ActivityExporter.safeName(ActivityExporter.displayName(record)).ifEmpty { record.sport }}",
            ".$extension",
        )

    /** A name for [record]'s original file, picked as [originalName]. */
    fun original(record: ActivityRecord, originalName: String): String {
        val safe = ActivityExporter.safeName(originalName).ifEmpty { "activity" }
        val dot = safe.lastIndexOf('.').takeIf { it > 0 } ?: safe.length
        return claim("${datePrefix(record)}-${safe.substring(0, dot)}", safe.substring(dot))
    }

    private fun claim(stem: String, extension: String): String {
        var name = stem + extension
        var n = 2
        while (!used.add(name.lowercase())) name = "$stem-${n++}$extension"
        return name
    }

    private fun datePrefix(record: ActivityRecord) = record.startedAt.toLocalDateTime(ActivityExporter.zoneOf(record)).date.toString()
}
