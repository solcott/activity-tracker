package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SpacesSupport
import io.github.solcott.activitytracker.data.di.SpacesUnsupportedException
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
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
 * would refuse and leave at the head of the outbox. On a PDS without Spaces the refusal is [SpacesUnsupportedException], since signing in
 * again can't help.
 */
internal fun SessionClients.requireSpacesFor(visibility: String, collection: String? = null) {
    val allowed = if (collection == null) canUseSpaces else canWriteToSpaces(collection)
    if (placeOf(visibility) != null && !allowed) throw spacesRefused("Sign in again to choose who sees your activities")
}

/**
 * Refuses a write of [collection] to the athlete's interactions space for a session whose `space:` grant doesn't cover it, as the PDS
 * would, so nothing is queued that would sit at the head of the outbox.
 */
internal fun SessionClients.requireInteractionsFor(collection: String, scopeMessage: String = "Sign in again to block or mute accounts") {
    if (!canWriteToSpaces(collection)) throw spacesRefused(scopeMessage)
}

/** Why this session can't write to its spaces: no Spaces on its PDS, or else a grant from before the scope ([scopeMessage]). */
private fun SessionClients.spacesRefused(scopeMessage: String): Exception =
    if (spacesSupport.value == SpacesSupport.Unsupported) SpacesUnsupportedException()
    else XrpcException(SCOPE_MISSING_STATUS, SpaceErrors.SCOPE_MISSING, scopeMessage)

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

/** The athlete's own [collection] records at [place] (null: the repo), by rkey. A tier space that doesn't exist yet holds none. */
internal suspend fun SessionClients.listOwn(collection: String, place: SpaceUri?): Map<String, JsonObject> {
    if (place == null) {
        val records = mutableMapOf<String, JsonObject>()
        var cursor: String? = null
        do {
            val page = pds.query(ListRecords.method, ListRecords.Params(did.value, collection, LIST_PAGE, cursor))
            page.records.forEach { r -> r.uri.rkey?.let { records[it] = r.value } }
            cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
        } while (cursor != null)
        return records
    }
    val read =
        try {
            spaces.listAllRecords(place, did, collection, LIST_PAGE)
        } catch (e: XrpcException) {
            if (e.error != SpaceErrors.SPACE_NOT_FOUND) throw e
            SpaceRead.NotFound
        }
    return when (read) {
        is SpaceRead.Found -> read.value.mapNotNull { r -> r.value?.let { r.rkey to it } }.toMap()
        SpaceRead.NotFound -> emptyMap()
        SpaceRead.NoAccess -> throw XrpcException(SCOPE_MISSING_STATUS, SpaceErrors.NOT_AUTHORIZED, "Can't read $place")
    }
}

private const val LIST_PAGE = 100
private const val RECORD_NOT_FOUND = "RecordNotFound"
private const val SCOPE_MISSING_STATUS = 403
