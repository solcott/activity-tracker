package io.github.solcott.activitytracker.data.profile

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.outcomeOf
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchActors
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.pageOf

/** Finds other athletes of this app through the AppView. */
@SingleIn(SessionScope::class)
@Inject
class AthleteSearchRepository(private val clients: SessionClients) {
    /** One page of athletes matching [query] by handle, display name or DID, best matches first. */
    suspend fun search(query: String, cursor: String? = null): SearchActors.Output =
        clients.appView.query(SearchActors.method, SearchActors.Params(query, cursor = cursor))

    /** A page of [search] for a paged list. */
    suspend fun searchPage(query: String, cursor: String?): Outcome<Page<ProfileViewBasic, Unit>> = outcomeOf {
        search(query, cursor).let { pageOf(it.actors, it.cursor) }
    }
}
