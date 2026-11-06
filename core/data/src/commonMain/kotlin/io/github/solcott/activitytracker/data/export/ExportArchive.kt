package io.github.solcott.activitytracker.data.export

import androidx.compose.runtime.Immutable
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A finished data export, to be zipped straight into wherever the athlete saves it (auth/data-export): [writeTo] hands the zip to its
 * caller chunk by chunk, so it's never whole in memory and no second copy is kept. Equal by export id.
 */
@Immutable
class ExportArchive(val id: String, val name: String, private val at: LocalDateTime, private val zipper: ArchiveZipper) {
    /** Writes the zip to [write], chunk by chunk. */
    suspend fun writeTo(write: suspend (ByteArray) -> Unit) = zipper.zip(id, at, write)

    override fun equals(other: Any?): Boolean = other is ExportArchive && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ExportArchive($id, $name)"

    companion object {
        const val MIME_TYPE = "application/zip"

        /** `activity-tracker-<handle>-<yyyy-mm-dd>.zip`, the date the export started, local to [zone]. */
        fun nameOf(handle: String, startedAt: Instant, zone: TimeZone): String =
            "activity-tracker-${handle.replace(Regex("""[/\\:*?"<>|]"""), "_")}-${startedAt.toLocalDateTime(zone).date}.zip"
    }
}
