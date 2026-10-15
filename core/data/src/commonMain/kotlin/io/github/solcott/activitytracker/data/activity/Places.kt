package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentView
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

/**
 * Where the signed-in athlete's like on an activity at [visibility] goes, or their comment written for that audience ([commentAudience]):
 * the repo for public, else their interactions space, which only they and the AppView read. It stays where it was written when the
 * activity's visibility later changes.
 */
internal fun SessionClients.interactionPlaceOf(visibility: String): SpaceUri? =
    placeOf(visibility)?.let { tiers.uri(AthleteSpace.INTERACTIONS) }

/**
 * Who a comment on an activity at [visibility] is written for: that visibility, or for a reply to [replyTo] the narrower of it and
 * [replyTo]'s audience, so a reply never reaches viewers its parent was hidden from. The comment is placed by it ([interactionPlaceOf]).
 */
internal fun commentAudience(visibility: String, replyTo: CommentView?): String =
    Visibility.known(visibility).let { if (replyTo == null) it else Visibility.narrower(it, Visibility.known(replyTo.audience)) }

/**
 * Refuses a non-public [visibility] for a session that can't write to spaces, as the PDS would, so nothing is published wider. With a
 * [collection], also one whose grant doesn't cover that collection ([SessionClients.canWriteToSpaces]), so nothing is queued that the PDS
 * would refuse and leave at the head of the outbox.
 */
internal fun SessionClients.requireSpacesFor(visibility: String, collection: String? = null) {
    val allowed = if (collection == null) canUseSpaces else canWriteToSpaces(collection)
    if (placeOf(visibility) != null && !allowed) {
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
