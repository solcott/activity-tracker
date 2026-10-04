package io.github.solcott.activitytracker.data.units

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The signed-in athlete's unit preference, stored as `units` on their `actor.profile` record so it follows them across devices. Until
 * [refresh] reads the record (or when it has no preference), [units] is [default], which comes from the device region.
 */
@SingleIn(SessionScope::class)
@Inject
class UnitsRepository(
    private val profiles: ProfileRepository,
    private val clock: Clock,
    logger: Logger,
    private val default: UnitSystem = UnitSystem.defaultFor(deviceRegion()),
) {
    private val log = logger.withTag("UnitsRepository")

    val units: StateFlow<UnitSystem>
        field = MutableStateFlow(default)

    /** Loads the preference from the profile record; on failure keeps the current value. */
    suspend fun refresh() {
        runSuspendCatching { profiles.ownProfileRecord() }
            .onSuccess { record -> UnitSystem.fromToken(record?.units)?.let { units.value = it } }
            .onFailure { e -> log.w(e) { "Couldn't load unit preference" } }
    }

    /**
     * Saves [value] to the profile record. Updates [units] optimistically and reverts if the write fails, then rethrows. Reads the record
     * first because `putRecord` replaces it whole, and the other fields (display name, bio) must survive.
     */
    suspend fun set(value: UnitSystem) {
        val previous = units.value
        units.value = value
        runSuspendCatching {
            val record = profiles.ownProfileRecord() ?: ProfileRecord(createdAt = clock.now())
            profiles.saveProfile(record.copy(units = value.token))
        }
            .onFailure { e ->
                units.value = previous
                throw e
            }
    }
}
