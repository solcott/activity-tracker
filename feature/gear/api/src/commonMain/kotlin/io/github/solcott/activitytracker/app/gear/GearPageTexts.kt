package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearComponent
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearMountView
import io.github.solcott.activitytracker.lexicon.app.GearStarting
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearView
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One line of the gear page's totals or breakdowns: what it counts ([label]) and how much ([value]). */
@Immutable data class GearFigure(val label: AppText, val value: AppText)

/**
 * What the gear item page says that follows a rule, chosen here for the shared and the native UI (dev/shared-display-text, gear/gear-usage,
 * gear/gear-components). Numbers are formatted here, so ui/number-format holds wherever the text is shown.
 *
 * One function per line the page shows, so both UIs ask the same one: splitting it would only split the page.
 */
@Suppress("TooManyFunctions")
object GearPageTexts {
    /** An item's kind within its type, or its type when it has none: "Gravel", "Chain", "Helmet". */
    fun kindLine(gear: GearView): AppText = kindOrType(gear.type, gear.kind)

    /** A mount candidate's kind or type. */
    fun candidateLine(candidate: GearMountCandidate): AppText = kindOrType(candidate.type, candidate.kind)

    /** The brand and model, "Rivendell Appaloosa"; null when it has neither. */
    fun maker(gear: GearView): AppText? = listOfNotNull(gear.brand, gear.model).joinToString(" ").ifEmpty { null }?.let(AppText::written)

    /** Who may see the item (the owner's page only) and Retired, "Public · Retired"; null when neither shows. */
    fun notes(page: GearPage): AppText? {
        val notes = listOfNotNull(page.visibility?.let(LabelText::visibility), AppText.named("gear_retired").takeIf { page.retired })
        return notes.reduceOrNull { joined, next -> AppText.named("gear_total_joined", joined, next) }
    }

    /** Every total that applies to the item's type, in the page's order: distance and vertical only where they count or matter. */
    fun figures(page: GearPage, units: UnitSystem): List<GearFigure> {
        val totals = page.usage.totals
        val main = GearType.mainTotal(page.gear.type)
        return buildList {
            add(GearFigure(AppText.named("gear_page_activity_count"), AppText.written(formatWhole(totals.activities))))
            add(GearFigure(AppText.named("gear_page_moving_time"), GearTexts.hours(totals.movingTime)))
            if (totals.distance > 0 || main == GearMainTotal.DISTANCE) {
                add(GearFigure(AppText.named("gear_page_distance"), GearTexts.distance(totals.distance, units)))
            }
            if (totals.elevationGain > 0) {
                add(GearFigure(AppText.named("gear_page_elevation_gain"), StatText.elevation(totals.elevationGain.toDouble(), units)))
            }
            if (totals.elevationLoss > 0 || main == GearMainTotal.DAYS_AND_VERTICAL) {
                add(GearFigure(AppText.named("gear_page_elevation_loss"), StatText.elevation(totals.elevationLoss.toDouble(), units)))
            }
            add(GearFigure(AppText.named("gear_page_days"), AppText.written(formatWhole(totals.days))))
            totals.dives?.let { add(GearFigure(AppText.named("gear_page_dives"), AppText.written(formatWhole(it)))) }
            totals.bottomTime?.let { add(GearFigure(AppText.named("gear_page_bottom_time"), AppText.written(StatText.duration(it)))) }
        }
    }

    /** "Includes 2,000 km · 100 h before tracking"; null without starting usage. */
    fun starting(starting: GearStarting?, units: UnitSystem): AppText? {
        val parts =
            listOfNotNull(
                starting?.distance?.takeIf { it > 0 }?.let { GearTexts.distance(it, units) },
                starting?.time?.takeIf { it > 0 }?.let { GearTexts.hours(it) },
            )
        val joined = parts.reduceOrNull { joined, next -> AppText.named("gear_total_joined", joined, next) } ?: return null
        return AppText.named("gear_page_starting", joined)
    }

    /** A row per activity type, "Gravel Ride: 1 activity · 32 km"; none when the item has only one type. */
    fun bySport(page: GearPage, units: UnitSystem): List<GearFigure> {
        val rows = page.usage.bySport
        if (rows.size <= 1) return emptyList()
        return rows.map { breakdown(LabelText.activityType(ActivityType(it.sport, it.subSport)), it.totals, page, units) }
    }

    /** A row per year, "2026: 1 activity · 32 km"; none when the item was used in only one year. */
    fun byYear(page: GearPage, units: UnitSystem): List<GearFigure> {
        val rows = page.usage.byYear
        if (rows.size <= 1) return emptyList()
        return rows.map { breakdown(AppText.written(it.year.toString()), it.totals, page, units) }
    }

    /** A component's kind and its main total: "Chain · 32 km". */
    fun componentLine(component: GearComponent, units: UnitSystem): AppText =
        AppText.named(
            "gear_total_joined",
            kindLine(component.gear),
            GearTexts.mainTotal(GearType.mainTotal(component.gear.type), component.totals, units),
        )

    /** When a component was mounted: "Since 2026-07-01", or "2025-03-01 – 2026-07-01" once it came off, in [zone]. */
    fun componentDates(component: GearComponent, zone: TimeZone): AppText = dates(component.from, component.to, zone)

    /** When the item was mounted on [mount]'s parent, as [componentDates] reads. */
    fun mountDates(mount: GearMountView, zone: TimeZone): AppText = dates(mount.from, mount.to, zone)

    /** A component's menu read aloud: "Actions for Chain #4". */
    fun componentActions(component: GearComponent): AppText = AppText.named("gear_component_actions", AppText.written(component.gear.name))

    /** The mount sheet's title: "Replace Chain #4", or "Mount on this item". */
    fun pickerTitle(picker: GearMountPicker): AppText =
        picker.replacing?.let { AppText.named("gear_mount_replace_title", AppText.written(it.gear.name)) }
            ?: AppText.named("gear_mount_pick_title")

    private fun kindOrType(type: String, kind: String?): AppText =
        kind?.let { GearTexts.kind(GearType.known(type), it) } ?: GearTexts.type(type)

    private fun breakdown(label: AppText, totals: GearTotals, page: GearPage, units: UnitSystem): GearFigure {
        val count =
            AppText.Plural(
                "gear_total_activities",
                totals.activities.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                persistentListOf(AppText.written(formatWhole(totals.activities))),
            )
        return GearFigure(
            label,
            AppText.named("gear_total_joined", count, GearTexts.mainTotal(GearType.mainTotal(page.gear.type), totals, units)),
        )
    }

    private fun dates(from: Instant, to: Instant?, zone: TimeZone): AppText =
        if (to == null) AppText.named("gear_component_since", day(from, zone))
        else AppText.named("gear_component_between", day(from, zone), day(to, zone))

    // An ISO date, as the shared page has always written mount dates.
    private fun day(instant: Instant, zone: TimeZone): AppText = AppText.written(instant.toLocalDateTime(zone).date.toString())
}
