package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.RouteMap
import io.github.solcott.activitytracker.app.activitylist.LockLine
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.segment.PlaceTexts
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StartText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.activityType
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** What an owner's menu item does: one that sends its [ActivityMenuItem.event] at once, or one the UI asks about first. */
enum class ActivityMenuAction {
    Edit,
    Combine,
    AddRecording,
    Split,
    Ungroup,
    HideStart,
    HideEnd,
    CreateSegment,
    CreateDiveSite,
    Export,
    Delete,
}

/**
 * An item of the owner's "…" menu: its [label], what it does, and the [event] it sends when chosen; null for Split, Export and Delete,
 * which the UI confirms or opens a sheet for first. [destructive] marks Delete.
 */
@Immutable
class ActivityMenuItem(
    val label: AppText,
    val action: ActivityMenuAction,
    val event: ActivityDetailEvent?,
    val destructive: Boolean = false,
)

/** A button of the delete confirmation: its [label] and the [event] it sends. */
@Immutable class ActivityDeleteChoice(val label: AppText, val event: ActivityDetailEvent)

/**
 * What the activity page says beyond its figures (feed/activity-page-layout, dev/shared-display-text), and the conditions both UIs ask: the
 * like and likers buttons, Together with, the owner's and others' menus, the delete and split confirmations, the export sheet, the notices,
 * the dive site line, the recordings and members, and the combine suggestion. The Compose page and the native one resolve these.
 */
@Suppress("TooManyFunctions") // One per line of the page, so both UIs ask the same.
object ActivityPageTexts {
    val title: AppText = AppText.named("detail_title")
    val more: AppText = AppText.named("detail_more")

    /** "Like (3)" or "Liked (3)", on someone else's activity. */
    fun likeButton(activity: ActivityView): AppText =
        AppText.named(if (activity.viewer?.like != null) "detail_liked" else "detail_like", AppText.written("${activity.likeCount}"))

    /** "3 likes", on the owner's own. */
    fun likeCount(activity: ActivityView): AppText =
        AppText.Plural("like_count", activity.likeCount.toInt(), persistentListOf(AppText.written(formatWhole(activity.likeCount))))

    /** Whether the page offers Show likes: someone liked it, or the list is open (hiding stays offered if the last like goes). */
    fun showsLikesToggle(page: ActivityDetailPage): Boolean = page.activity.likeCount > 0 || page.likesList != null

    fun likesToggle(shown: Boolean): AppText = AppText.named(if (shown) "detail_hide_likes" else "detail_show_likes")

    val togetherWith: AppText = AppText.named("detail_together_with")
    val groupLeft: AppText = AppText.named("detail_group_left")

    /** Leave group, or Rejoin group once the owner left it. */
    fun groupButton(own: OwnGroupState): AppText = AppText.named(if (own.left) "detail_group_rejoin" else "detail_group_leave")

    /** What the group button sends. */
    fun groupEvent(own: OwnGroupState): ActivityDetailEvent =
        if (own.left) ActivityDetailEvent.RejoinGroup else ActivityDetailEvent.LeaveGroup

    val leaveCopyGroup: AppText = AppText.named("detail_leave_copy_group")
    val addPeople: AppText = AppText.named("detail_add_people")

    /** What a write from the page just did: a privacy zone added, a segment or dive site published. */
    fun notices(page: ActivityDetailPage): List<AppText> =
        listOfNotNull(
            AppText.named("detail_zone_added").takeIf { page.zoneAdded },
            AppText.named("detail_segment_created").takeIf { page.segmentCreated },
            AppText.named("detail_dive_site_created").takeIf { page.diveSiteCreated },
        )

    /**
     * The dive's site (dive-sites/site-visits): the site it visits, by its current name and its status unless active, which opens its page
     * ([opensDiveSite]); or the logbook's own site text when it visits none the viewer may see; null for neither.
     */
    fun diveSite(activity: ActivityView): AppText? {
        val visited = activity.diveSite
        if (visited != null) {
            val name = AppText.written(visited.name)
            return PlaceTexts.status(visited.status)?.let { AppText.named("text_dot_pair", name, it) } ?: name
        }
        return activity.record.dive?.site?.let(AppText::written)
    }

    /** The dive site line opens the site's page: the dive visits one the viewer may see. */
    fun opensDiveSite(activity: ActivityView): Boolean = activity.diveSite != null

    /** The page's title line: the activity's title, or its type when untitled. */
    fun heading(activity: ActivityView): AppText =
        activity.record.title?.let(AppText::written) ?: LabelText.activityType(activity.record.activityType)

    /** The type under a titled activity's heading; null when the heading already is the type. */
    fun typeLine(activity: ActivityView): AppText? =
        if (activity.record.title != null) LabelText.activityType(activity.record.activityType) else null

    /** The author, by the name the app shows them. */
    fun author(activity: ActivityView): AppText = AppText.written(activity.author.shownName)

    /** Whether the page waits behind the hidden warning: the labels hide it and the viewer hasn't chosen to see it here. */
    fun waitsHidden(page: ActivityDetailPage): Boolean =
        ModerationDecision.of(page.activity.labels, page.activity.author.labels, page.isOwn) == ModerationDecision.Hidden &&
            Revealed.hiddenKey(page.activity.uri.value) !in page.revealed

    /** The key the hidden warning's View anyway sends to [ActivityDetailPage.revealed]. */
    fun hiddenKey(page: ActivityDetailPage): String = Revealed.hiddenKey(page.activity.uri.value)

    val hiddenTitle: AppText = AppText.named("label_hidden_title")
    val hiddenMessage: AppText = AppText.named("label_hidden_message")
    val hiddenShow: AppText = AppText.named("label_hidden_show")

    /** What the map draws ([ActivityDetailPage.route]) for a native map; null with no route. */
    fun routeMap(page: ActivityDetailPage): RouteMap? = page.route?.let { RouteMap.of(it.polyline, it.gaps, it.ends) }

    /** The map's lock on the owner's page, when the Map category is narrower than the activity; null otherwise. */
    fun mapLock(page: ActivityDetailPage): LockLine? {
        val mark = page.page.mapMark ?: return null
        val text = VisibilityTexts.lock(DetailCategory.MAP, mark) ?: return null
        return LockLine(text, VisibilityTexts.lockDescription(DetailCategory.MAP, mark) ?: text)
    }

    /** A narrower category's badge beside a figure or a card's title, and what a screen reader says for it; null for none. */
    fun mark(mark: String?): AppText? = mark?.let(VisibilityTexts::badge)

    fun markDescription(mark: String?): AppText? = mark?.let(VisibilityTexts::badgeDescription)

    val segmentsHeading: AppText = AppText.named("detail_section_segments")
    val liftsHeading: AppText = AppText.named("detail_section_lifts")

    /** "Ridden 3 times". */
    fun liftRides(lift: LiftRides): AppText =
        AppText.Plural("detail_lift_rides", lift.rides, persistentListOf(AppText.written("${lift.rides}")))

    /** The time on a lift, in all its rides. */
    fun liftTime(lift: LiftRides): String = StatText.duration(lift.elapsedSeconds)

    /** What opening an effort sends: its segment, and whether it was an e-bike ride's. */
    fun openEffort(effort: EffortView): ActivityDetailEvent = ActivityDetailEvent.SegmentOpened(effort.segment.uri, effort.eBike == true)

    /** What opening a lift sends. */
    fun openLift(lift: LiftRides): ActivityDetailEvent = ActivityDetailEvent.SegmentOpened(lift.segment)

    // The owner's menu.

    /**
     * The owner's "…" menu: Edit, Combine with… and Add a recording… unless it's a member or full, Split on a combined activity, Ungroup
     * when it's grouped with just one other, Hide this start and end when its track can be read, Create segment and Create dive site when
     * they can be made from it, Export when it has data to export, and Delete. Empty on someone else's activity.
     */
    fun ownerMenu(page: ActivityDetailPage): List<ActivityMenuItem> {
        val manage = page.manage ?: return emptyList()
        return buildList {
            add(ActivityMenuItem(AppText.named("detail_edit"), ActivityMenuAction.Edit, ActivityDetailEvent.Edit))
            if (manage.canCombine) {
                add(ActivityMenuItem(AppText.named("detail_combine"), ActivityMenuAction.Combine, ActivityDetailEvent.Combine))
                add(
                    ActivityMenuItem(
                        AppText.named("detail_add_recording"),
                        ActivityMenuAction.AddRecording,
                        ActivityDetailEvent.AddRecording,
                    ),
                )
            }
            if (manage.members > 0) add(ActivityMenuItem(AppText.named("detail_split"), ActivityMenuAction.Split, null))
            if (manage.canUngroup)
                add(ActivityMenuItem(AppText.named("detail_ungroup"), ActivityMenuAction.Ungroup, ActivityDetailEvent.Ungroup))
            if (page.canHideRouteEnds) {
                add(ActivityMenuItem(AppText.named("detail_hide_start"), ActivityMenuAction.HideStart, ActivityDetailEvent.HideStart))
                add(ActivityMenuItem(AppText.named("detail_hide_end"), ActivityMenuAction.HideEnd, ActivityDetailEvent.HideEnd))
            }
            if (page.canCreateSegment) {
                add(
                    ActivityMenuItem(
                        AppText.named("detail_create_segment"),
                        ActivityMenuAction.CreateSegment,
                        ActivityDetailEvent.CreateSegment,
                    ),
                )
            }
            if (page.canCreateDiveSite) {
                add(
                    ActivityMenuItem(
                        AppText.named("detail_create_dive_site"),
                        ActivityMenuAction.CreateDiveSite,
                        ActivityDetailEvent.CreateDiveSite,
                    ),
                )
            }
            if (page.export != null) add(ActivityMenuItem(AppText.named("detail_export"), ActivityMenuAction.Export, null))
            add(ActivityMenuItem(AppText.named("detail_delete"), ActivityMenuAction.Delete, null, destructive = true))
        }
    }

    /** Whether the owner's menu can be opened: no delete or ungroup is running. */
    fun menuEnabled(manage: ActivityManage): Boolean = !manage.busy

    val report: AppText = AppText.named("report_activity")

    /** Whom a report names: the author's display name, or their handle with its `@`. */
    fun reportName(activity: ActivityView): AppText =
        activity.author.displayName?.let(AppText::written) ?: LabelText.handle(activity.author.handle.value)

    /** Someone else's page offers Report, when reporting is available. */
    fun offersReport(page: ActivityDetailPage): Boolean = !page.isOwn && page.reporting.available

    /** Report on someone else's activity, naming its author as [authorName] reads; null when [offersReport] isn't. */
    fun reportEvent(page: ActivityDetailPage, authorName: String): ReportingEvent.Open? {
        val activity = page.activity
        if (!offersReport(page)) return null
        return ReportingEvent.Open(ReportTarget.Activity(activity.uri, activity.cid, activity.author.did), authorName)
    }

    val cancel: AppText = AppText.named("action_cancel")
    val deleteTitle: AppText = AppText.named("delete_activity_title")

    /** The delete confirmation's message: with [ActivityManage.otherRecordings] and members, whether they go too. */
    fun deleteMessage(manage: ActivityManage): AppText {
        val others = manage.otherRecordings + manage.members
        return if (others == 0) {
            AppText.named("delete_activity_message")
        } else {
            AppText.Plural("delete_activity_group_message", others, persistentListOf(AppText.written("$others")))
        }
    }

    /** The delete confirmation's buttons: Delete; or, with other recordings or members, Delete all and Delete only this one. */
    fun deleteChoices(manage: ActivityManage): List<ActivityDeleteChoice> {
        val others = manage.otherRecordings + manage.members
        if (others == 0)
            return listOf(ActivityDeleteChoice(AppText.named("detail_delete"), ActivityDetailEvent.Delete(withRecordings = false)))
        return listOf(
            ActivityDeleteChoice(
                AppText.Plural("delete_activity_all", others, persistentListOf(AppText.written("${others + 1}"))),
                ActivityDetailEvent.Delete(withRecordings = true),
            ),
            ActivityDeleteChoice(AppText.named("delete_activity_only_this"), ActivityDetailEvent.Delete(withRecordings = false)),
        )
    }

    val splitTitle: AppText = AppText.named("split_activity_title")
    val splitMessage: AppText = AppText.named("split_activity_message")
    val split: AppText = AppText.named("detail_split")

    // The export sheet.

    val exportTitle: AppText = AppText.named("export_title")

    /** The sheet's action: Export the rebuilt format picked, or Download when there's only the original. */
    fun exportAction(export: ActivityExportState): AppText =
        AppText.named(if (export.formats.isNotEmpty()) "export_action" else "export_original_download")

    /** What the sheet's action sends with the [selected] format. */
    fun exportEvent(export: ActivityExportState, selected: Int): ActivityDetailEvent? =
        when {
            export.busy -> null
            export.formats.isNotEmpty() -> ActivityDetailEvent.Export(export.formats[selected.coerceIn(0, export.formats.lastIndex)])
            else -> ActivityDetailEvent.DownloadOriginal
        }

    /** How many rebuilt formats the sheet offers, and the [index]th's name. */
    fun formatCount(export: ActivityExportState): Int = export.formats.size

    fun formatName(export: ActivityExportState, index: Int): AppText = formatName(export.formats[index])

    fun formatName(format: ExportFormat): AppText =
        AppText.named(
            when (format) {
                ExportFormat.GPX -> "export_format_gpx"
                ExportFormat.TCX -> "export_format_tcx"
                ExportFormat.FIT -> "export_format_fit"
                ExportFormat.UDDF -> "export_format_uddf"
            },
        )

    val exportOriginal: AppText = AppText.named("export_original")
    val exportOriginalExplained: AppText = AppText.named("export_original_explained")
    val exportDownload: AppText = AppText.named("export_original_download")
    val exportRebuilt: AppText = AppText.named("export_rebuilt")
    val exportPreparing: AppText = AppText.named("export_preparing")

    /** What a rebuilt file lacks, and that it holds the full route. */
    fun exportNote(export: ActivityExportState): AppText = AppText.named(if (export.hasLaps) "export_note_with_laps" else "export_note")

    // The combine suggestion.

    /** What the suggestion looks like, as a question ([RecordingTexts.combineCue]). */
    fun combineCue(cue: CombineCue): AppText = RecordingTexts.combineCue(cue)

    /** "Combine this with Morning Ride (Today at 7:05 AM)?", or "Add this to …" when the other is a combined activity. */
    fun combineMessage(suggestion: CombineSuggestionState, now: Instant, zone: TimeZone): AppText {
        val record = suggestion.other.record
        val start = StartText.of(FigureValue.StartTime(record.startedAt.epochSeconds, record.utcOffset, dateOnly = false), now, zone)
        val name = record.title?.let(AppText::written) ?: LabelText.activityType(record.activityType)
        return AppText.named(
            if (record.combined != null) "combine_suggestion_member_message" else "combine_suggestion_message",
            name,
            start,
        )
    }

    val combine: AppText = AppText.named("combine_suggestion_combine")
    val notNow: AppText = AppText.named("combine_suggestion_not_now")
    val dontCombine: AppText = AppText.named("combine_suggestion_dont")
}
