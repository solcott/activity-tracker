package io.github.solcott.activitytracker.app.activitylist

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.RouteShape
import io.github.solcott.activitytracker.app.media.MediaItem
import io.github.solcott.activitytracker.app.media.mediaItems
import io.github.solcott.activitytracker.app.moderation.ModerationTexts
import io.github.solcott.activitytracker.app.moderation.NoticeText
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.CardStatsText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.LabeledText
import io.github.solcott.activitytracker.app.text.StartText
import io.github.solcott.activitytracker.app.ui.narrowerCategories
import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.WarnReason
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GroupPartner
import io.github.solcott.activitytracker.lexicon.app.activityType
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** A detail category's lock on the owner's card: its line ("Heart rate · Only me") and what a screen reader says for it. */
@Immutable data class LockLine(val text: AppText, val spoken: AppText)

/**
 * What one activity card shows of [activity] for a viewer who is its author ([isOwn]) or not, chosen once for the shared and the native
 * card (dev/shared-display-text): who and when, the labels' notices and blur, the title, the figures, the owner's locks, the media and
 * route, and whether Like is offered and given. Each UI only lays it out.
 */
@Immutable
class ActivityCardContent(val activity: ActivityView, val isOwn: Boolean) {
    /** Every detail the viewer was served, with what they weren't left out. */
    val record: ActivityRecord = activity.effectiveRecord()

    /** The type its icon and untitled title show. */
    val type: ActivityType = record.activityType

    /** How its labels and its author's treat it (`moderation/labels`). */
    val decision: ModerationDecision = ModerationDecision.of(activity.labels, activity.author.labels, isOwn)

    /** The media label its photos are blurred under until revealed, if any. */
    val blur: BlurReason? = ModerationDecision.mediaOf(activity.labels, activity.author.labels)

    /** The media label the author's avatar is blurred under, from their account alone (the card's own labels blur only its media). */
    val avatarBlur: BlurReason? = ModerationDecision.mediaOf(null, activity.author.labels)

    /** The warning its labels or its author's carry, whatever else applies. */
    val warn: WarnReason? = ModerationDecision.warnOf(activity.labels, activity.author.labels)

    /** The owner's own taken-down activity: its photos are placeholders, since the AppView no longer serves them. */
    val takenDown: Boolean
        get() = decision == ModerationDecision.Takedown

    /** Like is offered on others' activities only. */
    val offersLike: Boolean
        get() = !isOwn

    /** Whether the viewer gave it a like. */
    val liked: Boolean
        get() = activity.viewer?.like != null

    /** Its photos this client can show, in order. */
    val media: ImmutableList<MediaItem> = activity.mediaItems()

    /** Whether it has a route the viewer was served. */
    val hasRoute: Boolean
        get() = record.polyline != null

    /** Its route placed on a tile [width] by [height] with [pad] kept clear, broken at its gaps; null without one to draw. */
    fun routeShape(width: Double, height: Double, pad: Double): RouteShape? =
        record.polyline?.let { RouteShape.of(it, record.polylineGaps, width, height, pad) }

    /** The author, by the name the app shows them. */
    fun author(): AppText = AppText.written(activity.author.shownName)

    /** Its non-public tier's badge beside the author; null for a public activity. */
    fun badge(): AppText? = VisibilityTexts.badge(activity.visibilityOrPublic)

    /** What a screen reader says for [badge]. */
    fun badgeDescription(): AppText? = VisibilityTexts.badgeDescription(activity.visibilityOrPublic)

    /** Its start on its own clock, beside [now]'s day; [zone] is the viewer's, for a start with no offset. */
    fun start(now: Instant, zone: TimeZone): AppText = StartText.of(record, now, zone)

    /** Whom it was done with (feed/group-activities); null for no one. */
    fun together(): AppText? = togetherWith(activity.group.orEmpty())

    /** The lines its labels show above it (`ModerationTexts.notices`). */
    fun notices(): List<NoticeText> = ModerationTexts.notices(decision, isOwn, warn)

    /** Its title, or its type when untitled. */
    fun title(): AppText = record.title?.let { AppText.written(it) } ?: LabelText.activityType(type)

    /** What a screen reader says for its icon: its type beside a title, and nothing when the title already is the type. */
    fun typeDescription(): AppText? = if (record.title != null) LabelText.activityType(type) else null

    /** A dive's site, by name. */
    fun diveSite(): String? = record.dive?.site

    /** What the athlete wrote about it, shown to two lines. Not `description`, which Swift export leaves out (dev/ios-swift-interop). */
    fun descriptionText(): String? = record.description

    /** Its figures in [units] (`CardStatsText`). */
    fun stats(units: UnitSystem): List<LabeledText> = CardStatsText.of(record, units)

    /** On the owner's own, each detail category stored narrower than the activity, as its lock reads; none for anyone else's. */
    fun locks(): List<LockLine> = if (isOwn) locksOf(narrowerCategories(activity)) else emptyList()

    /** The label its photos are blurred under, as their cover names it; null when they aren't. */
    fun blurLabel(): AppText? = blur?.let(ModerationTexts::blur)

    /** "3 likes · 1 comment". */
    fun counts(): AppText =
        AppText.named(
            "activity_card_counts",
            AppText.Plural("like_count", activity.likeCount.toInt(), persistentListOf(AppText.written(formatWhole(activity.likeCount)))),
            AppText.Plural(
                "comments_count",
                activity.commentCount.toInt(),
                persistentListOf(AppText.written(formatWhole(activity.commentCount))),
            ),
        )

    override fun equals(other: Any?): Boolean = other is ActivityCardContent && other.activity == activity && other.isOwn == isOwn

    override fun hashCode(): Int = activity.hashCode() * 31 + isOwn.hashCode()

    companion object {
        /** [activity]'s card for the signed-in [viewerDid]. */
        fun of(activity: ActivityView, viewerDid: String): ActivityCardContent =
            ActivityCardContent(activity, isOwn = activity.author.did.value == viewerDid)

        /** Whom an activity was done with: "with Bob", "with Bob and Carol", or "with Bob and 2 others"; null for no one. */
        fun togetherWith(partners: List<GroupPartner>): AppText? {
            val names = partners.map { AppText.written(it.author.shownName) }
            return when (names.size) {
                0 -> null
                1 -> AppText.named("card_with_one", names[0])
                2 -> AppText.named("card_with_two", names[0], names[1])
                else ->
                    AppText.Plural(
                        "card_with_others",
                        names.size - 1,
                        persistentListOf(names[0], AppText.written(formatWhole((names.size - 1).toLong()))),
                    )
            }
        }

        private fun locksOf(categories: List<CategoryVisibility>): List<LockLine> = categories.mapNotNull { (category, visibility) ->
            val text = VisibilityTexts.lock(category, visibility) ?: return@mapNotNull null
            LockLine(text, VisibilityTexts.lockDescription(category, visibility) ?: text)
        }
    }
}
