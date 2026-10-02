package io.github.solcott.activitytracker.data.feed

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** AppView reads, cached in Room so feeds render offline and instantly on launch. */
@SingleIn(SessionScope::class)
@Inject
class FeedRepository(
    private val db: AppDatabase,
    private val clients: SessionClients,
) {
    fun timeline(): Flow<List<ActivityView>> = observe(TIMELINE)

    fun authorActivities(did: String): Flow<List<ActivityView>> = observe(authorKey(did))

    suspend fun refreshTimeline() {
        val output = clients.appView.query(GetTimeline.method, GetTimeline.Params())
        db.feed().replace(TIMELINE, output.activities.toEntities(TIMELINE))
    }

    suspend fun refreshAuthor(actor: String) {
        val output = clients.appView.query(GetAuthorActivities.method, GetAuthorActivities.Params(actor))
        val did = output.activities.firstOrNull()?.author?.did?.value ?: actor
        db.feed().replace(authorKey(did), output.activities.toEntities(authorKey(did)))
    }

    suspend fun activity(uri: AtUri): GetActivity.Output =
        clients.appView.query(GetActivity.method, GetActivity.Params(uri)).also { output -> updateCached(uri) { output.activity } }

    /** Optimistically rewrites every cached copy of [uri]. */
    suspend fun updateCached(uri: AtUri, transform: (ActivityView) -> ActivityView) {
        val items = db.feed().byUri(uri.value)
        if (items.isEmpty()) return
        db.feed()
            .upsert(
                items.map { item ->
                    val view = transform(AtJson.decodeFromString(ActivityView.serializer(), item.json))
                    item.copy(json = AtJson.encodeToString(ActivityView.serializer(), view))
                }
            )
    }

    private fun observe(key: String): Flow<List<ActivityView>> =
        db.feed().observe(key).map { items ->
            items.mapNotNull { runCatching { AtJson.decodeFromString(ActivityView.serializer(), it.json) }.getOrNull() }
        }

    private fun List<ActivityView>.toEntities(key: String) = mapIndexed { index, view ->
        FeedItemEntity(
            key,
            view.uri.value,
            index,
            AtJson.encodeToString(ActivityView.serializer(), view),
        )
    }

    private companion object {
        const val TIMELINE = "timeline"

        fun authorKey(did: String) = "author:$did"
    }
}
