package io.github.solcott.activitytracker.data.sport

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.KeyValueEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.activityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.builtins.ListSerializer

/**
 * The activity types the signed-in athlete tracked most recently, newest first, so the sport picker can offer them first. Stored per
 * account on this device. Until the athlete publishes from this device, they come from the athlete's own activities in the feed cache.
 */
@SingleIn(SessionScope::class)
@Inject
class RecentActivityTypes(
    private val db: AppDatabase,
    private val clients: SessionClients,
    feed: FeedRepository,
) {
    private val key = "recentActivityTypes:${clients.did.value}"

    val types: Flow<List<ActivityType>> =
        combine(db.keyValue().observe(key), feed.authorActivities(clients.did.value)) { stored, own ->
                stored?.let(::decode) ?: own.map { it.record.activityType }.distinct().take(MAX)
            }
            .distinctUntilChanged()

    /** Moves [type] to the front. */
    suspend fun remember(type: ActivityType) {
        val current = db.keyValue().get(key)?.let(::decode).orEmpty()
        val updated = (listOf(type) + current.filter { it != type }).take(MAX)
        db.keyValue().put(KeyValueEntity(key, AtJson.encodeToString(serializer, updated)))
    }

    private fun decode(json: String): List<ActivityType>? = runCatching { AtJson.decodeFromString(serializer, json) }.getOrNull()

    private companion object {
        const val MAX = 6
        val serializer = ListSerializer(ActivityType.serializer())
    }
}
