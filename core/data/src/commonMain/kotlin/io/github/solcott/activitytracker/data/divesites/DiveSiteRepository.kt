package io.github.solcott.activitytracker.data.divesites

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.outcomeOf
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSite
import io.github.solcott.activitytracker.lexicon.app.DiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.GetDiveSite
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteDives
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteRegulars
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The AppView's dive-site queries (dive-sites): a site, its dives, conditions and Regulars, and the check before one is published. */
@SingleIn(SessionScope::class)
@Inject
class DiveSiteRepository(private val clients: SessionClients) {
    /** The dives the signed-in athlete published a site from this session ([DiveSitePublisher]), so their pages can say so. */
    val createdFrom: StateFlow<Set<AtUri>>
        field = MutableStateFlow(emptySet())

    /** Notes that a site was published from [dive]. */
    fun markCreated(dive: AtUri) = createdFrom.update { it + dive }

    /** Forgets [dive]'s note, once its page has said so. */
    fun dismissCreated(dive: AtUri) = createdFrom.update { it - dive }

    /** The site at [uri], with its names and the viewer's naming flags. */
    suspend fun site(uri: AtUri): DiveSiteView = clients.appView.query(GetDiveSite.method, GetDiveSite.Params(uri)).site

    /** [site] as a stream: loading, then the site or why it couldn't be read. Each collection reads it again. */
    fun siteOutcome(uri: AtUri): Flow<Outcome<DiveSiteView>> = outcomeFlow { site(uri) }

    /** One page of the dives at [site] the viewer may see, newest first. */
    suspend fun divesPage(site: AtUri, cursor: String? = null): Outcome<Page<ActivityView, Unit>> = outcomeOf {
        val output = clients.appView.query(GetDiveSiteDives.method, GetDiveSiteDives.Params(site, cursor = cursor))
        Page(output.dives, Unit, output.cursor)
    }

    /** The site's conditions over the dives the viewer may see with their dive data; null when there are none. */
    suspend fun conditions(site: AtUri): Outcome<DiveSiteConditions?> = outcomeOf {
        clients.appView.query(GetDiveSiteConditions.method, GetDiveSiteConditions.Params(site)).conditions
    }

    /** One page of [site]'s Regulars board for [period] (a `RegularsPeriod` value) and [scope], with the viewer's own entry as its meta. */
    suspend fun regularsPage(
        site: AtUri,
        period: String,
        scope: String,
        cursor: String? = null,
        limit: Int? = null,
    ): Outcome<Page<DiveSiteRegularEntry, RegularsMeta>> = outcomeOf {
        val output =
            clients.appView.query(
                GetDiveSiteRegulars.method,
                GetDiveSiteRegulars.Params(site, period, scope, limit = limit, cursor = cursor),
            )
        Page(output.entries, RegularsMeta(output.viewer), output.cursor)
    }

    /** The live sites within 50 m of ([lat], [lng]), in 1e-7 degrees, and how many more sites the athlete may create today. */
    suspend fun check(lat: Int, lng: Int): CheckDiveSite.Output =
        clients.appView.procedure(CheckDiveSite.method, CheckDiveSite.Input(lat, lng))
}

/** A Regulars page's meta: the viewer's own entry, null when they aren't on the board. */
@Immutable data class RegularsMeta(val viewer: DiveSiteRegularEntry? = null)
