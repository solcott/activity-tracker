package io.github.solcott.activitytracker.data.training

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.SpeedZoneModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails

/** The threshold a [ZoneModel] scales: zones are percentages of it. */
enum class ZoneBasis {
    /** Lactate threshold heart rate. */
    ThresholdHeartRate,
    MaxHeartRate,

    /** Heart-rate reserve (Karvonen): resting heart rate plus a percentage of max minus resting. */
    HeartRateReserve,
    FunctionalThresholdPower,

    /**
     * Threshold pace, for speed zones. A zone's percentage is of threshold *pace* (time per distance), so 129 starts a zone at the speed of
     * a pace 29% slower than threshold.
     */
    ThresholdPace,
}

/** What a zone is called. The UI owns the words. */
enum class ZoneName {
    Recovery,
    Aerobic,
    Tempo,
    SubThreshold,
    SuperThreshold,
    AerobicCapacity,
    AnaerobicCapacity,
    ActiveRecovery,
    Endurance,
    Threshold,
    Vo2Max,
    Neuromuscular,
    VeryLight,
    Light,
    Moderate,
    Hard,
    Maximum,
}

/** Where a zone starts: [percent] of the model's basis. [label] is its number, like `2` or `5a`. */
@Immutable data class ZoneStart(val label: String, val name: ZoneName, val percent: Int)

/** A way of deriving zones from a threshold. [token] is what `actor.defs#training` stores. The first zone starts at 0. */
@Immutable data class ZoneModel(val token: String, val basis: ZoneBasis, val zones: List<ZoneStart>)

/** One computed zone, in bpm, watts or mm/s. [high] is null for the last, open-ended zone. */
@Immutable data class Zone(val label: String, val name: ZoneName, val low: Int, val high: Int?)

/** The zone models athletes can pick, from published formulas. Add a model here and give its name a string in the UI. */
object ZoneModels {
    private fun zones(vararg starts: Pair<ZoneName, Int>, labels: List<String> = starts.indices.map { "${it + 1}" }): List<ZoneStart> =
        zones(starts.asList(), labels)

    private fun zones(starts: List<Pair<ZoneName, Int>>, labels: List<String> = starts.indices.map { "${it + 1}" }): List<ZoneStart> =
        starts.mapIndexed { i, (name, percent) ->
            ZoneStart(labels[i], name, percent)
        }

    private val frielLabels = listOf("1", "2", "3", "4", "5a", "5b", "5c")

    val frielRunning =
        ZoneModel(
            HeartRateZoneModel.FRIEL_RUNNING,
            ZoneBasis.ThresholdHeartRate,
            zones(
                ZoneName.Recovery to 0,
                ZoneName.Aerobic to 85,
                ZoneName.Tempo to 90,
                ZoneName.SubThreshold to 95,
                ZoneName.SuperThreshold to 100,
                ZoneName.AerobicCapacity to 103,
                ZoneName.AnaerobicCapacity to 107,
                labels = frielLabels,
            ),
        )

    val frielCycling =
        ZoneModel(
            HeartRateZoneModel.FRIEL_CYCLING,
            ZoneBasis.ThresholdHeartRate,
            zones(
                ZoneName.Recovery to 0,
                ZoneName.Aerobic to 81,
                ZoneName.Tempo to 90,
                ZoneName.SubThreshold to 94,
                ZoneName.SuperThreshold to 100,
                ZoneName.AerobicCapacity to 103,
                ZoneName.AnaerobicCapacity to 107,
                labels = frielLabels,
            ),
        )

    val cogganHeartRate =
        ZoneModel(
            HeartRateZoneModel.COGGAN,
            ZoneBasis.ThresholdHeartRate,
            zones(
                ZoneName.ActiveRecovery to 0,
                ZoneName.Endurance to 69,
                ZoneName.Tempo to 84,
                ZoneName.Threshold to 95,
                ZoneName.Vo2Max to 106,
            ),
        )

    private val fiveIntensities =
        zones(
            ZoneName.VeryLight to 0,
            ZoneName.Light to 60,
            ZoneName.Moderate to 70,
            ZoneName.Hard to 80,
            ZoneName.Maximum to 90,
        )

    val percentMaxHeartRate = ZoneModel(HeartRateZoneModel.PERCENT_MAX, ZoneBasis.MaxHeartRate, fiveIntensities)

    val heartRateReserve = ZoneModel(HeartRateZoneModel.RESERVE, ZoneBasis.HeartRateReserve, fiveIntensities)

    private val cogganPowerStarts =
        listOf(
            ZoneName.ActiveRecovery to 0,
            ZoneName.Endurance to 56,
            ZoneName.Tempo to 76,
            ZoneName.Threshold to 91,
            ZoneName.Vo2Max to 106,
            ZoneName.AnaerobicCapacity to 121,
        )

    val cogganPower6 = ZoneModel(PowerZoneModel.COGGAN_6, ZoneBasis.FunctionalThresholdPower, zones(cogganPowerStarts))

    val cogganPower7 =
        ZoneModel(
            PowerZoneModel.COGGAN_7,
            ZoneBasis.FunctionalThresholdPower,
            zones(cogganPowerStarts + (ZoneName.Neuromuscular to 151)),
        )

    // Friel's pace zones, as TrainingPeaks offers them for running and swimming: each zone starts at a percentage of threshold pace.
    private val frielPaceStarts =
        zones(
            ZoneName.Recovery to 0,
            ZoneName.Aerobic to 129,
            ZoneName.Tempo to 114,
            ZoneName.SubThreshold to 106,
            ZoneName.SuperThreshold to 100,
            ZoneName.AerobicCapacity to 97,
            ZoneName.AnaerobicCapacity to 90,
            labels = frielLabels,
        )

    val frielRunningPace = ZoneModel(SpeedZoneModel.FRIEL_RUNNING, ZoneBasis.ThresholdPace, frielPaceStarts)

    val frielSwimmingPace = ZoneModel(SpeedZoneModel.FRIEL_SWIMMING, ZoneBasis.ThresholdPace, frielPaceStarts)

    val heartRate: List<ZoneModel> = listOf(frielRunning, frielCycling, cogganHeartRate, percentMaxHeartRate, heartRateReserve)

    val power: List<ZoneModel> = listOf(cogganPower6, cogganPower7)

    val defaultHeartRate: ZoneModel = cogganHeartRate

    val defaultPower: ZoneModel = cogganPower6

    val speed: List<ZoneModel> = listOf(frielRunningPace, frielSwimmingPace)

    /** The speed model a sport uses until one is picked. */
    fun defaultSpeed(sport: String?): ZoneModel = if (sport == Sport.SWIM) frielSwimmingPace else frielRunningPace

    /** The heart rate model stored as [token], or the default when it's unset or one this client doesn't know. */
    fun heartRate(token: String?): ZoneModel = heartRate.find { it.token == token } ?: defaultHeartRate

    /** The power model stored as [token], or the default when it's unset or one this client doesn't know. */
    fun power(token: String?): ZoneModel = power.find { it.token == token } ?: defaultPower

    /** The speed model stored as [token], or [sport]'s default when it's unset or one this client doesn't know. */
    fun speed(token: String?, sport: String?): ZoneModel = speed.find { it.token == token } ?: defaultSpeed(sport)
}

/** Computes zones from thresholds. */
object Zones {
    private const val PERCENT = 100
    private const val HALF = 50

    /** [model]'s zones for [training]'s defaults. */
    fun compute(model: ZoneModel, training: TrainingDetails): List<Zone>? = compute(model, training.forSport(null))

    /**
     * [model]'s zones for [training], in whole bpm, watts or mm/s: each zone starts at its percentage of the basis, rounded, and ends one
     * below the next. Null when the thresholds the model needs are missing, or resting heart rate isn't below max for heart-rate reserve.
     */
    fun compute(model: ZoneModel, training: EffectiveTraining): List<Zone>? {
        val lows = (if (model.basis == ZoneBasis.ThresholdPace) paceLows(model, training) else scaledLows(model, training)) ?: return null
        return model.zones.mapIndexed { i, start -> Zone(start.label, start.name, lows[i], lows.getOrNull(i + 1)?.minus(1)) }
    }

    private fun scaledLows(model: ZoneModel, training: EffectiveTraining): List<Int>? {
        val (offset, range) = scale(model.basis, training) ?: return null
        return model.zones.mapIndexed { i, start -> if (i == 0) 0 else (offset + (range * start.percent + HALF) / PERCENT).toInt() }
    }

    /** A slower pace is a lower speed: the speed at p% of threshold pace is threshold speed * 100 / p. */
    private fun paceLows(model: ZoneModel, training: EffectiveTraining): List<Int>? {
        val threshold = training.thresholdSpeed ?: return null
        return model.zones.mapIndexed { i, start -> if (i == 0) 0 else ((threshold * PERCENT + start.percent / 2) / start.percent).toInt() }
    }

    /** What 0% and 100% of [basis] are, as (offset, range); null when [training] lacks what it needs. */
    private fun scale(basis: ZoneBasis, training: EffectiveTraining): Pair<Long, Long>? =
        when (basis) {
            ZoneBasis.ThresholdHeartRate -> training.thresholdHeartRate?.let { 0L to it }
            ZoneBasis.MaxHeartRate -> training.maxHeartRate?.let { 0L to it }
            ZoneBasis.FunctionalThresholdPower -> training.functionalThresholdPower?.let { 0L to it }
            ZoneBasis.HeartRateReserve -> {
                val max = training.maxHeartRate
                val resting = training.restingHeartRate
                if (max != null && resting != null && resting < max) resting to max - resting else null
            }
            ZoneBasis.ThresholdPace -> null
        }
}

/** Threshold text fields: whole bpm or watts, or a pace. */
object ThresholdInput {
    /** Entered heart rate text in bpm; null when blank or outside the lexicon's range. */
    fun heartRate(text: String): Long? =
        text.trim().toLongOrNull()?.takeIf { it in TrainingDetails.MIN_HEART_RATE..TrainingDetails.MAX_HEART_RATE }

    /** Entered power text in watts; null when blank or outside the lexicon's range. */
    fun power(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it in 1..TrainingDetails.MAX_FTP }

    fun isValidHeartRate(text: String): Boolean = text.isBlank() || heartRate(text) != null

    fun isValidPower(text: String): Boolean = text.isBlank() || power(text) != null

    /** Entered pace text per [unit] in mm/s; null when blank, unparseable or outside the lexicon's range. */
    fun pace(text: String, unit: PaceUnit): Long? =
        PaceText.parse(text, unit)?.takeIf { it in TrainingDetails.MIN_THRESHOLD_SPEED..TrainingDetails.MAX_THRESHOLD_SPEED }

    fun isValidPace(text: String, unit: PaceUnit): Boolean = text.isBlank() || pace(text, unit) != null
}
