package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.moderation.ModerationTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailConditions
import io.github.solcott.activitytracker.lexicon.app.TrailPhotoView
import io.github.solcott.activitytracker.lexicon.app.TrailReportRecord
import io.github.solcott.activitytracker.lexicon.app.TrailReportView
import io.github.solcott.activitytracker.textfield.TextFieldState
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Reporting what a segment is like now (segments/trail-reports): the Conditions card, the report sheet, the add-photos screen, the share
 * confirmation and their events. The Segment presenter fills [eventSink], so the host declares no events of its own.
 */
@Immutable
data class ReportFields(
    /** The Conditions card; null when the segment has no reports and the viewer may not report, so the page leaves it out. */
    val card: ConditionsCard? = null,
    /** The report sheet, while it's open. */
    val sheet: ReportSheet? = null,
    /** The add-photos screen, while it's open over the sheet. */
    val addPhotos: AddPhotos? = null,
    /** Whether the sheet is asking before it posts a public copy of a photo from a non-public ride. */
    val confirmingShare: Boolean = false,
    /** Why the last post failed. */
    val error: UiError? = null,
    val eventSink: (ReportEvent) -> Unit = {},
)

/**
 * The Segment page's Conditions card. With current conditions: [lead] in large type (Closed drawn in the error colour, [closed]),
 * [leadLine] ("3 riders · latest 2 days ago"), the other [tags] with their riders, and the newest [note]. Without: [empty] and
 * [emptyDetail]. Then the [photos] strip with [morePhotos] over its last one and [seeAll], and, for a viewer who may report, [ask] and the
 * two actions.
 */
@Immutable
data class ConditionsCard(
    val lead: AppText? = null,
    val closed: Boolean = false,
    val leadLine: AppText? = null,
    val tags: ImmutableList<AppText> = persistentListOf(),
    val note: ReportNote? = null,
    /** "7 reports" beside the title, from the reports list once read; null until then. */
    val count: AppText? = null,
    val empty: AppText? = null,
    val emptyDetail: AppText? = null,
    val photos: ImmutableList<PhotoThumb> = persistentListOf(),
    /** "+13" over the last photo when there are more than the strip shows. */
    val morePhotos: AppText? = null,
    val seeAll: AppText? = null,
    /** "You rode it 3 days ago. How was it?", for a recent rider when nothing is current. */
    val ask: AppText? = null,
    /** Report conditions and Add photos are offered: the AppView says the viewer may report, and this session can write. */
    val offersReport: Boolean = false,
) {
    /** [tags] as a plain list, a copy, for Swift. */
    val shownTags: List<AppText>
        get() = tags.toList()

    /** [photos] as a plain list, a copy, for Swift. */
    val shownPhotos: List<PhotoThumb>
        get() = photos.toList()
}

/** The newest note: its [author]'s name, its [age] ("2d") and [text]. */
@Immutable data class ReportNote(val author: String, val age: AppText, val text: String)

/** One photo in a strip: its [url], [alt] text, and, when a label asks it, the [blur] reason it's shown behind until tapped. */
@Immutable data class PhotoThumb(val url: String, val alt: String?, val blur: AppText? = null)

/**
 * The report sheet: what the viewer rode ([subtitle]), the tags as toggles in two groups, the [note] with its [noteCount], the [photos] to
 * add (at most [TrailReportRecord.MAX_IMAGES]), and whether Post report may be pressed ([canPost]) or is sending ([posting]).
 */
@Immutable
data class ReportSheet(
    val subtitle: AppText,
    val surface: ImmutableList<TagToggle>,
    val onTrail: ImmutableList<TagToggle>,
    val note: TextFieldState,
    val noteCount: AppText,
    val photos: ImmutableList<ReportPhotoChoice> = persistentListOf(),
    val posting: Boolean = false,
    /**
     * Whether the [note] fits [TrailReportRecord.MAX_NOTE_GRAPHEMES] ([TrailReportTexts.noteFits]); its counter shows as an error if not.
     */
    val noteFits: Boolean = true,
) {
    /** Post report needs a tag or a photo, a note that fits, and nothing being sent. */
    val canPost: Boolean
        get() = !posting && noteFits && (surface.any { it.selected } || onTrail.any { it.selected } || photos.isNotEmpty())

    /** How many more photos may be added, for a picker's limit. */
    val room: Int
        get() = (TrailReportRecord.MAX_IMAGES - photos.size).coerceAtLeast(0)

    /** Whether more photos may be added. */
    val canAddPhotos: Boolean
        get() = photos.size < TrailReportRecord.MAX_IMAGES

    /** [surface] as a plain list, a copy, for Swift. */
    val shownSurface: List<TagToggle>
        get() = surface.toList()

    /** [onTrail] as a plain list, a copy, for Swift. */
    val shownOnTrail: List<TagToggle>
        get() = onTrail.toList()

    /** [photos] as a plain list, a copy, for Swift. */
    val shownPhotos: List<ReportPhotoChoice>
        get() = photos.toList()
}

/** A condition tag in the sheet: its [value] (a [TrailCondition]), [label], whether it's [selected], and whether it's Closed ([warns]). */
@Immutable
data class TagToggle(val value: String, val label: AppText, val selected: Boolean = false) {
    val warns: Boolean
        get() = value == TrailCondition.CLOSED
}

/** A photo picked for a report: [key] names it in events, [url] shows it (a held or served URL), [alt] its text. */
@Immutable data class ReportPhotoChoice(val key: String, val url: String?, val alt: String?, val fromNonPublic: Boolean = false)

/**
 * The add-photos screen: the viewer's own [rides] that passed the segment with their photos, newest first; [picked] counts what the sheet
 * already holds plus what's ticked here; [loading] while the rides are read.
 */
@Immutable
data class AddPhotos(
    val rides: ImmutableList<RidePhotos> = persistentListOf(),
    val picked: Int = 0,
    val loading: Boolean = false,
) {
    /** "Add 2", once anything here is ticked; null before. */
    val addLabel: AppText?
        get() =
            rides
                .sumOf { r -> r.photos.count { it.selected } }
                .takeIf { it > 0 }
                ?.let { AppText.named("trail_photos_add", AppText.whole(it)) }

    /** [rides] as a plain list, a copy, for Swift. */
    val shownRides: List<RidePhotos>
        get() = rides.toList()
}

/** One of the viewer's rides with photos: its [title] ("Yesterday · Evening loop"), [visibility] label and [photos]. */
@Immutable
data class RidePhotos(val title: AppText, val visibility: AppText, val photos: ImmutableList<RidePhoto>) {
    val shownPhotos: List<RidePhoto>
        get() = photos.toList()
}

/** One photo of a ride: [key] names it in events; it can't be ticked once the report is full ([enabled]). */
@Immutable data class RidePhoto(val key: String, val url: String, val alt: String?, val selected: Boolean, val enabled: Boolean)

sealed interface ReportEvent {
    /** Opens the report sheet. */
    data object Open : ReportEvent

    /** Closes the sheet, dropping what it held. */
    data object Close : ReportEvent

    /** Turns the tag [value] on or off. */
    data class Toggle(val value: String) : ReportEvent

    /** Adds photos picked on the device (taken, or chosen from the library), already re-encoded without their metadata. */
    data class AddPicked(val photos: ImmutableList<PickedPhoto>) : ReportEvent {
        /** For Swift, whose export gives no initialiser to a class that takes an immutable collection (dev/ios-swift-interop). */
        constructor(photos: List<PickedPhoto>) : this(photos.toImmutableList())
    }

    /** Removes the photo [key] from the sheet. */
    data class RemovePhoto(val key: String) : ReportEvent

    /** Opens the add-photos screen, from the card (opening the sheet too) or from the sheet. */
    data object OpenAddPhotos : ReportEvent

    /** Ticks or unticks the ride photo [key] on the add-photos screen. */
    data class TogglePhoto(val key: String) : ReportEvent

    /** Adds the ticked ride photos to the sheet and closes the add-photos screen. */
    data object AddTicked : ReportEvent

    /** Closes the add-photos screen without adding anything. */
    data object CloseAddPhotos : ReportEvent

    /** Posts the report; asks first when it shares a photo from a non-public ride. */
    data object Post : ReportEvent

    /** Answers the share question: post, or go back to the sheet. */
    data class ConfirmShare(val share: Boolean) : ReportEvent

    /** Opens every report on the segment. */
    data object OpenReports : ReportEvent

    /** Opens the photo at [index] of the card's strip in the viewer. */
    data class OpenPhoto(val index: Int) : ReportEvent

    data object DismissError : ReportEvent
}

/** The words trail reports show, which both UIs resolve (segments/trail-reports); the Trail page's card is [PathConditionsTexts]. */
@Suppress("TooManyFunctions") // One per line of the card, the sheet and the list, so both UIs read the same words.
object TrailReportTexts {
    /** How many photos the card's strip shows. */
    const val STRIP = 4

    /** A condition tag's name ("Downed tree"). */
    fun tag(value: String): AppText = AppText.named("trail_condition_$value")

    /** Whether [tag] is Closed's name, which both UIs mark in the error colour wherever a tag is listed. */
    fun isClosed(tag: AppText): Boolean = tag == tag(TrailCondition.CLOSED)

    /** [tags] by name, joined ("Muddy, Downed tree"). */
    fun tags(values: List<String>): AppText? = AppText.joined(values.map(::tag))

    /**
     * How long ago [at] was on [now]'s calendar in [zone]: "today", "yesterday", "3 days ago", then weeks from 14 days and months from 60.
     */
    fun age(at: Instant, now: Instant, zone: TimeZone): AppText {
        val days = at.toLocalDateTime(zone).date.daysUntil(now.toLocalDateTime(zone).date).coerceAtLeast(0)
        return when {
            days == 0 -> AppText.named("trail_age_today")
            days == 1 -> AppText.named("trail_age_yesterday")
            days < WEEKS_FROM -> plural("trail_age_days", days)
            days < MONTHS_FROM -> plural("trail_age_weeks", days / DAYS_PER_WEEK)
            else -> plural("trail_age_months", days / DAYS_PER_MONTH)
        }
    }

    /**
     * The card for [segment] at [now] in [zone], or null when it has no reports and the viewer may not report. [canWrite] says whether this
     * session can post; [reports] counts the reports listed, once read.
     */
    fun card(segment: SegmentView?, now: Instant, zone: TimeZone, canWrite: Boolean, reports: Int? = null): ConditionsCard? {
        val conditions = segment?.conditions
        val viewer = segment?.viewer
        val mayReport = canWrite && viewer?.canReportConditions == true
        if (conditions == null && !mayReport) return null
        val current = conditions != null && conditions.riders > 0
        val photos = conditions?.photos.orEmpty().take(STRIP)
        val total = conditions?.photoCount ?: 0
        return ConditionsCard(
            lead = conditions?.lead?.let(::tag),
            closed = conditions?.lead == TrailCondition.CLOSED,
            leadLine = conditions?.takeIf { current }?.let { leadLine(it, now, zone) },
            tags = tagCounts(conditions),
            note = conditions?.latestNote?.let { note(it, now, zone) },
            count = reports?.takeIf { it > 0 }?.let { plural("trail_reports_count", it) },
            empty = AppText.named("trail_conditions_none").takeUnless { current },
            emptyDetail = conditions?.takeUnless { current }?.let { lastReport(it, now, zone) },
            photos = photos.map(::thumb).toImmutableList(),
            morePhotos = morePhotos(total, photos.size, STRIP),
            seeAll = seeAllPhotos(total, photos.size),
            ask = viewer?.lastEffortAt?.takeIf { mayReport && !current }?.let { AppText.named("trail_conditions_ask", age(it, now, zone)) },
            offersReport = mayReport,
        )
    }

    /** Each of [conditions]' tags with how many riders gave it ("Muddy · 3"). */
    private fun tagCounts(conditions: TrailConditions?): ImmutableList<AppText> =
        conditions?.tags.orEmpty().map { AppText.named("trail_condition_count", tag(it.value), AppText.whole(it.riders)) }.toImmutableList()

    /** "Last report: Muddy, 3 weeks ago", for conditions no longer current; null when none was tagged. */
    private fun lastReport(conditions: TrailConditions, now: Instant, zone: TimeZone): AppText? {
        val at = conditions.lastReportAt ?: return null
        return tags(conditions.lastReportTags)?.let { AppText.named("trail_conditions_last", it, age(at, now, zone)) }
    }

    /** "3 riders · latest 2 days ago". */
    fun leadLine(conditions: TrailConditions, now: Instant, zone: TimeZone): AppText? {
        val latest = conditions.latestAt ?: return null
        val riders = conditions.leadRiders ?: conditions.riders
        return AppText.Plural("trail_conditions_riders", riders, persistentListOf(AppText.whole(riders), age(latest, now, zone)))
    }

    /** A report's note line. */
    fun note(report: TrailReportView, now: Instant, zone: TimeZone): ReportNote? =
        report.note?.let { ReportNote(nameOf(report.author), age(report.indexedAt, now, zone), it) }

    /** A strip photo, blurred behind its label's reason when the report or its author carries a media label. */
    fun thumb(photo: TrailPhotoView): PhotoThumb =
        PhotoThumb(
            url = photo.image.fullsize,
            alt = photo.image.alt,
            blur = ModerationDecision.mediaOf(photo.labels, photo.author.labels)?.let(ModerationTexts::blur),
        )

    /** Why [report]'s photos are blurred, when it or its author carries a media label; null when they show. */
    fun blurOf(report: TrailReportView): AppText? =
        ModerationDecision.mediaOf(report.labels, report.author.labels)?.let(ModerationTexts::blur)

    /** The report sheet's subtitle: the segment's name and when the viewer rode it. */
    fun subtitle(segment: SegmentView, now: Instant, zone: TimeZone): AppText {
        val name = AppText.written(segment.currentName)
        val rode = segment.viewer?.lastEffortAt ?: return name
        return AppText.named("trail_report_rode", name, age(rode, now, zone))
    }

    /** The sheet's tag toggles: [TrailCondition.surface] or [TrailCondition.onTrail], with [selected] on. */
    fun toggles(values: List<String>, selected: Set<String>): ImmutableList<TagToggle> =
        values.map { TagToggle(it, tag(it), it in selected) }.toImmutableList()

    /** How long a [note] is as it posts: trimmed, in characters ([ActivityRecord.textLength], never more than its graphemes). */
    fun noteLength(note: String): Int = ActivityRecord.textLength(note.trim())

    /** Whether [note] fits the lexicon's [TrailReportRecord.MAX_NOTE_GRAPHEMES]; nothing refuses typing, so Post is held back instead. */
    fun noteFits(note: String): Boolean = noteLength(note) <= TrailReportRecord.MAX_NOTE_GRAPHEMES

    /** "82 / 300" under the note. */
    fun noteCount(length: Int): AppText =
        AppText.named("trail_report_note_count", AppText.whole(length), AppText.whole(TrailReportRecord.MAX_NOTE_GRAPHEMES))

    /** An author's display name, else their handle. */
    fun nameOf(author: ProfileViewBasic): String = author.displayName?.takeIf { it.isNotBlank() } ?: author.handle.value

    /** Whether [report] is still in the conditions window at [now]: as the AppView says, or 14 days since it was indexed. */
    fun isCurrent(report: TrailReportView, now: Instant): Boolean = report.current && now - report.indexedAt < CURRENT

    private fun plural(name: String, count: Int) = AppText.Plural(name, count, persistentListOf(AppText.whole(count)))

    private val CURRENT = 14.days
    private const val WEEKS_FROM = 14
    private const val MONTHS_FROM = 60
    private const val DAYS_PER_WEEK = 7
    private const val DAYS_PER_MONTH = 30
}

/** "+12" over a full strip of [strip] photos, when [total] has more than the [shown] ones. */
internal fun morePhotos(total: Int, shown: Int, strip: Int): AppText? =
    (total - shown).takeIf { it > 0 && shown == strip }?.let { AppText.named("trail_photos_more", AppText.whole(it)) }

/** "See all 16", when [total] has more photos than the [shown] ones. */
internal fun seeAllPhotos(total: Int, shown: Int): AppText? =
    total.takeIf { it > shown }?.let { AppText.named("trail_photos_see_all", AppText.whole(it)) }
