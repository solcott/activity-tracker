package io.github.solcott.activitytracker.app.divesite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.PublishCheck
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.checkThenPublish
import io.github.solcott.activitytracker.app.formContent
import io.github.solcott.activitytracker.app.rememberActivityRouteLoad
import io.github.solcott.activitytracker.app.rememberFormSeed
import io.github.solcott.activitytracker.app.rememberPublishChecks
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.mapIsPublic
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.divesites.DiveSitePublisher
import io.github.solcott.activitytracker.data.divesites.DiveSiteRepository
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.PrivacyCircle
import io.github.solcott.activitytracker.location.siteOutline
import io.github.solcott.activitytracker.location.toE7
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd

@CircuitInject(CreateDiveSiteScreen::class, SessionScope::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // Loading the dive, its position and zones, then the form and publishing.
fun CreateDiveSitePresenter(
    screen: CreateDiveSiteScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    privacy: PrivacyZones,
    sites: DiveSiteRepository,
    publisher: DiveSitePublisher,
): CreateDiveSiteState {
    val uri = AtUri(screen.diveUri)
    // The zones are read fresh, since they decide whether a position that will be public may be.
    val load = rememberActivityRouteLoad(uri, feed, activities, privacy)
    val name = rememberTextFieldState()
    // The logbook's own site name, when it has one, starts the name.
    val seed = rememberFormSeed(load.content) { it.activity.effectiveRecord().dive?.site?.let(name::setTextAndPlaceCursorAtEnd) }
    var radius by rememberRetained { mutableIntStateOf(DiveSiteRecord.DEFAULT_RADIUS) }
    val checks = rememberPublishChecks<DiveSiteView>()
    val flights = rememberSingleFlight()

    val activity = seed?.activity
    // The site's centre is the dive's entry: its track's first position.
    val entry = seed?.track?.firstOrNull()
    val unavailable = seed?.let { unavailableFor(it.activity, entry, it.zones) }
    val latE7 = entry?.latitude?.toE7()
    val lngE7 = entry?.longitude?.toE7()
    val outline =
        remember(unavailable, latE7, lngE7, radius) {
            if (unavailable == null && latE7 != null && lngE7 != null) siteOutline(latE7, lngE7, radius) else null
        }
    val namesDive = remember(activity) { activity?.let(::mapIsPublic) == true }
    // Read here so typing a name gives a new state.
    val title = name.text.toString().trim()

    return CreateDiveSiteState(
        content = load.content.formContent(seeded = seed != null),
        loadErrorKind = load.loadErrorKind(UiError.Kind.LoadDivePosition),
        unavailable = unavailable,
        outline = outline,
        name = name,
        named = title.isNotEmpty() && title.length <= DiveSiteRecord.MAX_NAME_GRAPHEMES,
        radius = radius,
        namesDive = namesDive,
        duplicates = checks.duplicates,
        limitReached = checks.limitReached,
        publishing = flights.isRunning(CreateDiveSiteEvent.Publish),
        actionError = checks.error,
    ) { event ->
        when (event) {
            CreateDiveSiteEvent.Back -> navigator.pop()
            CreateDiveSiteEvent.Retry -> load.retry()
            is CreateDiveSiteEvent.RadiusChanged -> radius = event.meters.coerceIn(DiveSiteRecord.MIN_RADIUS, DiveSiteRecord.MAX_RADIUS)
            is CreateDiveSiteEvent.OpenSite -> navigator.goTo(DiveSiteScreen(event.uri.value))
            CreateDiveSiteEvent.Publish -> {
                val dive = activity ?: return@CreateDiveSiteState
                if (unavailable != null || latE7 == null || lngE7 == null) return@CreateDiveSiteState
                if (title.isEmpty() || title.length > DiveSiteRecord.MAX_NAME_GRAPHEMES) return@CreateDiveSiteState
                val source = StrongRef(dive.uri, dive.cid).takeIf { namesDive }
                flights.checkThenPublish(
                    key = CreateDiveSiteEvent.Publish,
                    checks = checks,
                    check = { sites.check(latE7, lngE7).let { PublishCheck(it.duplicates, it.remaining) } },
                    checkError = UiError.Kind.CheckDiveSite,
                    publish = { publisher.create(uri, title, latE7, lngE7, radius, source) },
                    publishError = UiError.Kind.PublishDiveSite,
                    onPublished = { navigator.pop() },
                )
            }
        }
    }
}

/**
 * Why [dive] can't give a site, if it can't: it already visits a live one, it has no [entry], or the entry is one the athlete's privacy
 * settings hide (inside a zone, or at the start of the track, which their hidden start distance always covers).
 */
private fun unavailableFor(dive: ActivityView, entry: LocationSample?, zones: PrivacySettings): CreateDiveSiteUnavailable? =
    when {
        dive.diveSite?.let { SegmentStatus.of(it.status) in LIVE } == true -> CreateDiveSiteUnavailable.AtSite
        entry == null -> CreateDiveSiteUnavailable.NoPosition
        zones.zones.any {
            PrivacyCircle.fromE7(it.latitudeE7, it.longitudeE7, it.radiusMeters).contains(entry.latitude, entry.longitude)
        } -> CreateDiveSiteUnavailable.InsideZone
        zones.hideEndsMeters > 0 -> CreateDiveSiteUnavailable.HiddenStart
        else -> null
    }

private val LIVE = setOf(SegmentStatus.PROPOSED, SegmentStatus.ACTIVE)
