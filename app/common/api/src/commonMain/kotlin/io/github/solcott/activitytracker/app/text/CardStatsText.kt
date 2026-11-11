package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.data.sport.RateKind
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.Sport

/**
 * An activity card's figures (feed/activity-stats, dev/shared-display-text), from its record alone, each a label over a value: a dive's
 * depth, dive time and water temperature; otherwise its distance where it means something, its time, and its rate while the viewer was
 * served the speed (the Pace/Speed category). The activity page's dive figures start from the same three.
 */
object CardStatsText {
    fun of(record: ActivityRecord, units: UnitSystem): List<LabeledText> =
        if (record.sport == Sport.DIVE) {
            listOfNotNull(maxDepth(record.dive, null, units), diveTime(record), waterTemp(record, units))
        } else {
            val seconds = record.movingTime ?: record.elapsedTime
            listOfNotNull(
                distance(record.sport, record.distance?.toDouble(), units),
                LabeledText(AppText.named("stat_time"), AppText.written(StatText.duration(seconds))),
                record.avgSpeed?.let { StatText.rate(record.sport, record.distance?.toDouble(), seconds, units) },
            )
        }

    /** The distance, or null for a sport without a rate whose distance is absent or zero, like a strength workout's 0 m. */
    fun distance(sport: String, meters: Double?, units: UnitSystem): LabeledText? =
        if (showsLength(sport, meters)) LabeledText(AppText.named("stat_distance"), StatText.distance(meters, units)) else null

    /** Sports with a rate always show distance and elevation, a dash when absent; the others only when above zero. */
    fun showsLength(sport: String, meters: Double?): Boolean = SportCatalog.rate(sport) != RateKind.None || (meters != null && meters > 0)

    /** The deepest point: the dive's summary, else [streamMeters] from its depth chart when the viewer may see it. */
    fun maxDepth(dive: ActivityDive?, streamMeters: Double?, units: UnitSystem): LabeledText? =
        (dive?.maxDepth?.let { it / CM_PER_M } ?: streamMeters)?.let {
            LabeledText(AppText.named("stat_max_depth"), StatText.depth(it, units))
        }

    /** The whole dive, surface to surface: moving time means nothing underwater. */
    fun diveTime(record: ActivityRecord): LabeledText =
        LabeledText(AppText.named("stat_dive_time"), AppText.written(StatText.duration(record.elapsedTime)))

    /** The coldest water, which divers plan for, else the average. */
    fun waterTemp(record: ActivityRecord, units: UnitSystem): LabeledText? =
        (record.dive?.minTemperature ?: record.avgTemperature)?.let {
            LabeledText(AppText.named("stat_water_temp"), StatText.temperature(it, units))
        }

    private const val CM_PER_M = 100.0
}
