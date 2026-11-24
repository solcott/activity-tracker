package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import io.ktor.http.HttpStatusCode

// The segment importer (`SEGMENT_IMPORTER_DID`, segments/segment-import): one account whose segments, dive sites and paths are trusted
// further than an athlete's. With none configured, nobody is it.

/** Whether [author] is the segment importer this names; never when none is configured. */
internal fun Did?.isImporter(author: String): Boolean = this != null && author == value

/** Whether [author] is the segment importer this names; never when none is configured. */
internal fun Did?.isImporter(author: Did): Boolean = this != null && author == this

/** Refuses [caller], `403 NotImporter`, unless it's the segment importer this names; everyone when none is configured. */
internal fun Did?.requireImporter(caller: Did, calls: String) {
    if (!isImporter(caller)) {
        throw XrpcError(HttpStatusCode.Forbidden.value, SegmentCandidates.NOT_IMPORTER, "Only the segment importer may $calls")
    }
}
