package io.github.solcott.activitytracker.data.activity

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.PrivacyCircle
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.activitytracker.location.trim
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The athlete's privacy zones (stored centers, already offset) and how many meters of each route's start and end they hide (0: off). */
data class PrivacySettings(val zones: List<PrivacyZone> = emptyList(), val hideEndsMeters: Int = 0) {
    /** [route] without what these settings hide. */
    fun trim(route: List<LocationSample>): List<LocationSample> =
        TrackMath.trim(
            route,
            zones.map { PrivacyCircle.fromE7(it.latitudeE7, it.longitudeE7, it.radiusMeters) },
            hideEndsMeters.toDouble(),
        )

    /** These settings within the lexicon's bounds: radii and hidden ends clamped, at most [PrivacyZonesRecord.MAX_ZONES] zones. */
    fun clamped(): PrivacySettings =
        PrivacySettings(
            zones.take(PrivacyZonesRecord.MAX_ZONES).map {
                it.copy(radiusMeters = clampRadius(it.radiusMeters), label = it.label?.trim()?.ifEmpty { null })
            },
            hideEndsMeters.coerceIn(0, PrivacyZonesRecord.MAX_METERS),
        )

    companion object {
        val NONE = PrivacySettings()

        fun clampRadius(meters: Int) = meters.coerceIn(PrivacyZonesRecord.MIN_RADIUS_METERS, PrivacyZonesRecord.MAX_METERS)

        fun of(record: PrivacyZonesRecord) = PrivacySettings(record.zones.orEmpty(), record.hideEndsMeters ?: 0).clamped()
    }
}

/** How a route is trimmed when it's written: by settings read from the PDS ([Known]), or not yet known ([Unknown], so not drawn at all). */
sealed interface RouteTrim {
    /** The polyline [route] is published with: trimmed, or none. */
    fun polylineOf(route: List<LocationSample>): String?

    /** [settings] as saved at [revision] (null: never saved, so nothing is hidden). */
    data class Known(val settings: PrivacySettings, val revision: String?) : RouteTrim {
        override fun polylineOf(route: List<LocationSample>): String? = ActivityDraft.polylineOf(settings.trim(route))
    }

    /** The zones couldn't be read this session: the route is written without a polyline, never an untrimmed one. */
    data object Unknown : RouteTrim {
        override fun polylineOf(route: List<LocationSample>): String? = null
    }
}

/** How a publish trims its routes: [trim], and whether that was read fresh just now rather than a fallback. */
class PublishTrim(val trim: RouteTrim, internal val fresh: Boolean)

/** An activity published this session without a fresh read of the zones, kept so it can be trimmed again once they're read. */
data class StaleRoute(val uri: AtUri, val visibility: String, val route: List<LocationSample>, val trimmedWith: RouteTrim)

/** An athlete already has [PrivacyZonesRecord.MAX_ZONES] zones. */
class TooManyZonesException : IllegalStateException("At most ${PrivacyZonesRecord.MAX_ZONES} privacy zones")

/** Zones can't be shown or changed: they couldn't be read, and saving over them from nothing would drop them. */
class ZonesUnreadableException : IllegalStateException("Couldn't read your privacy zones")

/**
 * The athlete's privacy zones: the `actor.privacyZones` record in their `self` space, which every polyline is trimmed by before it's
 * written. Read fresh for every publish ([forPublish]), since a stale read publishes a route the athlete has since hidden; when a read
 * fails it falls back to the last read this session, and with none to [RouteTrim.Unknown], so an unreadable zone set hides the route rather
 * than showing it. Activities published without a fresh read are remembered ([takeStale]) to be trimmed again by the zones once they're
 * read. Saves are direct calls, like [ActivityDefaults]: a privacy setting that silently applied later would be the wrong failure.
 */
@SingleIn(SessionScope::class)
@Inject
class PrivacyZones(private val clients: SessionClients, private val clock: Clock, logger: Logger) {
    private val log = logger.withTag("PrivacyZones")
    private val mutex = Mutex()
    private var last: RouteTrim.Known? = null
    private var batch: PublishTrim? = null
    private val stale = mutableMapOf<String, StaleRoute>()

    /** Random for new zones' offsets; replaced in tests. */
    internal var random: Random = Random.Default

    /** The last settings read or saved this session; null until one has been. */
    val current: StateFlow<PrivacySettings?>
        field = MutableStateFlow<PrivacySettings?>(null)

    /** The revision of this session's last save; null before one. [ActivityRepository.repairRoutesOnSave] re-trims on each. */
    val saved: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    /** Whether this session can save zones: not one signed in before its `space:` grant listed them. */
    val canSave: Boolean
        get() = clients.canWriteToSpaces(Nsid.ACTOR_PRIVACY_ZONES)

    /** How to trim a route published now: the zones read fresh, or the batch's ([withReadOnce]). */
    suspend fun forPublish(): PublishTrim = batch ?: mutex.withLock { read().let { (trim, fresh) -> PublishTrim(trim, fresh) } }

    /** Runs [block] with the zones read once for every publish in it, as a logbook import queues hundreds. */
    suspend fun <T> withReadOnce(block: suspend () -> T): T {
        batch = mutex.withLock { read().let { (trim, fresh) -> PublishTrim(trim, fresh) } }
        try {
            return block()
        } finally {
            batch = null
        }
    }

    /**
     * Remembers [uri], published at [visibility] with [with], to trim again by the zones once they're read ([takeStale]), unless they were
     * read fresh for it.
     */
    fun published(uri: AtUri, visibility: String, route: List<LocationSample>, with: PublishTrim) {
        if (route.size < 2 || with.fresh) return
        stale[requireNotNull(uri.rkey)] = StaleRoute(uri, visibility, route, with.trim)
    }

    /**
     * The activities this session published by zones other than [latest], removed from the list: [RouteTrimmer] trims them again. Ones
     * trimmed by [latest] already are dropped.
     */
    fun takeStale(latest: RouteTrim.Known): List<StaleRoute> {
        val taken = stale.values.filter { it.trimmedWith != latest }
        stale.clear()
        return taken
    }

    /** Whether any activity waits to be trimmed again ([takeStale]). */
    val hasStale: Boolean
        get() = stale.isNotEmpty()

    /**
     * The settings read now, as Settings shows them on opening. Throws [ZonesUnreadableException] when nothing can be read this session.
     */
    suspend fun refresh(): RouteTrim.Known = mutex.withLock { read().first as? RouteTrim.Known ?: throw ZonesUnreadableException() }

    /** The zones as the PDS holds them now; null when they can't be read (offline, or a record that doesn't decode). */
    internal suspend fun readFresh(): RouteTrim.Known? = mutex.withLock { read().takeIf { (_, fresh) -> fresh }?.first as? RouteTrim.Known }

    /**
     * Saves [settings] (clamped) under a new revision and returns them. Zones keep the centers they have; add new ones with [add], which
     * offsets them. Throws if the PDS can't be reached, keeping the zones saved before, and refuses a session that can't write them.
     */
    suspend fun save(settings: PrivacySettings): PrivacySettings = mutex.withLock { write(settings.clamped()) }

    /**
     * Adds a zone of [radiusMeters] around ([latitude], [longitude]), its stored center offset by up to half the radius, to the zones as
     * read now. Throws [TooManyZonesException] when there are already [PrivacyZonesRecord.MAX_ZONES], and [ZonesUnreadableException] when
     * they can't be read.
     */
    suspend fun add(latitude: Double, longitude: Double, radiusMeters: Int, label: String? = null): PrivacySettings = mutex.withLock {
        val now = read().takeIf { (_, fresh) -> fresh }?.first as? RouteTrim.Known ?: throw ZonesUnreadableException()
        if (now.settings.zones.size >= PrivacyZonesRecord.MAX_ZONES) throw TooManyZonesException()
        val radius = PrivacySettings.clampRadius(radiusMeters)
        val (lat, lon) = PrivacyCircle.offsetCenter(latitude, longitude, radius.toDouble(), random)
        val zone = PrivacyZone(PrivacyCircle.toE7(lat), PrivacyCircle.toE7(lon), radius, label?.trim()?.ifEmpty { null })
        write(now.settings.copy(zones = now.settings.zones + zone).clamped())
    }

    private suspend fun write(settings: PrivacySettings): PrivacySettings {
        clients.requireSpacesFor(Visibility.SELF, Nsid.ACTOR_PRIVACY_ZONES)
        val revision = Tid.next(clock)
        val record = PrivacyZonesRecord(revision, settings.zones.ifEmpty { null }, settings.hideEndsMeters.takeIf { it > 0 })
        val space = clients.tiers.ensure(AthleteSpace.SELF)
        clients.spaces.putRecord(space, Nsid.ACTOR_PRIVACY_ZONES, RKEY, AtJson.encodeToJsonElement(PrivacyZonesRecord.serializer(), record))
        remember(RouteTrim.Known(settings, revision))
        saved.value = revision
        return settings
    }

    /** The zones now, and whether that's a fresh read rather than the last one this session or [RouteTrim.Unknown]. */
    private suspend fun read(): Pair<RouteTrim, Boolean> {
        if (!clients.canUseSpaces) return RouteTrim.Known(PrivacySettings.NONE, null).also(::remember) to true
        val read = runSuspendCatching {
            clients.spaces.getRecord(clients.tiers.uri(AthleteSpace.SELF), clients.did, Nsid.ACTOR_PRIVACY_ZONES, RKEY)
        }
            .onFailure { e -> log.w(e) { "Couldn't read the athlete's privacy zones" } }
            .getOrNull()
        val known =
            when (read) {
                is SpaceRead.Found ->
                    runCatching { AtJson.decodeFromJsonElement(PrivacyZonesRecord.serializer(), read.value.value) }
                        .getOrNull()
                        ?.let { RouteTrim.Known(PrivacySettings.of(it), it.revision) }
                SpaceRead.NotFound -> RouteTrim.Known(PrivacySettings.NONE, null)
                // A record another client wrote that doesn't decode, a refusal, or a failed read: hide rather than guess.
                SpaceRead.NoAccess,
                null -> null
            }
        return if (known != null) known.also(::remember) to true else (last ?: RouteTrim.Unknown) to false
    }

    private fun remember(known: RouteTrim.Known) {
        last = known
        current.value = known.settings
    }

    private companion object {
        const val RKEY = PrivacyZonesRecord.RKEY
    }
}
