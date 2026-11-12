package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DayText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.activity.UploadSummary
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.CardRoute
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One of the scopes the feed's title offers, as an object, since a collection of enums can't reach Swift (dev/ios-swift-interop). */
@Immutable data class FeedScopeOption(val scope: FeedScope, val label: AppText, val chosen: Boolean)

/** What the feed's Add menu can start. */
enum class FeedAddKind {
    Record,
    Manual,
    Import,
}

/** One entry of the feed's Add menu: what it starts, its words, and the event it sends. */
@Immutable
data class FeedAddChoice(val kind: FeedAddKind, val label: AppText) {
    val event: FeedEvent
        get() =
            when (kind) {
                FeedAddKind.Record -> FeedEvent.Record
                FeedAddKind.Manual -> FeedEvent.AddManual
                FeedAddKind.Import -> FeedEvent.Import
            }
}

/**
 * What the feed says and offers (feed/feed-filter, data/outbox), chosen here for the shared and the native UI (dev/shared-display-text):
 * its title and scopes, its Add menu, the upload rows and the empty message. A pause's day is read in the [TimeZone] and beside the current
 * [Instant] each UI passes.
 */
object FeedTexts {
    /** A scope's name, which is also the feed's title while it is chosen: "Following" or "Mine". */
    fun scope(scope: FeedScope): AppText =
        when (scope) {
            FeedScope.Following -> AppText.named("feed_filter_following")
            FeedScope.Mine -> AppText.named("feed_filter_mine")
        }

    /** The feed's title: the chosen scope's name. */
    fun title(state: FeedState): AppText = scope(state.scope)

    /** The scopes the title's menu offers, in order, the chosen one marked. */
    fun scopes(state: FeedState): List<FeedScopeOption> = state.scopes.map { FeedScopeOption(it, scope(it), chosen = it == state.scope) }

    /** "Add activity": the Add menu's name, read aloud. */
    fun add(): AppText = AppText.named("feed_add")

    /**
     * The Add menu: Record when [recordInMenu] (where recording isn't a button of its own) and this device can record, then a manual
     * activity and an imported file.
     */
    fun addChoices(state: FeedState, recordInMenu: Boolean): List<FeedAddChoice> = buildList {
        if (recordInMenu && state.canRecord) add(FeedAddChoice(FeedAddKind.Record, AppText.named("feed_record")))
        add(FeedAddChoice(FeedAddKind.Manual, AppText.named("feed_add_manual")))
        add(FeedAddChoice(FeedAddKind.Import, AppText.named("feed_import")))
    }

    /**
     * The empty feed's message, once it has loaded nothing: that nothing matches the filters, or how to start (record, or add by hand where
     * this device can't record). Null while it lists something or is loading.
     */
    fun empty(state: FeedState): AppText? =
        when {
            !state.showsEmpty -> null
            state.filter.isNarrowed -> AppText.named("feed_empty_filtered")
            state.canRecord -> AppText.named("feed_empty_can_record")
            else -> AppText.named("feed_empty_manual_only")
        }

    /** Whether the empty feed offers Clear filters: only while the filters left nothing. */
    fun emptyOffersClear(state: FeedState): Boolean = state.showsEmpty && state.filter.isNarrowed
}

/**
 * What the feed's upload rows say (data/outbox), for the shared and the native feed (dev/shared-display-text): the refused changes, what is
 * uploading, and the imports' and the server's pauses. A pause's day is read in the [TimeZone] and beside the current [Instant] each UI
 * passes.
 */
object FeedUploadTexts {
    /** "2 changes couldn't be published", while the server refused any; null otherwise. */
    fun refused(uploads: UploadSummary): AppText? =
        if (uploads.refused > 0) AppText.Plural("feed_rejected_changes", uploads.refused, persistentListOf(whole(uploads.refused)))
        else null

    /**
     * What is uploading, by activity, while anything is: "Uploading 1 activity and 950 imported · 3 other changes". Imports the athlete
     * paused are waiting rather than uploading. Null with nothing uploading.
     */
    fun uploading(uploads: UploadSummary): AppText? {
        if (!uploads.uploading) return null
        val own = count("feed_uploading_activities", uploads.own)
        val imported = count("feed_uploading_imported", uploads.imported)
        val activities =
            when {
                uploads.own > 0 && uploads.imported > 0 -> AppText.named("feed_uploading_and_imported", own, whole(uploads.imported))
                uploads.own > 0 -> AppText.named("feed_uploading", own)
                uploads.imported > 0 && uploads.importsPaused -> AppText.named("feed_imports_waiting", imported)
                uploads.imported > 0 -> AppText.named("feed_uploading", imported)
                else -> null
            }
        val other = count("feed_other_changes", uploads.other)
        return when {
            activities != null && uploads.other > 0 -> AppText.named("feed_uploading_with_other", activities, other)
            activities != null -> activities
            else -> count("feed_pending_writes", uploads.other)
        }
    }

    /** "Imports paused", under the uploading line while the athlete paused imports that are waiting; null otherwise. */
    fun importsPaused(uploads: UploadSummary): AppText? =
        if (uploads.uploading && uploads.importsPaused && uploads.imported > 0) AppText.named("feed_imports_paused") else null

    /**
     * "Paused by the server until 3:40 PM", or "until tomorrow at" when the pause ends after [now]'s day in [zone], under the uploading
     * line; null with no pause.
     */
    fun pausedUntil(uploads: UploadSummary, now: Instant, zone: TimeZone): AppText? =
        uploads.serverPausedUntil
            ?.takeIf { uploads.uploading }
            ?.let { DayText.until(it, now, zone, today = "feed_upload_paused", tomorrow = "feed_upload_paused_tomorrow") }

    private fun count(name: String, quantity: Int): AppText = AppText.Plural(name, quantity, persistentListOf(whole(quantity)))
}

/**
 * What an import card says (feed/import-cards), for the shared and the native card (dev/shared-display-text): who, over which months, how
 * many, which sports and how far, and which routes it draws.
 */
object ImportCardTexts {
    /** Who imported the batch, as their name or handle. */
    fun author(card: ImportCardView): AppText = AppText.written(card.author.shownName)

    /** Whether the author's avatar is blurred under an account label, as every author line's is. */
    fun avatarBlurred(card: ImportCardView): Boolean = ModerationDecision.mediaOf(null, card.author.labels) != null

    /** "Imported 312 activities". */
    fun title(card: ImportCardView): AppText {
        val count = card.count.toInt()
        return AppText.Plural("import_card_title", count, persistentListOf(whole(count)))
    }

    /** "Nov 2019 – Oct 2026": the months of its first and last start, in the viewer's [zone]. */
    fun range(card: ImportCardView, zone: TimeZone): AppText =
        AppText.named("import_card_range", month(card.earliestStartedAt, zone), month(card.latestStartedAt, zone))

    /** "Ride 201 · Run 98 · Swim 13 · 14,380 km": each sport's count, most first, then the distance in [units] where one is known. */
    fun summary(card: ImportCardView, units: UnitSystem): AppText {
        val sports = card.sports.map { AppText.named("import_card_sport", LabelText.sport(it.sport), whole(it.count.toInt())) }
        val distance = card.distance?.let { StatText.distance(it.toDouble(), units) }
        return (sports + listOfNotNull(distance)).reduceOrNull { line, next -> AppText.named("text_dot_pair", line, next) }
            ?: AppText.written("")
    }

    /** The routes the card draws, at most [ImportCardView.MAX_ROUTES]. */
    fun routes(card: ImportCardView): List<CardRoute> = card.routes.orEmpty().take(ImportCardView.MAX_ROUTES)

    /** "Show the imported activities": what a tap on the card does, read aloud. */
    fun open(): AppText = AppText.named("import_card_open")

    private fun month(instant: Instant, zone: TimeZone): AppText {
        val date = instant.toLocalDateTime(zone).date
        // A year is a label, not a quantity: never grouped (ui/number-format).
        return AppText.named(
            "import_batch_month",
            AppText.named("month_short_${date.month.ordinal + 1}"),
            AppText.written(date.year.toString()),
        )
    }
}

/** [count] with its digits grouped as the device's locale groups them ("3,000", ui/number-format). */
private fun whole(count: Int): AppText = AppText.written(formatWhole(count))
