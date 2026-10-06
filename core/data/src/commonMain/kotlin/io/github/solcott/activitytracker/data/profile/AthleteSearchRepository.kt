package io.github.solcott.activitytracker.data.profile

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.SearchActors

/** Finds other athletes of this app through the AppView. */
@SingleIn(SessionScope::class)
@Inject
class AthleteSearchRepository(private val clients: SessionClients) {
    /** One page of athletes matching [query] by handle, display name or DID, best matches first. */
    suspend fun search(query: String, cursor: String? = null): SearchActors.Output =
        clients.appView.query(SearchActors.method, SearchActors.Params(query, cursor = cursor))
}
