package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import io.github.solcott.activitytracker.lexicon.atproto.PutRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlinx.serialization.json.JsonObject

// Where the signed-in athlete's activities and streams are stored: an activity's visibility is its place. Public records are in the repo;
// the others in the athlete's tier space for that visibility. Visibility moves are direct calls, not outbox writes, so a failure shows.

/** The tier space for [visibility], or null for the repo. An unknown value is treated as Only me. */
internal fun SessionClients.placeOf(visibility: String): SpaceUri? =
    Visibility.known(visibility).takeIf { it != Visibility.PUBLIC }?.let(tiers::uri)

/** Refuses a non-public [visibility] for a session that can't write to spaces, as the PDS would, so nothing is published wider. */
internal fun SessionClients.requireSpacesFor(visibility: String) {
    if (placeOf(visibility) != null && !canUseSpaces) {
        throw XrpcException(SCOPE_MISSING_STATUS, SpaceErrors.SCOPE_MISSING, "Sign in again to choose who sees your activities")
    }
}

/** The athlete's own [collection]/[rkey] at [place], as stored now; null when it isn't there. */
internal suspend fun SessionClients.readOwn(collection: String, rkey: String, place: SpaceUri?): JsonObject? =
    if (place == null) {
        try {
            pds.query(GetRecord.method, GetRecord.Params(did.value, collection, rkey)).value
        } catch (e: XrpcException) {
            if (e.error != RECORD_NOT_FOUND) throw e
            null
        }
    } else {
        when (val read = spaces.getRecord(place, did, collection, rkey)) {
            is SpaceRead.Found -> read.value.value
            SpaceRead.NotFound -> null
            SpaceRead.NoAccess -> throw XrpcException(SCOPE_MISSING_STATUS, SpaceErrors.NOT_AUTHORIZED, "Can't read $place")
        }
    }

/** Writes the athlete's [collection]/[rkey] at [place] now, creating the tier space if need be. */
internal suspend fun SessionClients.writeOwn(collection: String, rkey: String, record: JsonObject, place: SpaceUri?) {
    if (place == null) {
        pds.procedure(PutRecord.method, PutRecord.Input(repo = did, collection = collection, rkey = rkey, record = record))
    } else {
        tiers.ensure(place.skey)
        spaces.putRecord(place, collection, rkey, record)
    }
}

/** Deletes the athlete's [collection]/[rkey] at [place] now; one that isn't there is deleted already. */
internal suspend fun SessionClients.deleteOwn(collection: String, rkey: String, place: SpaceUri?) {
    if (place == null) {
        pds.procedure(DeleteRecord.method, DeleteRecord.Input(repo = did, collection = collection, rkey = rkey))
    } else {
        deleteFromSpace(place, collection, rkey)
    }
}

/**
 * Moves the athlete's [collection]/[rkey] from [from] to [to], [transform]ing the record on the way: the new place is written before the
 * old one is deleted, so a failure part-way leaves it stored at least once, and a retry finishes the job. Returns false if it's in neither.
 */
internal suspend fun SessionClients.moveOwn(
    collection: String,
    rkey: String,
    from: SpaceUri?,
    to: SpaceUri?,
    transform: (JsonObject) -> JsonObject = { it },
): Boolean {
    val record = readOwn(collection, rkey, from) ?: readOwn(collection, rkey, to) ?: return false
    writeOwn(collection, rkey, transform(record), to)
    if (from != to) deleteOwn(collection, rkey, from)
    return true
}

private const val RECORD_NOT_FOUND = "RecordNotFound"
private const val SCOPE_MISSING_STATUS = 403
