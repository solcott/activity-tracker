package io.github.solcott.activitytracker.lexicon.app

import io.github.solcott.activitytracker.lexicon.AtUri
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The detail categories an activity's data is grouped into, each with its own [Visibility] no wider than the activity's: the tokens of
 * `actor.activityDefaults#details`, an open set. Every [ActivityRecord] field, stream channel and [Lap]/[Leg] field is in exactly one
 * category or is core ([CORE_RECORD_FIELDS], [CORE_CHANNELS], [CORE_LAP_FIELDS]), and core data is always at the activity's tier.
 *
 * The client splitter ([recordFor], [detailsFor], [streamsFor]) and both mergers ([effectiveRecord], [ActivityStreamData.overlaid]) read
 * this one table, so a field moves category in one place.
 */
object DetailCategory {
    const val START_TIME = "startTime"
    const val HEART_RATE = "heartRate"
    const val POWER = "power"
    const val SPEED = "speed"
    const val CALORIES = "calories"
    const val MAP = "map"
    const val PHYSIOLOGY = "physiology"
    const val CADENCE = "cadence"
    const val DIVE = "dive"

    /** Every known category, in the order pickers list them. */
    val all = listOf(START_TIME, HEART_RATE, POWER, SPEED, CALORIES, MAP, PHYSIOLOGY, CADENCE, DIVE)

    /** A summary field of [ActivityRecord] in a category, mirrored on [ActivityDetailsRecord] under the same [name]. */
    @Suppress("LongParameterList") // How to read, strip, copy and lay over one field on each record.
    class SummaryField(
        val name: String,
        val category: String,
        /** Whether the activity record has it. */
        val isSet: (ActivityRecord) -> Boolean,
        /** The activity record without it (for [START_TIME], with `startedAt` rounded to its local day instead). */
        val strip: (ActivityRecord) -> ActivityRecord,
        /** The details record with the activity record's value. */
        val copy: (ActivityDetailsRecord, ActivityRecord) -> ActivityDetailsRecord,
        /** The activity record with the details record's value, when it has one. */
        val overlay: (ActivityRecord, ActivityDetailsRecord) -> ActivityRecord,
        /** Whether the details record has it. */
        val inDetails: (ActivityDetailsRecord) -> Boolean,
    )

    val SUMMARY_FIELDS: List<SummaryField> =
        listOf(
            SummaryField(
                "startedAt",
                START_TIME,
                { true },
                { it.copy(startedAt = localMidnight(it.startedAt, it.utcOffset)) },
                { d, r -> d.copy(startedAt = r.startedAt) },
                { r, d -> d.startedAt?.let { r.copy(startedAt = it) } ?: r },
                { it.startedAt != null },
            ),
            SummaryField(
                "polyline",
                MAP,
                { it.polyline != null },
                { it.copy(polyline = null) },
                { d, r -> d.copy(polyline = r.polyline) },
                { r, d -> d.polyline?.let { r.copy(polyline = it) } ?: r },
                { it.polyline != null },
            ),
            SummaryField(
                "avgSpeed",
                SPEED,
                { it.avgSpeed != null },
                { it.copy(avgSpeed = null) },
                { d, r -> d.copy(avgSpeed = r.avgSpeed) },
                { r, d -> d.avgSpeed?.let { r.copy(avgSpeed = it) } ?: r },
                { it.avgSpeed != null },
            ),
            SummaryField(
                "maxSpeed",
                SPEED,
                { it.maxSpeed != null },
                { it.copy(maxSpeed = null) },
                { d, r -> d.copy(maxSpeed = r.maxSpeed) },
                { r, d -> d.maxSpeed?.let { r.copy(maxSpeed = it) } ?: r },
                { it.maxSpeed != null },
            ),
            SummaryField(
                "avgHeartRate",
                HEART_RATE,
                { it.avgHeartRate != null },
                { it.copy(avgHeartRate = null) },
                { d, r -> d.copy(avgHeartRate = r.avgHeartRate) },
                { r, d -> d.avgHeartRate?.let { r.copy(avgHeartRate = it) } ?: r },
                { it.avgHeartRate != null },
            ),
            SummaryField(
                "maxHeartRate",
                HEART_RATE,
                { it.maxHeartRate != null },
                { it.copy(maxHeartRate = null) },
                { d, r -> d.copy(maxHeartRate = r.maxHeartRate) },
                { r, d -> d.maxHeartRate?.let { r.copy(maxHeartRate = it) } ?: r },
                { it.maxHeartRate != null },
            ),
            SummaryField(
                "avgCadence",
                CADENCE,
                { it.avgCadence != null },
                { it.copy(avgCadence = null) },
                { d, r -> d.copy(avgCadence = r.avgCadence) },
                { r, d -> d.avgCadence?.let { r.copy(avgCadence = it) } ?: r },
                { it.avgCadence != null },
            ),
            SummaryField(
                "maxCadence",
                CADENCE,
                { it.maxCadence != null },
                { it.copy(maxCadence = null) },
                { d, r -> d.copy(maxCadence = r.maxCadence) },
                { r, d -> d.maxCadence?.let { r.copy(maxCadence = it) } ?: r },
                { it.maxCadence != null },
            ),
            SummaryField(
                "avgPower",
                POWER,
                { it.avgPower != null },
                { it.copy(avgPower = null) },
                { d, r -> d.copy(avgPower = r.avgPower) },
                { r, d -> d.avgPower?.let { r.copy(avgPower = it) } ?: r },
                { it.avgPower != null },
            ),
            SummaryField(
                "maxPower",
                POWER,
                { it.maxPower != null },
                { it.copy(maxPower = null) },
                { d, r -> d.copy(maxPower = r.maxPower) },
                { r, d -> d.maxPower?.let { r.copy(maxPower = it) } ?: r },
                { it.maxPower != null },
            ),
            SummaryField(
                "normalizedPower",
                POWER,
                { it.normalizedPower != null },
                { it.copy(normalizedPower = null) },
                { d, r -> d.copy(normalizedPower = r.normalizedPower) },
                { r, d -> d.normalizedPower?.let { r.copy(normalizedPower = it) } ?: r },
                { it.normalizedPower != null },
            ),
            SummaryField(
                "work",
                POWER,
                { it.work != null },
                { it.copy(work = null) },
                { d, r -> d.copy(work = r.work) },
                { r, d -> d.work?.let { r.copy(work = it) } ?: r },
                { it.work != null },
            ),
            SummaryField(
                "calories",
                CALORIES,
                { it.calories != null },
                { it.copy(calories = null) },
                { d, r -> d.copy(calories = r.calories) },
                { r, d -> d.calories?.let { r.copy(calories = it) } ?: r },
                { it.calories != null },
            ),
        )

    /** [ActivityRecord] fields in no category, always at the activity's tier, by serial name. */
    val CORE_RECORD_FIELDS: Set<String> =
        setOf(
            "sport",
            "subSport",
            "title",
            "description",
            "elapsedTime",
            "movingTime",
            "distance",
            "elevationGain",
            "elevationLoss",
            "avgTemperature",
            "device",
            "sensors",
            "dive",
            "recordingOf",
            "media",
            "frontShifts",
            "rearShifts",
            "jumps",
            "grit",
            "flow",
            "poolLength",
            "cycles",
            "avgStrokeDistance",
            "utcOffset",
            "createdAt",
            "\$type",
        )

    /** Stream channels by category. Lap and leg fields are split by [LAP_FIELDS] instead. */
    val CHANNELS: Map<String, Set<String>> =
        mapOf(
            HEART_RATE to setOf(StreamChannel.HEART_RATE),
            POWER to
                setOf(
                    StreamChannel.POWER,
                    StreamChannel.LEFT_RIGHT_BALANCE,
                    StreamChannel.TORQUE_EFFECTIVENESS_LEFT,
                    StreamChannel.TORQUE_EFFECTIVENESS_RIGHT,
                    StreamChannel.PEDAL_SMOOTHNESS_LEFT,
                    StreamChannel.PEDAL_SMOOTHNESS_RIGHT,
                    StreamChannel.PCO_LEFT,
                    StreamChannel.PCO_RIGHT,
                    StreamChannel.FORM_POWER,
                ),
            SPEED to setOf(StreamChannel.SPEED),
            PHYSIOLOGY to
                setOf(
                    StreamChannel.RESPIRATION_RATE,
                    StreamChannel.CORE_TEMPERATURE,
                    StreamChannel.SM_O2,
                    StreamChannel.TOTAL_HEMOGLOBIN,
                ),
            CADENCE to
                setOf(
                    StreamChannel.CADENCE,
                    StreamChannel.VERTICAL_OSCILLATION,
                    StreamChannel.STANCE_TIME,
                    StreamChannel.STEP_LENGTH,
                    StreamChannel.VERTICAL_RATIO,
                    StreamChannel.STANCE_TIME_BALANCE,
                    StreamChannel.LEG_SPRING_STIFFNESS,
                ),
            DIVE to
                setOf(
                    StreamChannel.DEPTH,
                    StreamChannel.PP_O2,
                    StreamChannel.CNS,
                    StreamChannel.N2_LOAD,
                    StreamChannel.NDL,
                    StreamChannel.DECO_STOP_DEPTH,
                    StreamChannel.DECO_STOP_TIME,
                    StreamChannel.TTS,
                    StreamChannel.GAS_SWITCHES,
                    StreamChannel.DIVE_ALERTS,
                    StreamChannel.TANK_PRESSURES,
                ),
        )

    /**
     * Stream channels in no category, always in the activity's tier record. [StreamChannel.LAPS] and `legs` hold their core fields there.
     */
    val CORE_CHANNELS: Set<String> =
        setOf(
            StreamChannel.DISTANCE,
            StreamChannel.ALTITUDE,
            StreamChannel.TEMPERATURE,
            StreamChannel.GEARS,
            StreamChannel.EBIKE_BATTERY,
            StreamChannel.EBIKE_ASSIST_MODE,
            StreamChannel.EBIKE_ASSIST_LEVEL,
            StreamChannel.LAPS,
            StreamChannel.LENGTHS,
            StreamChannel.SETS,
            StreamChannel.LEGS,
        )

    /** [Lap] and [Leg] fields by category, by serial name. */
    val LAP_FIELDS: Map<String, Set<String>> =
        mapOf(
            HEART_RATE to setOf("avgHeartRate", "maxHeartRate"),
            POWER to setOf("avgPower"),
            SPEED to setOf("avgSpeed", "maxSpeed"),
            CADENCE to setOf("avgCadence"),
            CALORIES to setOf("calories"),
        )

    /**
     * [Lap] and [Leg] fields in no category: their structure, in the activity's tier record. Every record holding any field of a lap or leg
     * also holds its `start` and `elapsed` (and a leg's required `sport`), so viewers can line them up.
     */
    val CORE_LAP_FIELDS: Set<String> =
        setOf("start", "elapsed", "moving", "distance", "ascent", "descent", "intensity", "trigger", "sport", "subSport", "transition")

    /** The category [channel] is in, or null when it's core (or unknown, which is treated as core). */
    fun ofChannel(channel: String): String? = CHANNELS.entries.firstOrNull { channel in it.value }?.key

    /** The start of [startedAt]'s local day, by [utcOffset] minutes east of UTC (UTC when null), as an instant. */
    fun localMidnight(startedAt: Instant, utcOffset: Int?): Instant {
        val offset = (utcOffset ?: 0).minutes
        val local = startedAt + offset
        val day = 1.days.inWholeSeconds
        val midnight = Instant.fromEpochSeconds(local.epochSeconds.floorDiv(day) * day)
        return midnight - offset
    }

    /**
     * The tier ([Visibility] value) each category is stored at for an activity at [activity]: [categories]' value capped at the activity,
     * or the activity's when a category is missing.
     */
    fun tiers(activity: String, categories: Map<String, String>): Map<String, String> = all.associateWith {
        categories[it]?.let { v -> Visibility.capped(Visibility.known(v), activity) } ?: activity
    }

    /** [record] as stored at the activity's tier: without every summary field whose category is narrower ([tiers]). */
    fun recordFor(record: ActivityRecord, activity: String, categories: Map<String, String>): ActivityRecord {
        val tiers = tiers(activity, categories)
        return SUMMARY_FIELDS.filter { tiers.getValue(it.category) != activity }.fold(record) { r, f -> f.strip(r) }
    }

    /**
     * The details record for each tier narrower than [activity] that holds any of [record]'s summary fields, by tier. Empty when every
     * category with data is at the activity's tier.
     */
    fun detailsFor(
        record: ActivityRecord,
        uri: AtUri,
        activity: String,
        categories: Map<String, String>,
    ): Map<String, ActivityDetailsRecord> {
        val tiers = tiers(activity, categories)
        return SUMMARY_FIELDS.filter { tiers.getValue(it.category) != activity && it.isSet(record) }
            .groupBy { tiers.getValue(it.category) }
            .mapValues { (_, fields) ->
                fields.fold(ActivityDetailsRecord(activity = uri, createdAt = record.createdAt)) { d, f -> f.copy(d, record) }
            }
    }

    /**
     * [data] split into one [ActivityStreamData] per tier that holds any of it, by tier: each with [ActivityStreamData.time] and the
     * channels and lap and leg fields whose category is at that tier, the activity's tier also holding the core ones.
     */
    fun streamsFor(data: ActivityStreamData, activity: String, categories: Map<String, String>): Map<String, ActivityStreamData> {
        val tiers = tiers(activity, categories)
        return Visibility.all
            .mapNotNull { tier ->
                val inTier = all.filter { tiers.getValue(it) == tier }.toSet()
                data.forTier(inTier, core = tier == activity)?.let { tier to it }
            }
            .toMap()
    }

    /**
     * The tier each category with data is stored at, read from where it appears: [parts] (streams, each with its tier), then [record] and
     * [details] for categories with no channels or lap fields. So an activity from before categories, with one streams record, reads its
     * channel categories at that record's tier and the rest at [activity]. A category with no data anywhere is absent. A category found in
     * several tiers (an interrupted move) reads as the widest.
     */
    fun currentTiers(
        activity: String,
        record: ActivityRecord,
        details: List<DetailsView>,
        parts: List<Pair<String, ActivityStreamData>>,
    ): Map<String, String> = currentTiersOf(activity, record, details, parts.map { (tier, data) -> tier to data.heldCategories() })

    /**
     * As [currentTiers], from what `getActivityStreams` says of each part: its tier and channels (or, from an AppView that predates
     * categories, one part at the output's visibility). A category in a part only by its lap and leg fields isn't seen here.
     */
    fun currentTiers(activity: String, view: ActivityView, streams: GetActivityStreams.Output?): Map<String, String> {
        val parts = streams?.parts ?: streams?.let { listOf(GetActivityStreams.StreamsPart(it.visibility, it.channels)) }.orEmpty()
        return currentTiersOf(
            activity,
            view.record,
            view.details.orEmpty(),
            parts.map { part -> Visibility.known(part.visibility) to part.channels.mapNotNull { ofChannel(it) }.toSet() },
        )
    }

    private fun currentTiersOf(
        activity: String,
        record: ActivityRecord,
        details: List<DetailsView>,
        parts: List<Pair<String, Set<String>>>,
    ): Map<String, String> {
        fun MutableMap<String, String>.saw(category: String, tier: String) {
            val known = this[category]
            if (known == null || Visibility.rank(tier) < Visibility.rank(known)) this[category] = tier
        }
        val fromStreams = mutableMapOf<String, String>()
        for ((tier, categories) in parts) categories.forEach { fromStreams.saw(it, tier) }
        val fromSummary = mutableMapOf<String, String>()
        SUMMARY_FIELDS.filter { it.category != START_TIME && it.isSet(record) }.forEach { fromSummary.saw(it.category, activity) }
        for (d in details) SUMMARY_FIELDS.filter { it.inDetails(d.record) }.forEach { fromSummary.saw(it.category, d.visibility) }
        // The record always has a start: it's exact unless a details record holds the exact one.
        if (START_TIME !in fromSummary) fromSummary[START_TIME] = activity
        return fromSummary + fromStreams
    }
}

/** [record] with each of [details] laid over it, in order (widest first, so a narrower tier's value wins). */
fun effectiveRecord(record: ActivityRecord, details: List<ActivityDetailsRecord>): ActivityRecord =
    details.fold(record) { r, d -> DetailCategory.SUMMARY_FIELDS.fold(r) { acc, f -> f.overlay(acc, d) } }

/** The activity as this viewer may see it: [ActivityView.record] with [ActivityView.details] laid over it. */
fun ActivityView.effectiveRecord(): ActivityRecord = effectiveRecord(record, details.orEmpty().map { it.record })
