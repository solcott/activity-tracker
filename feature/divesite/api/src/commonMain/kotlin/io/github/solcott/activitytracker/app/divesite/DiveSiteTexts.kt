package io.github.solcott.activitytracker.app.divesite

import io.github.solcott.activitytracker.app.segment.PlaceTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.DiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import kotlinx.collections.immutable.persistentListOf

/**
 * What a dive site's page says (dive-sites/site-records, dive-sites/site-conditions, dive-sites/site-regulars, dev/shared-display-text):
 * the site's radius, creator, credit and status, its Regulars card, its conditions in the viewer's units and its dives' heading. Both UIs
 * ask it.
 */
object DiveSiteTexts {
    /** "Within 30 m of its centre", in the viewer's units. */
    fun radius(site: DiveSiteView, units: UnitSystem): AppText =
        AppText.named("dive_site_radius", StatText.elevation(site.record.radius.toDouble(), units))

    /** "By @handle". */
    fun createdBy(site: DiveSiteView): AppText = PlaceTexts.createdBy(site.creator.handle.value)

    /** OpenStreetMap's credit for a site imported from it, opening [PlaceTexts.OSM_COPYRIGHT_URL]; null for any other. */
    fun osmCredit(site: DiveSiteView): AppText? = PlaceTexts.osmCredit.takeIf { site.record.source == SegmentSource.OSM }

    /** The site data's licence; null when it names none. */
    fun license(site: DiveSiteView): AppText? = PlaceTexts.license(site.record.license)

    /** The status of a site that isn't active ("Proposed"); null for an active one. */
    fun status(site: DiveSiteView): AppText? = PlaceTexts.status(site.status)

    /** What the status means for a dive site; null for an active one. */
    fun statusHelp(site: DiveSiteView): AppText? =
        PlaceTexts.statusHelp(
            site.status,
            proposedHelp = "dive_site_status_proposed_help",
            duplicateHelp = "dive_site_duplicate_help",
            archivedHelp = "dive_site_status_archived_help",
        )

    /** Open the original is offered: a duplicate whose original is named. */
    fun offersOriginal(site: DiveSiteView): Boolean = PlaceTexts.offersOriginal(site.status, site.duplicateOf != null)

    /** The Regulars card's title. */
    val regularsTitle: AppText = AppText.named("dive_site_regulars_card")

    /** The Regulars card with no leader yet. */
    val regularsNone: AppText = AppText.named("dive_site_regulars_none")

    /** The viewer's own count on the card ("You: 6 dives here"). */
    fun yourVisits(entry: DiveSiteRegularEntry): AppText =
        AppText.Plural("dive_site_regulars_yours", entry.visits, persistentListOf(AppText.written(formatWhole(entry.visits))))

    /** The conditions' heading. */
    val conditionsTitle: AppText = AppText.named("dive_site_conditions")

    /** How many dives the conditions come from. */
    fun conditionsFrom(conditions: DiveSiteConditions): AppText =
        AppText.Plural("dive_site_conditions_from", conditions.dives, persistentListOf(AppText.written(formatWhole(conditions.dives))))

    /** The water temperature, visibility and depth lines in [units], each only when some dive recorded it. */
    fun conditionLines(conditions: DiveSiteConditions, units: UnitSystem): List<AppText> =
        listOfNotNull(
            conditions.medianTemperature?.let { median ->
                AppText.named(
                    "dive_site_conditions_temperature",
                    StatText.temperature(median, units),
                    StatText.temperature(conditions.minTemperature, units),
                    StatText.temperature(conditions.maxTemperature, units),
                )
            },
            conditions.medianVisibility?.let { AppText.named("dive_site_conditions_visibility", StatText.depth(it / CM_PER_M, units)) },
            conditions.medianMaxDepth?.let { median ->
                AppText.named(
                    "dive_site_conditions_depth",
                    StatText.depth(median / CM_PER_M, units),
                    StatText.depth(conditions.maxDepth?.let { it / CM_PER_M }, units),
                )
            },
        )

    /** Each month's median temperature and visibility in [units] ("Jun · 27.0 °C · 18.0 m"), only when more than one month has dives. */
    fun monthLines(conditions: DiveSiteConditions, units: UnitSystem): List<AppText> =
        if (conditions.months.size <= 1) {
            emptyList()
        } else {
            conditions.months.map { month ->
                AppText.named(
                    "dive_site_conditions_month",
                    AppText.named("month_short_${month.month}"),
                    StatText.temperature(month.medianTemperature, units),
                    StatText.depth(month.medianVisibility?.let { it / CM_PER_M }, units),
                )
            }
        }

    /** The dives' heading. */
    val dives: AppText = AppText.named("dive_site_dives")

    /** No dives the viewer may see. */
    val noDives: AppText = AppText.named("dive_site_no_dives")

    private const val CM_PER_M = 100.0
}
