package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import kotlinx.serialization.KSerializer

// The athlete's one-record settings in their `self` space (activity and gear defaults, privacy zones, segment, grouping and leaderboard
// choices), which only they and the AppView read. Each repository decides what a missing, refused or unreadable record means.

/**
 * The athlete's [collection]/[rkey] in their `self` space, decoded with [serializer]: found with null when it doesn't decode, since any
 * client can write it. Throws when the PDS can't be reached.
 */
internal suspend fun <T> SessionClients.readSelfRecord(collection: String, rkey: String, serializer: KSerializer<T>): SpaceRead<T?> =
    when (val read = spaces.getRecord(tiers.uri(AthleteSpace.SELF), did, collection, rkey)) {
        is SpaceRead.Found -> SpaceRead.Found(runCatching { AtJson.decodeFromJsonElement(serializer, read.value.value) }.getOrNull())
        SpaceRead.NotFound -> SpaceRead.NotFound
        SpaceRead.NoAccess -> SpaceRead.NoAccess
    }

/** Writes [record] as the athlete's [collection]/[rkey] in their `self` space, creating the space if need be. */
internal suspend fun <T> SessionClients.writeSelfRecord(collection: String, rkey: String, serializer: KSerializer<T>, record: T) {
    spaces.putRecord(tiers.ensure(AthleteSpace.SELF), collection, rkey, AtJson.encodeToJsonElement(serializer, record))
}
