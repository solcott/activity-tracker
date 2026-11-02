package io.github.solcott.activitytracker.data.importsave

import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.vinceglb.filekit.PlatformFile

/**
 * How a picked file of a batch import's save is kept so a later process can reopen it (feed/batch-import): a [Kind.BOOKMARK] to the
 * athlete's own file (an Android persisted URI grant, an Apple security-scoped bookmark, a desktop path), or a [Kind.COPY] in app storage
 * (Android past its grant budget). Stored as [Kind.name] and [data] (a BLOB) beside the save.
 */
class KeptSource(val kind: Kind, val data: ByteArray) {
    enum class Kind {
        BOOKMARK,
        COPY,
    }

    // A ByteArray's equals is identity: compare the bytes.
    override fun equals(other: Any?): Boolean = other is KeptSource && kind == other.kind && data.contentEquals(other.data)

    override fun hashCode(): Int = kind.hashCode() * 31 + data.contentHashCode()

    override fun toString(): String = "KeptSource($kind, ${data.size} bytes)"

    companion object {
        /** The [KeptSource] stored as [kind] ([Kind.name]) and [data], or null for a kind this build doesn't know. */
        fun of(kind: String, data: ByteArray): KeptSource? = Kind.entries.firstOrNull { it.name == kind }?.let { KeptSource(it, data) }
    }
}

/**
 * A kept source opened again: [source] reads it, [refreshed] (when not null) replaces the stored [KeptSource] (an Apple bookmark the system
 * reported stale), and [close] lets go of what the reopening holds (an Apple bookmark's security scope) once the save is done with it.
 * Closing never releases the keeping itself: that's [SourceKeeper.release].
 */
class ReopenedSource(val source: ImportSource, val refreshed: KeptSource? = null, private val onClose: () -> Unit = {}) {
    fun close() = onClose()
}

/** An [ImportSource] over a FileKit [PlatformFile], which a [SourceKeeper] can bookmark. */
interface PlatformBacked {
    val file: PlatformFile
}

/**
 * Keeps a batch import's picked files reopenable after process death, so an interrupted save resumes (feed/batch-import), and lets go of
 * them when the save ends. One per platform in `AppScope`.
 */
interface SourceKeeper {
    /**
     * Keeps what it can of [sources] for save [saveId], in order: null for one it can't keep (anything on the web; on Android, a source
     * past the grant budget too large to copy).
     */
    suspend fun keep(saveId: String, sources: List<ImportSource>): List<KeptSource?>

    /** Reopens [kept], or null when the file is gone or access to it was lost. */
    suspend fun open(kept: KeptSource): ReopenedSource?

    /** Releases [kept] (grants, copies) of save [saveId], once it's finished or cancelled. */
    suspend fun release(saveId: String, kept: List<KeptSource>)

    /**
     * Releases the grants and deletes the copies no save in [live] (save id to its kept sources) names: the startup sweep after a crash or
     * a save deleted without [release].
     */
    suspend fun sweep(live: Map<String, List<KeptSource>>)
}
