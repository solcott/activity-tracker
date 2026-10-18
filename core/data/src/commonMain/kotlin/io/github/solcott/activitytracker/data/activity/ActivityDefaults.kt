package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDefaultsRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.DetailVisibilities
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * Who an activity and each of its [DetailCategory]s are shown to: [Visibility] values, no category wider than the activity. A category
 * missing from [details] is at the activity's visibility ([tiers]).
 */
data class VisibilityChoice(val activity: String = Visibility.PUBLIC, val details: Map<String, String> = emptyMap()) {
    /** Every [DetailCategory.all] at its visibility, capped at the activity's. */
    val tiers: Map<String, String>
        get() = DetailCategory.tiers(activity, details)

    /** This choice with every category narrowed to the activity if it's wider. */
    fun capped(): VisibilityChoice = copy(activity = Visibility.known(activity), details = tiers)

    /** This choice with the activity at [visibility]: wider categories are narrowed with it, and a widening leaves them as they are. */
    fun withActivity(visibility: String): VisibilityChoice = copy(activity = Visibility.known(visibility), details = tiers).capped()

    /** This choice with [category] at [visibility], capped at the activity. */
    fun withCategory(category: String, visibility: String): VisibilityChoice =
        copy(details = tiers + (category to Visibility.capped(Visibility.known(visibility), activity)))

    /** Whether some category is narrower than the activity, so publishing needs details records in the tier spaces. */
    val splits: Boolean
        get() = tiers.values.any { it != Visibility.known(activity) }

    // Two choices that show everything to the same viewers are equal, however their categories are spelled out.
    override fun equals(other: Any?): Boolean =
        other is VisibilityChoice && Visibility.known(activity) == Visibility.known(other.activity) && tiers == other.tiers

    override fun hashCode(): Int = Visibility.known(activity).hashCode() * HASH_PRIME + tiers.hashCode()

    companion object {
        val PUBLIC = VisibilityChoice()
        private const val HASH_PRIME = 31
    }
}

/**
 * The athlete's default visibilities for new activities and their detail categories: the `actor.activityDefaults` record in their `self`
 * space, so every device they sign in on starts from the same ones. Read with the owner's session and kept for the session; anything that
 * goes wrong reading it (offline, no record, a session without the `space:` scope) means Public, and never stops the athlete publishing.
 * Saves are direct calls, like profile saves: a privacy setting that silently applied later would be the wrong failure.
 */
@SingleIn(SessionScope::class)
@Inject
class ActivityDefaults(private val clients: SessionClients) {
    private val mutex = Mutex()

    /** Whether the athlete can choose anything but Public: not in a session signed in before access to their spaces. */
    val canChoose: Boolean
        get() = clients.canUseSpaces

    /** The defaults read again, as Settings shows them on opening: another device may have changed them. */
    suspend fun refresh(): VisibilityChoice = mutex.withLock {
        read()?.also { current.value = it } ?: current.value ?: VisibilityChoice.PUBLIC
    }

    /** The last defaults read or saved this session; null until [load] has run once. */
    val current: StateFlow<VisibilityChoice?>
        field = MutableStateFlow<VisibilityChoice?>(null)

    /** The defaults, read once per session (again after a failed read). */
    suspend fun load(): VisibilityChoice = mutex.withLock {
        current.value ?: read()?.also { current.value = it } ?: VisibilityChoice.PUBLIC
    }

    /** Saves [choice] (each category capped at the activity) and returns what was saved. Throws if the PDS can't be reached. */
    suspend fun save(choice: VisibilityChoice): VisibilityChoice = mutex.withLock {
        val capped = choice.capped()
        val record = ActivityDefaultsRecord(visibility = capped.activity, details = DetailVisibilities.of(capped.details))
        val space = clients.tiers.ensure(AthleteSpace.SELF)
        clients.spaces.putRecord(
            space,
            Nsid.ACTOR_ACTIVITY_DEFAULTS,
            RKEY,
            AtJson.encodeToJsonElement(ActivityDefaultsRecord.serializer(), record),
        )
        current.value = capped
        capped
    }

    /** The record's choice, Public where it says nothing; null when it can't be read now. */
    private suspend fun read(): VisibilityChoice? {
        val read =
            if (clients.canUseSpaces) {
                runSuspendCatching {
                    clients.spaces.getRecord(clients.tiers.uri(AthleteSpace.SELF), clients.did, Nsid.ACTOR_ACTIVITY_DEFAULTS, RKEY)
                }
                    .getOrNull()
            } else {
                SpaceRead.NotFound
            }
        return when (read) {
            null -> null
            is SpaceRead.Found -> decode(read.value.value)
            SpaceRead.NotFound,
            SpaceRead.NoAccess -> VisibilityChoice.PUBLIC
        }
    }

    /**
     * The record's choice, Public where it says nothing or doesn't decode (any client can write it). A category with no default starts at
     * the activity's, and an old `chartsVisibility` is ignored ([ActivityDefaultsRecord.resolved]).
     */
    private fun decode(value: JsonObject): VisibilityChoice =
        runCatching { AtJson.decodeFromJsonElement(ActivityDefaultsRecord.serializer(), value) }
            .getOrNull()
            ?.resolved()
            ?.let { (activity, details) -> VisibilityChoice(activity, details) } ?: VisibilityChoice.PUBLIC

    private companion object {
        const val RKEY = ActivityDefaultsRecord.RKEY
    }
}
