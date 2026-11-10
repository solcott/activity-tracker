package io.github.solcott.activitytracker.app.divesite

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Creating a dive site from the signed-in athlete's own dive at [diveUri] (dive-sites/site-creation): centred on the dive's entry, with a
 * name and a radius, and a duplicate check before it's published.
 */
@CircuitSerializable(SessionScope::class) data class CreateDiveSiteScreen(val diveUri: String) : Screen

@Immutable
data class CreateDiveSiteState(
    /**
     * The dive, its track and the privacy zones (ui/content-loading): loading or failed until all three first load, then loaded for good,
     * since a later load never moves the site.
     */
    val content: ContentState<Unit> = ContentState(Unit),
    /** What failed while [content] has failed: the dive, its position or the zones. */
    val loadErrorKind: UiError.Kind = UiError.Kind.LoadActivity,
    /** Why no site can be made from this dive; null once it can, or while loading. */
    val unavailable: CreateDiveSiteUnavailable? = null,
    /** The site's radius around the dive's entry as a closed precision-5 polyline, as the maps draw routes; null until it can be made. */
    val outline: String? = null,
    val name: TextFieldState = textFieldStateOf(),
    /** [name] has more than spaces, and isn't longer than [DiveSiteRecord.MAX_NAME_GRAPHEMES]. */
    val named: Boolean = false,
    /** Meters, [DiveSiteRecord.MIN_RADIUS] to [DiveSiteRecord.MAX_RADIUS]. */
    val radius: Int = DiveSiteRecord.DEFAULT_RADIUS,
    /** The dive and its Map category are public, so the site names it as its source. */
    val namesDive: Boolean = false,
    /** Existing sites the position duplicates, found by the last check; publishing is refused while there are any. */
    val duplicates: ImmutableList<DiveSiteView> = persistentListOf(),
    /** The athlete has created as many sites as they may today. */
    val limitReached: Boolean = false,
    /** Checking and publishing. */
    val publishing: Boolean = false,
    /** Why the last check or publish failed; never a loading failure, which is [content]'s. */
    val actionError: UiError? = null,
    val eventSink: (CreateDiveSiteEvent) -> Unit = {},
) : CircuitUiState {
    /** A site that can be made, a name, and nothing pending. */
    val canPublish: Boolean
        get() = unavailable == null && outline != null && named && !publishing && !limitReached
}

/** Why a site can't be created from a dive. */
enum class CreateDiveSiteUnavailable {
    /** Its track has no position, such as a logbook dive with no GPS fix. */
    NoPosition,

    /** Its entry lies inside one of the athlete's privacy zones. */
    InsideZone,

    /** Its entry lies within the distance the athlete hides at every activity's start. */
    HiddenStart,

    /** It already visits a live site. */
    AtSite,
}

sealed interface CreateDiveSiteEvent {
    data object Back : CreateDiveSiteEvent

    /** Loads the dive, its position and the zones again after the first load failed. */
    data object Retry : CreateDiveSiteEvent

    /** The radius moved to [meters], kept within [DiveSiteRecord.MIN_RADIUS] to [DiveSiteRecord.MAX_RADIUS]. */
    data class RadiusChanged(val meters: Int) : CreateDiveSiteEvent

    /** Checks the position for duplicates and today's allowance, then publishes the site and returns to the dive. */
    data object Publish : CreateDiveSiteEvent

    /** Opens an existing site the position duplicates. */
    data class OpenSite(val uri: AtUri) : CreateDiveSiteEvent
}
