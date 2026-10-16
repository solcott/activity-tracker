package io.github.solcott.activitytracker.activityfile

import kotlinx.datetime.LocalDateTime

/**
 * The FIT messages an import needs, already scaled to SI units by the SDK (positions in degrees, see [FitUnits]). Times are epoch
 * milliseconds and enum values are the SDK's names, in whatever case the SDK spells them ("RUNNING", "running", "eBiking"):
 * [DecodedActivityMapper] normalizes both.
 */
data class DecodedActivity(
    val fileId: DecodedSource?,
    val sessions: List<DecodedSummary>,
    val laps: List<DecodedSummary>,
    val records: List<DecodedSample>,
    /** Dive computer header values; null unless the file is a dive log. */
    val dive: DecodedDive? = null,
    /** The start as the device's clock read it, set only when the file's times have no zone (Shearwater logs). */
    val localStart: LocalDateTime? = null,
    /** The activity's name, when the file carries one (GPX `trk/name`, which Strava and Garmin Connect exports fill). */
    val title: String? = null,
    /** Each `device_info` message, in file order: devices usually write one at the start and another at the end. */
    val sensors: List<DecodedSensor> = emptyList(),
    /** Electronic shifting's front and rear gear change events, in file order. */
    val gearChanges: List<DecodedGearChange> = emptyList(),
    /** Each jump a mountain bike ride logged. */
    val jumps: List<DecodedJump> = emptyList(),
    /** The athlete's settings on the recording device: FIT `zones_target` and `user_profile`. Null when the file has neither. */
    val athlete: DecodedAthleteSettings? = null,
    /** Each switch of breathing gas the dive log placed in time, in time order, the first gas included when the file switched to it. */
    val gasSwitches: List<DecodedGasSwitch> = emptyList(),
    /** Tank pressure readings during the dive, in time order, each naming its tank by [DiveTank.sensor]. */
    val tankUpdates: List<DecodedTankUpdate> = emptyList(),
    /** The diver's notes on the dive, from a logbook that keeps them. */
    val notes: String? = null,
)

/** A switch to [gas] at [timestampMillis]. */
data class DecodedGasSwitch(val timestampMillis: Long, val gas: DiveGas)

/** A tank pressure reading: [sensor] is the [DiveTank.sensor] of the tank it's from. */
data class DecodedTankUpdate(val timestampMillis: Long, val sensor: String, val pressureBar: Double)

/**
 * The athlete's thresholds as the recording device had them set, as raw values: zero or out of range when unset. FIT `zones_target` holds
 * the first three and `user_profile` the resting heart rate.
 */
data class DecodedAthleteSettings(
    /** Beats per minute. */
    val maxHeartRate: Int? = null,
    /** Lactate threshold heart rate, beats per minute. */
    val thresholdHeartRate: Int? = null,
    /** Watts. */
    val functionalThresholdPower: Int? = null,
    /** Beats per minute. */
    val restingHeartRate: Int? = null,
)

/**
 * A FIT `device_info`: the recording device itself, its built-in sensors, or a paired one. Enum values are the SDK's names; [deviceType] is
 * the one the SDK resolved for [sourceType] (an ANT+, Bluetooth or local device type).
 */
data class DecodedSensor(
    val deviceIndex: Int?,
    /** "antplus", "bluetooth_low_energy", "local", ... */
    val sourceType: String?,
    val deviceType: String?,
    val manufacturer: String?,
    /** The SDK's name for a Garmin product, else the device's own product name, else the product number. */
    val product: String?,
    /** "new", "good", "ok", "low", "critical" or "charging". */
    val batteryStatus: String?,
    val batteryLevelPercent: Int?,
)

/**
 * A front or rear gear change, with the gears it changed to: their numbers as the derailleur reports them (rear gear 1 is the largest cog),
 * and their teeth as the device's bike profile has them, which may not match the bike.
 */
data class DecodedGearChange(
    val timestampMillis: Long?,
    val rear: Boolean,
    val frontGear: Int?,
    val frontTeeth: Int?,
    val rearGear: Int?,
    val rearTeeth: Int?,
)

data class DecodedJump(val hangTimeSeconds: Double?, val distanceMeters: Double?)

data class DecodedSource(
    /** File type; an activity file is "activity". */
    val type: String?,
    val manufacturer: String?,
    val product: String?,
    val timeCreatedMillis: Long?,
)

/** A session or lap message. */
data class DecodedSummary(
    val sport: String?,
    val subSport: String?,
    val startTimeMillis: Long?,
    val totalElapsedSeconds: Double?,
    /** Timer time, which excludes auto-pauses: what we call moving time. */
    val totalTimerSeconds: Double?,
    val totalDistanceMeters: Double?,
    val totalAscentMeters: Double?,
    val avgSpeedMps: Double?,
    val maxSpeedMps: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val avgPowerWatts: Int?,
    val avgCadence: Int?,
    val avgTemperatureCelsius: Double?,
    val maxPowerWatts: Int? = null,
    val normalizedPowerWatts: Int? = null,
    val maxCadence: Int? = null,
    val totalDescentMeters: Double? = null,
    val totalWorkJoules: Double? = null,
    /** The athlete's FTP when the device recorded it; intensity factor and TSS are relative to it. */
    val thresholdPowerWatts: Int? = null,
    val intensityFactor: Double? = null,
    val trainingStressScore: Double? = null,
    val frontShiftCount: Int? = null,
    val rearShiftCount: Int? = null,
    val jumpCount: Int? = null,
    /** Garmin's trail difficulty score. */
    val totalGrit: Double? = null,
    /** Garmin's score for how smoothly the trail was ridden; lower is smoother. */
    val avgFlow: Double? = null,
    /** Kilocalories. */
    val totalCalories: Int? = null,
    /** Aerobic training effect, 0 to 5. */
    val aerobicTrainingEffect: Double? = null,
    /** Anaerobic training effect, 0 to 5. */
    val anaerobicTrainingEffect: Double? = null,
)

/** One sample of the activity's record stream. */
data class DecodedSample(
    val timestampMillis: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val heartRate: Int?,
    val cadence: Int?,
    val powerWatts: Int?,
    val speedMps: Double?,
    val distanceMeters: Double?,
    val temperatureCelsius: Double?,
    /** Depth below the surface, for dives. */
    val depthMeters: Double? = null,
    /** Partial pressure of the oxygen breathed, as the dive computer reported or calculated it. */
    val ppO2Bar: Double? = null,
    /** Each oxygen cell's reading on a rebreather; when there are any they win over [ppO2Bar]. */
    val measuredPpO2Bars: List<Double> = emptyList(),
    /** Central nervous system oxygen toxicity, in percent of the limit. */
    val cnsPercent: Int? = null,
    /** Nitrogen loading, in percent of the limit. */
    val n2LoadPercent: Int? = null,
    /** No-decompression limit; 0 while in deco. */
    val ndlSeconds: Long? = null,
    /** The next decompression stop (the ceiling); null when not in deco. */
    val nextStopDepthMeters: Double? = null,
    val nextStopSeconds: Long? = null,
    /** Time to surface. */
    val ttsSeconds: Long? = null,
    /** Breaths per minute. */
    val respirationRate: Double? = null,
    val verticalOscillationMm: Double? = null,
    val stanceTimeMs: Double? = null,
    val stepLengthMm: Double? = null,
    /** Vertical oscillation over step length, in percent. */
    val verticalRatioPercent: Double? = null,
    /** The left foot's share of ground contact time, in percent. */
    val stanceTimeBalancePercent: Double? = null,
    /** FIT `left_right_balance` as recorded: the low 7 bits are a percent, from the right leg when [FitBalance.RIGHT] is set. */
    val leftRightBalance: Int? = null,
)

/** FIT's `left_right_balance` bits. */
object FitBalance {
    const val RIGHT = 0x80
    const val MASK = 0x7F
    private const val MAX_PERCENT = 100

    /** The percent of power from the right leg, or null when the device didn't say which side it measured. */
    fun rightPercent(raw: Int): Int? = if (raw and RIGHT != 0) (raw and MASK).takeIf { it <= MAX_PERCENT } else null
}

/** A dive log's header, in SI units. */
data class DecodedDive(
    val maxDepthMeters: Double?,
    /** The dive computer's own count. */
    val diveNumber: Int?,
    /** Time at the surface since the previous dive. */
    val surfaceIntervalMinutes: Int?,
    /** Bühlmann gradient factors, in percent. */
    val gradientFactorLow: Int?,
    val gradientFactorHigh: Int?,
    /** Central nervous system oxygen toxicity, in percent of the limit. */
    val startCns: Int?,
    val endCns: Int?,
    /** Each gas breathed, in order of first use. */
    val gases: List<DiveGas>,
    /** Breathed from a closed-circuit rebreather for at least part of the dive. */
    val closedCircuit: Boolean,
    /** The dive computer's own average; null to compute it from the depth profile. */
    val avgDepthMeters: Double? = null,
    /** Time from leaving the surface to the start of the final ascent. */
    val bottomTimeSeconds: Double? = null,
    /** Whole-body oxygen exposure, in oxygen toxicity units. */
    val oxygenToxicityOtu: Int? = null,
    /** Air consumption by volume (respiratory minute volume), which needs the tank's size. */
    val rmvLitersPerMinute: Double? = null,
    /** Air consumption by tank pressure, adjusted to the surface. */
    val sacBarPerMinute: Double? = null,
    /** One per tank a pressure transmitter was on. */
    val tanks: List<DiveTank> = emptyList(),
    /** Where the diver went in and came out, if the computer had a GPS fix at the surface; a logbook's site position is the entry. */
    val entry: DivePosition? = null,
    /** The dive site's name, from a logbook that names it. */
    val siteName: String? = null,
    val exit: DivePosition? = null,
    /** Nitrogen loading, in percent of the limit. */
    val startN2: Int? = null,
    val endN2: Int? = null,
    val avgAscentRateMps: Double? = null,
    val maxAscentRateMps: Double? = null,
    val avgDescentRateMps: Double? = null,
    val maxDescentRateMps: Double? = null,
    /** Time from leaving the surface to reaching the maximum depth. */
    val descentTimeSeconds: Double? = null,
    /** A `WaterType` token. */
    val waterType: String? = null,
    val waterDensityKgPerM3: Double? = null,
    val airTemperatureCelsius: Double? = null,
    val visibilityMeters: Double? = null,
    /** The site's altitude above sea level. */
    val altitudeMeters: Double? = null,
    /** Time after the dive before flying. */
    val noFlyTimeSeconds: Double? = null,
    /** Time after the dive until fully desaturated. */
    val desaturationTimeSeconds: Double? = null,
    val highestPpO2Bar: Double? = null,
)

/**
 * A tank's pressures, in bar (the diving convention), the gas used, in liters at the surface when the tank's size was set, the tank's own
 * water volume and the gas it held. [sensor] names it in [DecodedActivity.tankUpdates].
 */
data class DiveTank(
    val startPressureBar: Double?,
    val endPressureBar: Double?,
    val volumeUsedLiters: Double?,
    val volumeLiters: Double? = null,
    val gas: DiveGas? = null,
    val sensor: String? = null,
)

data class DivePosition(val latitude: Double, val longitude: Double)

/** A breathing gas, as whole percents. Air is 21/0. */
data class DiveGas(val oxygenPercent: Int, val heliumPercent: Int)

object FitUnits {
    /** FIT stores positions as semicircles: 2^31 semicircles = 180°. */
    private const val DEGREES_PER_SEMICIRCLE = 180.0 / 2_147_483_648.0

    fun semicirclesToDegrees(semicircles: Int): Double = semicircles * DEGREES_PER_SEMICIRCLE
}
