package io.github.solcott.activitytracker.data.training

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails

/** The threshold a [ZoneModel] scales: zones are percentages of it. */
enum class ZoneBasis {
    /** Lactate threshold heart rate. */
    ThresholdHeartRate,
    MaxHeartRate,

    /** Heart-rate reserve (Karvonen): resting heart rate plus a percentage of max minus resting. */
    HeartRateReserve,
    FunctionalThresholdPower,
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

/** One computed zone, in bpm or watts. [high] is null for the last, open-ended zone. */
@Immutable data class Zone(val label: String, val name: ZoneName, val low: Int, val high: Int?)

/** The zone models athletes can pick, from published formulas. Add a model here and give its name a string in the UI. */
object ZoneModels {
    private fun zones(vararg starts: Pair<ZoneName, Int>, labels: List<String> = starts.indices.map { "${it + 1}" }) =
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

    val cogganPower6 = ZoneModel(PowerZoneModel.COGGAN_6, ZoneBasis.FunctionalThresholdPower, zones(*cogganPowerStarts.toTypedArray()))

    val cogganPower7 =
        ZoneModel(
            PowerZoneModel.COGGAN_7,
            ZoneBasis.FunctionalThresholdPower,
            zones(*(cogganPowerStarts + (ZoneName.Neuromuscular to 151)).toTypedArray()),
        )

    val heartRate: List<ZoneModel> = listOf(frielRunning, frielCycling, cogganHeartRate, percentMaxHeartRate, heartRateReserve)

    val power: List<ZoneModel> = listOf(cogganPower6, cogganPower7)

    val defaultHeartRate: ZoneModel = cogganHeartRate

    val defaultPower: ZoneModel = cogganPower6

    /** The heart rate model stored as [token], or the default when it's unset or one this client doesn't know. */
    fun heartRate(token: String?): ZoneModel = heartRate.find { it.token == token } ?: defaultHeartRate

    /** The power model stored as [token], or the default when it's unset or one this client doesn't know. */
    fun power(token: String?): ZoneModel = power.find { it.token == token } ?: defaultPower
}

/** Computes zones from thresholds. */
object Zones {
    private const val PERCENT = 100
    private const val HALF = 50

    /**
     * [model]'s zones for [training], in whole bpm or watts: each zone starts at its percentage of the basis, rounded, and ends one below
     * the next. Null when the thresholds the model needs are missing, or resting heart rate isn't below max for heart-rate reserve.
     */
    fun compute(model: ZoneModel, training: TrainingDetails): List<Zone>? {
        val (offset, range) = scale(model.basis, training) ?: return null
        val lows = model.zones.mapIndexed { i, start -> if (i == 0) 0 else (offset + (range * start.percent + HALF) / PERCENT).toInt() }
        return model.zones.mapIndexed { i, start -> Zone(start.label, start.name, lows[i], lows.getOrNull(i + 1)?.minus(1)) }
    }

    /** What 0% and 100% of [basis] are, as (offset, range); null when [training] lacks what it needs. */
    private fun scale(basis: ZoneBasis, training: TrainingDetails): Pair<Long, Long>? =
        when (basis) {
            ZoneBasis.ThresholdHeartRate -> training.thresholdHeartRate?.let { 0L to it }
            ZoneBasis.MaxHeartRate -> training.maxHeartRate?.let { 0L to it }
            ZoneBasis.FunctionalThresholdPower -> training.functionalThresholdPower?.let { 0L to it }
            ZoneBasis.HeartRateReserve -> {
                val max = training.maxHeartRate
                val resting = training.restingHeartRate
                if (max != null && resting != null && resting < max) resting to max - resting else null
            }
        }
}

/** Threshold text fields: whole bpm or watts. */
object ThresholdInput {
    /** Entered heart rate text in bpm; null when blank or outside the lexicon's range. */
    fun heartRate(text: String): Long? =
        text.trim().toLongOrNull()?.takeIf { it in TrainingDetails.MIN_HEART_RATE..TrainingDetails.MAX_HEART_RATE }

    /** Entered power text in watts; null when blank or outside the lexicon's range. */
    fun power(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it in 1..TrainingDetails.MAX_FTP }

    fun isValidHeartRate(text: String): Boolean = text.isBlank() || heartRate(text) != null

    fun isValidPower(text: String): Boolean = text.isBlank() || power(text) != null
}
