package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.ShownRoute
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.interactions.CommentsPreview
import io.github.solcott.activitytracker.app.interactions.LikesList
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GroupPartner
import io.github.solcott.activitytracker.lexicon.app.MemberView
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf

/**
 * @property suggestOnOpen the activity was saved moments ago: a combine suggestion for it shows as a prompt
 *   ([CombineSuggestionState.asPrompt]) rather than a banner, and the page waits for the AppView to index it.
 */
@CircuitSerializable(SessionScope::class) data class ActivityDetailScreen(val uri: String, val suggestOnOpen: Boolean = false) : Screen

/**
 * The activity page (ui/content-loading): [content]'s status and origin are the activity's load, and its data the page built from it and
 * the page's other loads, null until the activity first loads.
 */
@Immutable
data class ActivityDetailState(
    val content: ContentState<ActivityDetailPage?> = ContentState(null),
    val eventSink: (ActivityDetailEvent) -> Unit,
) : CircuitUiState

/** The loaded activity page, as the viewer left it. */
@Immutable
data class ActivityDetailPage(
    /**
     * Its comment count already moved by the viewer's own comment changes, here or on its Comments screen, the AppView hasn't caught up
     * with.
     */
    val activity: ActivityView,
    /** The comments block after the likes (feed/comments): the count and the first comments, which open the Comments screen. */
    val comments: CommentsPreview,
    /** The signed-in athlete. */
    val viewerDid: Did,
    /** The activity is the signed-in athlete's own, so it can't be liked. */
    val isOwn: Boolean,
    /** Who liked it, once the viewer has shown the list; null while it's hidden. */
    val likesList: LikesList?,
    /** Why the viewer's last like couldn't be given: a session that can't like a non-public activity is told to sign in again. */
    val likeError: UiError? = null,
    /** Why blocking the activity's author from the report sheet failed. */
    val blockError: UiError? = null,
    val eventSink: (ActivityDetailEvent) -> Unit,
    /** Null while loading, and when there are no streams or the viewer may not see them. */
    val charts: ActivityCharts? = null,
    /** A pool swim's pool length, published with the activity, so shown to anyone who may see it; null for anything else. */
    val poolLength: PoolLength? = null,
    /** The viewer's own activity has heart rate or power but no zones, because they hadn't set thresholds. */
    val chartsMissingZones: Boolean = false,
    /**
     * On the owner's own activity, each detail category with data stored narrower than the activity, with its tier, so its stats and charts
     * show a lock; empty on anyone else's.
     */
    val narrowerCategories: ImmutableList<CategoryVisibility> = persistentListOf(),
    /** The owner approves followers, so Followers means only the ones they approved. */
    val approvesFollowers: Boolean = false,
    /** The sections this activity has data for, in the order they show. */
    val sections: ImmutableList<DetailSection> = persistentListOf(),
    /** What the page shows of the activity's data: its hero, cards, training load, stats and avg/max table. */
    val page: ActivityPageContent = ActivityPageContent(),
    /** What the owner can do to the activity; null on someone else's. */
    val manage: ActivityManage? = null,
    /**
     * What the map draws: for the owner, once read, their full route untrimmed by their privacy zones, marked where the route others see
     * starts and ends; else the record's trimmed polyline. Null with no route.
     */
    val route: ShownRoute? = null,
    /** The owner can add a privacy zone at the route's start or end: the track was read. */
    val canHideRouteEnds: Boolean = false,
    /** A zone was just added from this activity. */
    val zoneAdded: Boolean = false,
    val routeError: UiError? = null,
    /**
     * Exporting the activity as a file; null on someone else's activity, or when it has no track or streams to rebuild a file from and no
     * original file.
     */
    val export: ActivityExportState? = null,
    /** On the owner's combined activity, where each kind of its data comes from, to choose or leave out; null otherwise. */
    val dataSources: DataSources? = null,
    /** On a combined activity, the members each card's data came from, in time order; empty otherwise. */
    val chartSources: ImmutableList<ChartSource> = persistentListOf(),
    /** On the owner's recent activity, another of theirs it likely belongs with in one combined activity; null otherwise. */
    val combineSuggestion: CombineSuggestionState? = null,
    /** Report on someone else's activity, its photos and comments. */
    val reporting: Reporting = Reporting(),
    /** Its blurred photos, or the page of a hidden activity, once the viewer chose to see them on this screen. */
    val revealed: Revealed = Revealed(),
    /**
     * Its efforts on runs the viewer may see, grouped by overlapping time spans ([groupEfforts]); empty while loading, when it has none, or
     * when they failed.
     */
    val segments: ImmutableList<SegmentEffortGroup> = persistentListOf(),
    /** The ski lifts it rode that the viewer may see ([liftRidesOf]), apart from its runs; empty as [segments] is. */
    val lifts: ImmutableList<LiftRides> = persistentListOf(),
    /**
     * The owner can create a segment from the activity (segments/segment-creation): it has a full track, its sport has a segment sport
     * group, and their privacy zones were read.
     */
    val canCreateSegment: Boolean = false,
    /** A segment was just published from this activity; it shows once the AppView has processed it. */
    val segmentCreated: Boolean = false,
    /**
     * The owner can create a dive site from the dive (dive-sites/site-creation): its track has a position, their privacy zones were read,
     * and it visits no live site.
     */
    val canCreateDiveSite: Boolean = false,
    /** A dive site was just published from this dive; it shows once the AppView has processed it. */
    val diveSiteCreated: Boolean = false,
    /** The owner can trim the activity's start or end (feed/activity-trim): it isn't combined, a member, multisport, a dive or sets. */
    val canTrim: Boolean = false,
    /**
     * Other athletes' activities done together with this one that the viewer may see (feed/group-activities), most recently paired first;
     * empty when there are none, or the owner just left the group here.
     */
    val partners: ImmutableList<GroupPartner> = persistentListOf(),
    /**
     * On the owner's own activity, leaving its group while it lists a partner, or rejoining once it left; null on anyone else's, on one
     * done alone, or when it can't be offered now.
     */
    val ownGroup: OwnGroupState? = null,
    /** The owner may add people who follow them to this activity (feed/activity-invites). */
    val canAddPeople: Boolean = false,
    /** The owner's own copy of another athlete's activity, accepted from an invite: Leave group drops its link to the original. */
    val isOwnCopy: Boolean = false,
    /** Leaving the group on the owner's copy is being written. */
    val leavingCopyGroup: Boolean = false,
    /** Why leaving the group on the owner's copy failed. */
    val copyGroupError: UiError? = null,
    /**
     * The chart cards shown open; the rest show as strips. Null until the viewer opens or closes one, when only the lead (the first chart
     * of [ActivityPageContent.cards]) is open. Native views ask [isOpen], since a set of enums can't reach Swift.
     */
    val openCards: ImmutableSet<DetailSection>? = null,
    /** On a wide window, the side pane shows All data, from this section, in place of the page's summary; null while it doesn't. */
    val allData: DataSection? = null,
) {
    /** Whether the page has a Together with section: partners, or something for the owner to do about them. */
    val showsGroup: Boolean
        get() = partners.isNotEmpty() || ownGroup != null || canAddPeople || isOwnCopy

    /** Whether [card] shows open rather than as a strip; a table always shows open. */
    fun isOpen(card: MetricCard): Boolean = card.section.isTable || (openCards?.contains(card.section) ?: (card.section == lead))

    /** The card that opens with the page: its first chart in the sport's order. */
    val lead: DetailSection?
        get() = page.cards.firstOrNull { !it.section.isTable }?.section

    /** What All data shows, for the side pane while [allData] is set. */
    val data: ActivityData
        get() = ActivityData.of(page, chartSources, allData)
}

/**
 * Leaving or rejoining the group on the owner's own activity (feed/group-activities): offered while their grouping is on and its segment
 * track is stored, Leave group only while the activity lists a partner and Rejoin group whenever it left.
 *
 * @property left the activity left its group, as the owner last chose it here or as stored.
 * @property busy queueing the change.
 */
@Immutable data class OwnGroupState(val left: Boolean, val busy: Boolean = false, val error: UiError? = null)

/**
 * Another of the owner's activities this one likely belongs with (`feed/combine-suggestions`), until they combine them or say not to.
 *
 * @property cue what it looks like: the same activity on another device, a device switch, a shuttle or lift, or a break.
 * @property other what combining joins this activity to: the other activity, or the combined one it's a member of.
 * @property asPrompt asked right after a save, with Combine and Not now ([ActivityDetailEvent.CombineSuggested],
 *   [ActivityDetailEvent.NotNowSuggestion]); otherwise a banner with Combine and Don't combine
 *   ([ActivityDetailEvent.DontCombineSuggestion]).
 * @property busy combining, or saving the dismissal.
 */
@Immutable
data class CombineSuggestionState(
    val cue: CombineCue,
    val other: ActivityView,
    val asPrompt: Boolean,
    val busy: Boolean = false,
    val error: UiError? = null,
)

/**
 * Where each kind of a combined activity's data comes from (`feed/combined-sources`), on its owner's page: every kind any member recorded,
 * included by default.
 */
@Immutable
data class DataSources(
    /** In [DataKind] order; empty while loading. */
    val kinds: ImmutableList<DataSourceRow> = persistentListOf(),
    val isLoading: Boolean = false,
    /** The kind whose rebuild is running. */
    val saving: DataKind? = null,
    /** Why the sources couldn't be read, or the last choice couldn't be saved; [ActivityDetailEvent.RetrySources] tries it again. */
    val error: UiError? = null,
)

/** One kind of a combined activity's data, as its owner chooses it. */
@Immutable
data class DataSourceRow(
    val kind: DataKind,
    /** It's part of the combined activity; off when the owner left it out. */
    val included: Boolean,
    /** The member it comes from; null when it's left out. */
    val chosen: MemberView?,
    /** The members that recorded it, to choose from; empty when only one did. */
    val offered: ImmutableList<MemberView>,
)

/** The members [section]'s card on a combined activity came from, in time order, so its caption names their devices. */
@Immutable data class ChartSource(val section: DetailSection, val members: ImmutableList<MemberView>)

/** Deleting or ungrouping the signed-in athlete's own activity. */
@Immutable
data class ActivityManage(
    /** The other recordings of this activity, which deleting it can take too or leave as activities of their own. */
    val otherRecordings: Int,
    /** The activity is grouped with exactly one other, its main activity or its one recording, so the two can be split apart. */
    val canUngroup: Boolean,
    /** A combined activity's members, which deleting it can take too or leave as activities of their own; 0 otherwise. */
    val members: Int = 0,
    /** Another activity, or a recording from a file, can be combined with this one: it isn't a member, and has room for another member. */
    val canCombine: Boolean = false,
    /** A delete or ungroup is running. */
    val busy: Boolean,
    val error: UiError?,
)

/**
 * Exporting the signed-in athlete's own activity as a file rebuilt from its record, streams and full track, or as the [original] file it
 * was imported from.
 */
@Immutable
data class ActivityExportState(
    /** What the stored data allows, in [ExportFormat] order; empty only when there's an [original]. */
    val formats: ImmutableList<ExportFormat>,
    /** An export is being built. */
    val busy: Boolean = false,
    /** Why the last export failed: not sent yet ([UiError.Kind.ExportNotSent]) or unreadable. */
    val error: UiError? = null,
    /** The file built, waiting for the UI to hand to the platform and send [ActivityDetailEvent.ExportHandedOff]. */
    val file: ExportedFile? = null,
    /** The activity's streams hold laps, so the file has them and the note doesn't say it lacks them. */
    val hasLaps: Boolean = false,
    /** The file the activity was imported from, stored or still queued; null for one recorded in the app or entered by hand. */
    val original: OriginalFile? = null,
)

/** An imported activity's original file: its [name] as picked and its [format] (a `feed.activitySource#format` token). */
@Immutable data class OriginalFile(val name: String, val format: String)

sealed interface ActivityDetailEvent {
    data object Back : ActivityDetailEvent

    data object Retry : ActivityDetailEvent

    data object ToggleLike : ActivityDetailEvent

    data object OpenAuthor : ActivityDetailEvent

    /** Shows or hides who liked it; showing it reloads the list. */
    data object ToggleLikesList : ActivityDetailEvent

    data class OpenProfile(val did: Did) : ActivityDetailEvent

    /** Opens another recording of this activity, or the activity this one is a recording of. */
    data class OpenActivity(val uri: AtUri) : ActivityDetailEvent

    /** On the owner's own activity, leaves its group: no one is listed with it, and it isn't paired again until [RejoinGroup]. */
    data object LeaveGroup : ActivityDetailEvent

    /** On the owner's own activity, rejoins its group, so it can be paired again. */
    data object RejoinGroup : ActivityDetailEvent

    /** On the owner's own activity, opens the followers they may add to it. */
    data object AddPeople : ActivityDetailEvent

    /** On the owner's own copy of another's activity, leaves its group for good: the copy no longer names the original. */
    data object LeaveCopyGroup : ActivityDetailEvent

    /** Opens the activity's Comments screen, its field focused when the viewer chose to add a comment ([focus]). */
    data class OpenComments(val focus: Boolean = false) : ActivityDetailEvent

    /** Opens the training zones screen, to set the thresholds zone charts need. */
    data object OpenTrainingZones : ActivityDetailEvent

    /** Opens the page of the gear item at [uri], one the activity lists. */
    data class OpenGear(val uri: String) : ActivityDetailEvent

    /** The owner deletes the activity, confirmed, and its other recordings too [withRecordings] or else leaves them as activities. */
    data class Delete(val withRecordings: Boolean) : ActivityDetailEvent

    /** The owner splits the two activities of a group apart: see [ActivityManage.canUngroup]. */
    data object Ungroup : ActivityDetailEvent

    /** The owner picks another of their activities to combine with this one ([CombinePickerScreen]). */
    data object Combine : ActivityDetailEvent

    /** The owner picks a file, another device's recording of this activity, to combine with it (`ImportActivityScreen.combineInto`). */
    data object AddRecording : ActivityDetailEvent

    /** The owner takes [uri] out of this combined activity, which is rebuilt without it, or split when one member would be left. */
    data class RemoveMember(val uri: AtUri) : ActivityDetailEvent

    /** The owner splits this combined activity back into its members. */
    data object Split : ActivityDetailEvent

    /** The owner accepts [ActivityDetailPage.combineSuggestion]: the page then shows the combined activity. */
    data object CombineSuggested : ActivityDetailEvent

    /** The owner puts the suggestion prompt off: it stays on the page as a banner. */
    data object NotNowSuggestion : ActivityDetailEvent

    /** The owner says not to combine the suggested pair: neither activity's page suggests it again. */
    data object DontCombineSuggestion : ActivityDetailEvent

    /** The owner takes [kind] of this combined activity's data from [member], the others filling in where it has none. */
    data class ChooseSource(val kind: DataKind, val member: AtUri) : ActivityDetailEvent

    /** The owner includes [kind] of this combined activity's data again, ranked by default, or leaves it out. */
    data class SetIncluded(val kind: DataKind, val included: Boolean) : ActivityDetailEvent

    /** Reads the data sources again, or repeats the choice that failed: see [DataSources.error]. */
    data object RetrySources : ActivityDetailEvent

    /** The owner edits the activity's title, description, dive site and photos. */
    data object Edit : ActivityDetailEvent

    /** The owner adds a privacy zone at the route's start: see [ActivityDetailPage.canHideRouteEnds]. */
    data object HideStart : ActivityDetailEvent

    /** The owner adds a privacy zone at the route's end. */
    data object HideEnd : ActivityDetailEvent

    /** Opens the activity's media full screen at [index]. */
    data class OpenMedia(val index: Int) : ActivityDetailEvent

    /** Opens the route on a full-screen map the viewer can pan and zoom. */
    data object OpenMap : ActivityDetailEvent

    /** Opens the segment [uri] of one of the activity's efforts; [eBike] when that effort was an e-bike ride's. */
    data class SegmentOpened(val uri: AtUri, val eBike: Boolean = false) : ActivityDetailEvent

    /** The owner creates a segment from the activity's route ([ActivityDetailPage.canCreateSegment]). */
    data object CreateSegment : ActivityDetailEvent

    /** The owner creates a dive site from the dive's entry ([ActivityDetailPage.canCreateDiveSite]). */
    data object CreateDiveSite : ActivityDetailEvent

    /** The owner trims the activity's start or end ([ActivityDetailPage.canTrim]). */
    data object Trim : ActivityDetailEvent

    /** Opens the dive site the dive visits (`activityView.diveSite`). */
    data object OpenDiveSite : ActivityDetailEvent

    /** The owner exports the activity as [format]: see [ActivityExportState]. */
    data class Export(val format: ExportFormat) : ActivityDetailEvent

    /** The owner downloads the file the activity was imported from ([ActivityExportState.original]), byte for byte. */
    data object DownloadOriginal : ActivityDetailEvent

    /** The UI handed [ActivityExportState.file] to the platform, whether it was shared, saved or cancelled. */
    data object ExportHandedOff : ActivityDetailEvent

    /** The UI couldn't hand [ActivityExportState.file] to the platform: it was never offered, and [ActivityExportState.error] says so. */
    data object ExportShareFailed : ActivityDetailEvent

    /** Opens [section]'s chart card in place of its strip, or closes it back to one. */
    data class ToggleCard(val section: DetailSection) : ActivityDetailEvent

    /**
     * Opens All data at [section] (the top when null): in the side pane when the window has one ([inPane]), else as its own page
     * ([ActivityDataScreen]).
     */
    data class OpenAllData(val section: DataSection?, val inPane: Boolean) : ActivityDetailEvent

    /** Closes All data in the side pane, showing the page's summary again. */
    data object CloseAllData : ActivityDetailEvent
}
