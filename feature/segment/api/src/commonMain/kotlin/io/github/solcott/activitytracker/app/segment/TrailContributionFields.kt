package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDetail as Detail
import io.github.solcott.activitytracker.lexicon.app.TrailDetailInfo
import io.github.solcott.activitytracker.lexicon.app.TrailDetailSuggestionView
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import io.github.solcott.activitytracker.lexicon.app.TrailDog
import io.github.solcott.activitytracker.lexicon.app.TrailHorse
import io.github.solcott.activitytracker.lexicon.app.TrailLit
import io.github.solcott.activitytracker.lexicon.app.TrailPisteDifficulty
import io.github.solcott.activitytracker.lexicon.app.TrailPisteGrooming
import io.github.solcott.activitytracker.lexicon.app.TrailSacScale
import io.github.solcott.activitytracker.lexicon.app.TrailSeason
import io.github.solcott.activitytracker.lexicon.app.TrailSmoothness
import io.github.solcott.activitytracker.lexicon.app.TrailSource
import io.github.solcott.activitytracker.lexicon.app.TrailSurface
import io.github.solcott.activitytracker.lexicon.app.TrailSurfaces
import io.github.solcott.activitytracker.lexicon.app.TrailVisibility
import io.github.solcott.activitytracker.lexicon.app.TrailWheelchair
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet

/**
 * What the Segment page offers for keeping its trail details true (segments/trail-detail-suggestions): the open sheet of one detail, the
 * picker suggesting a value, the quick check after a recent ride, and the last failure. The holder that owns it (`rememberTrailFields`)
 * fills [eventSink], so the host declares no events of its own.
 */
@Immutable
data class TrailFields(
    /** Whether this session can vote and confirm: false on a PDS without Spaces or a grant from before trail detail votes. */
    val canVoteHere: Boolean = true,
    /** Whether "Add a missing detail" is offered: the viewer is a regular and some suggestable detail has no value. */
    val missing: ImmutableList<MissingDetail> = persistentListOf(),
    /** The sheet of one detail, while it's open. */
    val sheet: TrailSheet? = null,
    /** The picker suggesting a value for one detail, while it's open. */
    val picker: TrailPicker? = null,
    /** The quick check after a recent ride, while it shows. */
    val quickCheck: QuickCheck? = null,
    /** Why the last suggestion, vote or confirmation failed. */
    val error: UiError? = null,
    val eventSink: (TrailEvent) -> Unit = {},
) {
    /** [missing] as a plain list, a copy, for Swift. */
    val shownMissing: List<MissingDetail>
        get() = missing.toList()
}

/** A detail a regular may add: its [key] (a lexicon `TrailDetail` name) and its [label]. */
@Immutable data class MissingDetail(val key: String, val label: AppText)

/** One detail's sheet: its value and source, how riders confirmed it, and its open suggestions. */
@Immutable
data class TrailSheet(
    val key: String,
    val label: AppText,
    /** The value in effect; null when the detail has only suggestions. */
    val value: AppText? = null,
    /** "From OpenStreetMap · 8 riders confirmed it this year". */
    val source: AppText? = null,
    /** The viewer's own confirmation of the value in effect, a `TrailDetailVoteDirection` value; null for none. */
    val confirmed: String? = null,
    val suggestions: ImmutableList<SuggestionRow> = persistentListOf(),
    /** Whether Still right and It's changed show: a value is in effect. */
    val canConfirm: Boolean = false,
    /** Whether "Suggest a different value" shows: the viewer is a regular and the detail is open to their suggestion. */
    val canSuggest: Boolean = false,
) {
    /** [suggestions] as a plain list, a copy, for Swift. */
    val shownSuggestions: List<SuggestionRow>
        get() = suggestions.toList()

    /** Whether the viewer said the value is still right. */
    val stillRight: Boolean
        get() = confirmed == TrailDetailVoteDirection.STILL

    /** Whether the viewer said the value changed. */
    val changed: Boolean
        get() = confirmed == TrailDetailVoteDirection.CHANGED
}

/** One open suggestion in a sheet: [value] suggested by [author], its [totals], and the viewer's own [vote] (`up`, `down` or null). */
@Immutable
data class SuggestionRow(
    val uri: String,
    val value: AppText,
    val author: AppText,
    val totals: AppText,
    val vote: String? = null,
    /** The viewer's own suggestion, which they can't vote on. */
    val own: Boolean = false,
) {
    val agreed: Boolean
        get() = vote == TrailDetailVoteDirection.UP

    val disagreed: Boolean
        get() = vote == TrailDetailVoteDirection.DOWN
}

/** The picker suggesting a value for [key]: its [options] in words, and which is [selected]. */
@Immutable
data class TrailPicker(
    val key: String,
    val title: AppText,
    /** "Now: Dogs on leash, from OpenStreetMap"; null when the detail has no value. */
    val current: AppText? = null,
    val options: ImmutableList<TrailPickOption> = persistentListOf(),
    val selected: Int? = null,
) {
    /** [options] as a plain list, a copy, for Swift. */
    val shownOptions: List<TrailPickOption>
        get() = options.toList()

    /** Whether Suggest may be sent: a value is chosen. */
    val canSend: Boolean
        get() = selected != null
}

/** One value a picker offers: the value in effect is [current] and not selectable; one already [suggested] adds the viewer's agree vote. */
@Immutable data class TrailPickOption(val text: AppText, val current: Boolean = false, val suggested: Boolean = false)

/** The quick check after a recent ride: the folded summary's details with values, and the viewer's answers so far. */
@Immutable
data class QuickCheck(val rows: ImmutableList<QuickCheckRow> = persistentListOf()) {
    /** [rows] as a plain list, a copy, for Swift. */
    val shownRows: List<QuickCheckRow>
        get() = rows.toList()
}

/** One detail in the quick check: its [value], and the viewer's [answer] (`still`, `changed` or null). */
@Immutable
data class QuickCheckRow(val key: String, val value: AppText, val answer: String? = null) {
    val stillRight: Boolean
        get() = answer == TrailDetailVoteDirection.STILL

    val changed: Boolean
        get() = answer == TrailDetailVoteDirection.CHANGED
}

sealed interface TrailEvent {
    /** Opens [key]'s sheet. */
    data class Open(val key: String) : TrailEvent

    /** Closes the sheet. */
    data object Close : TrailEvent

    /** Says the open sheet's value is still right ([still]) or has changed; the same answer again takes it back. */
    data class Confirm(val still: Boolean) : TrailEvent

    /** Agrees ([up]) or disagrees with the suggestion [uri]; the same answer again takes it back. */
    data class Vote(val uri: String, val up: Boolean) : TrailEvent

    /** Opens the picker suggesting a value for [key]. */
    data class Suggest(val key: String) : TrailEvent

    /** Chooses the picker's option at [index]. */
    data class Pick(val index: Int) : TrailEvent

    /** Sends the picker's choice. */
    data object Send : TrailEvent

    /** Closes the picker without suggesting. */
    data object ClosePicker : TrailEvent

    /** Answers the quick check for [key]: still right ([still]) or changed, which opens its sheet. */
    data class Answer(val key: String, val still: Boolean) : TrailEvent

    /** Answers every unanswered quick check row still right. */
    data object AllStillRight : TrailEvent

    /** Puts the quick check off until next season. */
    data object NotNow : TrailEvent

    /** Clears [TrailFields.error]. */
    data object DismissError : TrailEvent
}

/**
 * The words and choices of the Segment page's trail detail contributions (segments/trail-detail-suggestions, dev/shared-display-text), for
 * the shared and the native UI.
 */
@Suppress("TooManyFunctions") // One per piece of the page, so both UIs read the same words.
object TrailContributionTexts {
    /** The details whose rows open a sheet: those riders may change. */
    private val changeable = Detail.suggestable.toPersistentSet()

    /** The keys of each card of the Trail details section, so a suggested-only detail joins its card. */
    private val difficultyKeys =
        persistentSetOf(
            Detail.SAC_SCALE,
            Detail.MTB_SCALE,
            Detail.MTB_SCALE_UPHILL,
            Detail.MTB_SCALE_IMBA,
            Detail.PISTE_DIFFICULTY,
            Detail.PISTE_GROOMING,
            Detail.TRAIL_CLASS,
            Detail.VISIBILITY,
        )
    private val surfaceKeys = persistentSetOf(Detail.SURFACE, Detail.SMOOTHNESS, Detail.WIDTH, Detail.GRADE)

    /**
     * [segment]'s Trail details cards with each row's source, notes and whether it opens (for a rider, [rider]), and each detail with only
     * open suggestions as a question row in its card. Empty when there's nothing to show.
     */
    @Suppress("ReturnCount") // Nothing to show, and nothing suggested-only.
    fun groups(segment: SegmentView?, rider: Boolean): List<TrailDetailGroup> {
        if (segment == null) return emptyList()
        val sport = segment.record.sport
        val infos = segment.trailDetails.associateBy { it.detail }
        val open = segment.trailSuggestions.groupBy { it.detail }
        val shown =
            TrailTexts.groups(segment.trail, sport).map { group ->
                group.copy(rows = group.rows.map { annotated(it, infos, open, rider) }.toImmutableList())
            }
        val held = segment.trail?.let(Detail::detailsOf).orEmpty().toSet()
        val pending =
            open.filterKeys { it !in held && fits(it, sport) }.map { (key, suggestions) -> key to questionRow(key, suggestions, rider) }
        if (pending.isEmpty()) return shown
        val titles = listOf("trail_group_difficulty", "trail_group_surface", "trail_group_access")
        val byTitle = shown.associateBy { it.title }.toMutableMap()
        for ((key, row) in pending) {
            val title = AppText.named(titles[cardOf(key)])
            val group = byTitle[title] ?: TrailDetailGroup(title, persistentListOf())
            byTitle[title] = group.copy(rows = (group.rows + row).toImmutableList())
        }
        return titles.mapNotNull { byTitle[AppText.named(it)] }
    }

    /**
     * The Trail page's cards (segments/trail-attributes): the path's own details, and each detail that an adopted community value on any of
     * [segments] makes differ from the path's reading "Varies along the trail" (the surface keeping its shares) with those segments listed.
     */
    @Suppress("ReturnCount") // Nothing varies, and nothing varies the path says nothing of.
    fun pathGroups(path: PathView?, segments: List<SegmentView>): List<TrailDetailGroup> {
        val varying = varyingOf(path, segments)
        val groups = TrailTexts.groups(path?.trail, sport = null)
        if (varying.isEmpty()) return groups
        val held = path?.trail?.let(Detail::detailsOf).orEmpty().toSet()
        val shown = groups.map { group ->
            group.copy(
                rows = group.rows.map { row -> row.key?.let { varying[it] }?.let { varied(row.key, row, it) } ?: row }.toImmutableList(),
            )
        }
        // A detail the path itself says nothing of, that riders set on some of its segments.
        val extra = varying.filterKeys { it !in held && it != Detail.SMOOTHNESS }
        if (extra.isEmpty()) return shown
        val titles = listOf("trail_group_difficulty", "trail_group_surface", "trail_group_access")
        val byTitle = shown.associateBy { it.title }.toMutableMap()
        for ((key, list) in extra) {
            val title = AppText.named(titles[cardOf(key)])
            val group = byTitle[title] ?: TrailDetailGroup(title, persistentListOf())
            val row = varied(key, TrailDetail(TrailTexts.labelOf(key), AppText.named("trail_varies"), key = key), list)
            byTitle[title] = group.copy(rows = (group.rows + row).toImmutableList())
        }
        return titles.mapNotNull { byTitle[AppText.named(it)] }
    }

    /**
     * The Trail page's folded line: [TrailTexts.summary]'s values, a detail that varies along the trail reading as varying ("Dogs vary").
     */
    fun pathSummary(path: PathView?, segments: List<SegmentView>): AppText? {
        val trail = path?.trail
        val varying = varyingOf(path, segments)
        val values =
            listOf(Detail.SURFACE, Detail.WIDTH, Detail.DOG, Detail.SEASON).mapNotNull { key ->
                when {
                    key in varying -> AppText.named("trail_vary_$key")
                    trail == null -> null
                    // As TrailTexts.summary reads each: the surface with its smoothness, the kind of trail, dogs, the season.
                    key == Detail.SURFACE -> TrailTexts.surface(trail)
                    key == Detail.WIDTH -> trail.widthCm?.takeIf { it > 0 }?.let(TrailTexts::width)
                    key == Detail.DOG -> trail.dog?.takeIf { it in TrailDog.all }?.let { AppText.named("trail_dog_$it") }
                    else -> TrailTexts.season(trail)
                }
            }
        return values.take(SUMMARY_VALUES).reduceOrNull { a, b -> AppText.named("segment_browse_details", a, b) }
    }

    /** Per detail, the segments whose adopted community value differs from [path]'s. */
    private fun varyingOf(path: PathView?, segments: List<SegmentView>): Map<String, List<VaryingSegment>> {
        val own = path?.trail
        return segments
            .distinctBy { it.uri }
            .flatMap { segment ->
                segment.trailDetails
                    .filter { it.source == TrailSource.COMMUNITY }
                    .mapNotNull { info ->
                        val value = segment.trail?.let { Detail.valueOf(it, info.detail) } ?: return@mapNotNull null
                        if (own != null && Detail.valueOf(own, info.detail) == value) return@mapNotNull null
                        info.detail to
                            VaryingSegment(
                                segment.uri.value,
                                segment.currentName,
                                valueText(info.detail, value),
                                sourceText(info.source, info.riders),
                            )
                    }
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun varied(key: String, row: TrailDetail, segments: List<VaryingSegment>): TrailDetail =
        row.copy(
            value = if (key == Detail.SURFACE) row.value else AppText.named("trail_varies"),
            note = AppText.Plural("trail_changed_on", segments.size, persistentListOf(AppText.whole(segments.size))),
            varying = segments.toImmutableList(),
        )

    /** "Riders confirmed these in Nov 2026": the latest month any detail was confirmed; null when none was served. */
    fun lastConfirmed(segment: SegmentView?): AppText? =
        segment
            ?.trailDetails
            ?.mapNotNull { it.confirmedMonth }
            ?.maxOrNull()
            ?.let { month -> AppText.named("trail_last_confirmed", monthText(month)) }

    /** The regular's missing details: suggestable ones that fit the segment's sport, with no value and no open suggestion. */
    fun missing(segment: SegmentView?): List<MissingDetail> {
        if (segment == null || segment.viewer?.canSuggestTrailDetails != true) return emptyList()
        val held = segment.trail?.let(Detail::detailsOf).orEmpty().toSet() + segment.trailSuggestions.map { it.detail }
        val closed = segment.viewer?.closedTrailDetails.orEmpty().toSet()
        return Detail.suggestable
            .filter { it !in held && it !in closed && fits(it, segment.record.sport) }
            .map { MissingDetail(it, TrailTexts.labelOf(it)) }
    }

    /** [key]'s sheet for [segment], as [viewerDid] sees it; null when the segment has nothing for that detail. */
    fun sheet(segment: SegmentView, key: String, viewerDid: String?): TrailSheet? {
        val value = segment.trail?.let { Detail.valueOf(it, key) }
        val suggestions = segment.trailSuggestions.filter { it.detail == key }
        if (value == null && suggestions.isEmpty()) return null
        val info = segment.trailDetails.firstOrNull { it.detail == key }
        val closed = key in segment.viewer?.closedTrailDetails.orEmpty()
        return TrailSheet(
            key = key,
            label = TrailTexts.labelOf(key),
            value = value?.let { valueText(key, it) },
            source = info?.let(::sheetSource),
            confirmed = info?.viewerConfirmation?.direction,
            suggestions = suggestions.map { suggestionRow(it, viewerDid) }.toImmutableList(),
            canConfirm = value != null,
            canSuggest = segment.viewer?.canSuggestTrailDetails == true && !closed && key in changeable,
        )
    }

    /** The picker suggesting a value for [key] on [segment]: every value of its vocabulary in words, the current and suggested marked. */
    fun picker(segment: SegmentView, key: String): TrailPicker {
        val current = segment.trail?.let { Detail.valueOf(it, key) }
        val suggested = segment.trailSuggestions.filter { it.detail == key }.map { it.value }
        val info = segment.trailDetails.firstOrNull { it.detail == key }
        return TrailPicker(
            key = key,
            title = AppText.named("trail_suggest_title", TrailTexts.labelOf(key)),
            current =
                current?.let { value ->
                    AppText.named(
                        "trail_suggest_now",
                        valueText(key, value),
                        sourceText(info?.source ?: TrailSource.IMPORTED, info?.riders),
                    )
                },
            options =
                options(key)
                    .map { option ->
                        TrailPickOption(
                            text = valueText(key, option),
                            current = current != null && Detail.valueOf(option, key) == current,
                            suggested = suggested.any { Detail.valueOf(it, key) == Detail.valueOf(option, key) },
                        )
                    }
                    .toImmutableList(),
        )
    }

    /** The quick check's rows: the folded summary's details (surface, width, dogs, season) [segment] holds, with the viewer's answers. */
    fun quickCheck(segment: SegmentView): QuickCheck? {
        val trail = segment.trail ?: return null
        val rows =
            listOf(Detail.SURFACE, Detail.WIDTH, Detail.DOG, Detail.SEASON)
                .filter { fits(it, segment.record.sport) }
                .mapNotNull { key ->
                    Detail.valueOf(trail, key)?.let { value ->
                        QuickCheckRow(
                            key,
                            valueText(key, value),
                            segment.trailDetails.firstOrNull { it.detail == key }?.viewerConfirmation?.direction,
                        )
                    }
                }
                .take(QUICK_CHECK_ROWS)
        return rows.takeIf { it.isNotEmpty() }?.let { QuickCheck(it.toImmutableList()) }
    }

    /** The value a picker's option at [index] stands for, for [key]. */
    fun optionValue(key: String, index: Int): TrailAttributes? = options(key).getOrNull(index)

    /** Every value a rider may suggest for [key], in its vocabulary's order. */
    @Suppress("CyclomaticComplexMethod")
    fun options(key: String): List<TrailAttributes> =
        when (key) {
            Detail.SURFACE -> TrailSurfaces.all.map { TrailAttributes(surfaces = listOf(TrailSurface(it, FULL))) }
            Detail.SMOOTHNESS -> TrailSmoothness.all.map { TrailAttributes(smoothness = it) }
            Detail.SAC_SCALE -> TrailSacScale.all.map { TrailAttributes(sacScale = it) }
            Detail.MTB_SCALE -> (0..TrailAttributes.MAX_MTB_SCALE).map { TrailAttributes(mtbScale = it) }
            Detail.MTB_SCALE_IMBA -> (0..TrailAttributes.MAX_MTB_SCALE_IMBA).map { TrailAttributes(mtbScaleImba = it) }
            Detail.MTB_SCALE_UPHILL -> (0..TrailAttributes.MAX_MTB_SCALE_UPHILL).map { TrailAttributes(mtbScaleUphill = it) }
            Detail.PISTE_DIFFICULTY -> TrailPisteDifficulty.all.map { TrailAttributes(pisteDifficulty = it) }
            Detail.PISTE_GROOMING -> TrailPisteGrooming.all.map { TrailAttributes(pisteGrooming = it) }
            Detail.VISIBILITY -> TrailVisibility.all.map { TrailAttributes(visibility = it) }
            Detail.WIDTH -> listOf(Detail.SINGLETRACK_CM, Detail.DOUBLETRACK_CM, Detail.ROAD_WIDTH_CM).map { TrailAttributes(widthCm = it) }
            Detail.LIT -> TrailLit.all.map { TrailAttributes(lit = it) }
            Detail.DOG -> TrailDog.all.map { TrailAttributes(dog = it) }
            Detail.HORSE -> TrailHorse.all.map { TrailAttributes(horse = it) }
            Detail.WHEELCHAIR -> TrailWheelchair.all.map { TrailAttributes(wheelchair = it) }
            // Open dates aren't a list to pick from: a season, or seasonal with none known.
            Detail.SEASON -> TrailSeason.all.map { TrailAttributes(seasons = listOf(it)) } + TrailAttributes(seasonal = true)
            else -> emptyList()
        }

    /** [value] of [key] in the words its row shows; the smoothness alone, which the Surface row only follows. */
    fun valueText(key: String, value: TrailAttributes): AppText =
        if (key == Detail.SMOOTHNESS) AppText.named("trail_smoothness_${value.smoothness}")
        else TrailTexts.rows(value, null).firstOrNull()?.value ?: AppText.written("")

    /** A row's source caption: the importer's source, or the community with its riders. */
    fun sourceText(source: String, riders: Int?): AppText =
        if (source == TrailSource.COMMUNITY)
            AppText.Plural("trail_source_community", riders ?: 0, persistentListOf(AppText.whole(riders ?: 0)))
        else AppText.named("trail_source_${source.takeIf { it in SOURCES } ?: TrailSource.IMPORTED}")

    private fun annotated(
        row: TrailDetail,
        infos: Map<String, TrailDetailInfo>,
        open: Map<String, List<TrailDetailSuggestionView>>,
        rider: Boolean,
    ): TrailDetail {
        val key = row.key ?: return row
        val info = infos[key]
        val suggestions = open[key].orEmpty().size
        val note =
            when {
                info?.mayHaveChanged == true -> AppText.named("trail_may_have_changed")
                suggestions > 0 -> AppText.Plural("trail_suggestions", suggestions, persistentListOf(AppText.whole(suggestions)))
                else -> null
            }
        return row.copy(
            source = info?.let { sourceText(it.source, it.riders) },
            community = info?.source == TrailSource.COMMUNITY,
            note = note,
            opens = rider && key in changeable,
        )
    }

    private fun questionRow(key: String, suggestions: List<TrailDetailSuggestionView>, rider: Boolean): TrailDetail {
        val best = suggestions.maxBy { it.up - it.down }
        return TrailDetail(
            label = TrailTexts.labelOf(key),
            value = AppText.named("trail_pending_question", valueText(key, best.value)),
            key = key,
            note = AppText.named("trail_pending_progress", AppText.whole(best.up)),
            question = true,
            opens = rider,
        )
    }

    private fun sheetSource(info: TrailDetailInfo): AppText {
        val from = AppText.named("trail_from", sourceText(info.source, info.riders))
        val confirmed = info.confirmations?.takeIf { it > 0 } ?: return from
        return AppText.named(
            "segment_browse_details",
            from,
            AppText.Plural("trail_confirmed_count", confirmed, persistentListOf(AppText.whole(confirmed))),
        )
    }

    private fun suggestionRow(suggestion: TrailDetailSuggestionView, viewerDid: String?): SuggestionRow =
        SuggestionRow(
            uri = suggestion.uri.value,
            value = valueText(suggestion.detail, suggestion.value),
            author = AppText.named("trail_suggested_by", AppText.written(suggestion.author.displayName ?: suggestion.author.handle.value)),
            totals = AppText.named("trail_vote_totals", AppText.whole(suggestion.up), AppText.whole(suggestion.down)),
            vote = suggestion.viewerVote?.direction,
            own = suggestion.author.did.value == viewerDid,
        )

    /** Whether [key] shows on a segment of [sport]: a sample of its values makes a row there (the scales and width depend on it). */
    private fun fits(key: String, sport: String): Boolean =
        key == Detail.SMOOTHNESS || options(key).firstOrNull()?.let { TrailTexts.rows(it, sport).isNotEmpty() } == true

    private fun cardOf(key: String): Int =
        when (key) {
            in difficultyKeys -> 0
            in surfaceKeys -> 1
            else -> 2
        }

    /** `YYYY-MM` as "Nov 2026"; the text itself when it isn't one. */
    private fun monthText(month: String): AppText {
        val year = month.substringBefore('-').toIntOrNull()
        val number = month.takeLast(2).toIntOrNull()
        return if (year != null && number != null && number in 1..MONTHS) DateText.monthYear(year, number) else AppText.written(month)
    }

    private fun <T> List<T>.toImmutableList(): ImmutableList<T> = kotlinx.collections.immutable.persistentListOf<T>().addAll(this)

    private val SOURCES = persistentSetOf(TrailSource.OSM, TrailSource.USFS, TrailSource.NPS, TrailSource.BLM, TrailSource.IMPORTED)
    private const val FULL = 100
    private const val MONTHS = 12
    private const val QUICK_CHECK_ROWS = 3
    private const val SUMMARY_VALUES = 3
}
