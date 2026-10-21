@file:JsModule("@garmin/fitsdk")

package io.github.solcott.activitytracker.activityfile

// Bindings for the parts of Garmin's JS FIT SDK (npm @garmin/fitsdk) that GarminJsFitDecoder uses.

internal external class Stream(arrayBuffer: JsAny) : JsAny

/** The SDK's profile: `types` maps each FIT type's name to its values' names, the per-category exercise-name types among them. */
internal external object Profile : JsAny {
    val types: JsAny
}

internal external class Decoder(stream: Stream) : JsAny {
    fun isFIT(): Boolean

    fun checkIntegrity(): Boolean

    /** [options] is a JS object of the SDK's decode options (`mergeHeartRates`, ...). */
    fun read(options: JsAny): FitReadResult
}

internal external interface FitReadResult : JsAny {
    val messages: FitMessages
    val errors: JsArray<JsAny>
}

internal external interface FitMessages : JsAny {
    val fileIdMesgs: JsArray<JsFileIdMesg>?
    val sessionMesgs: JsArray<JsSummaryMesg>?
    val lapMesgs: JsArray<JsSummaryMesg>?
    val recordMesgs: JsArray<JsRecordMesg>?
    val diveSummaryMesgs: JsArray<JsDiveSummaryMesg>?
    val diveSettingsMesgs: JsArray<JsDiveSettingsMesg>?
    val diveGasMesgs: JsArray<JsDiveGasMesg>?
    val eventMesgs: JsArray<JsEventMesg>?
    val tankSummaryMesgs: JsArray<JsTankSummaryMesg>?
    val tankUpdateMesgs: JsArray<JsTankUpdateMesg>?
    val deviceInfoMesgs: JsArray<JsDeviceInfoMesg>?
    val jumpMesgs: JsArray<JsJumpMesg>?
    val zonesTargetMesgs: JsArray<JsZonesTargetMesg>?
    val userProfileMesgs: JsArray<JsUserProfileMesg>?
    val lengthMesgs: JsArray<JsLengthMesg>?
    val setMesgs: JsArray<JsSetMesg>?
    val splitMesgs: JsArray<JsSplitMesg>?
    val hrMesgs: JsArray<JsHrMesg>?
    val hrvMesgs: JsArray<JsHrvMesg>?
    val activityMesgs: JsArray<JsActivityMesg>?
    val fieldDescriptionMesgs: JsArray<JsFieldDescriptionMesg>?
}

// Everything is typed JsAny? and converted in GarminJsFitDecoder: an absent field is undefined, which a Double? property reads as NaN
// rather than null. Enum fields are strings, or numbers for values newer than the SDK's profile. Date fields are JS Dates.
internal external interface JsFileIdMesg : JsAny {
    val type: JsAny?
    val manufacturer: JsAny?
    val garminProduct: JsAny?
    val product: JsAny?
    val productName: String?
    val timeCreated: JsAny?
    val serialNumber: JsAny?
}

internal external interface JsSummaryMesg : JsAny {
    val sport: JsAny?
    val subSport: JsAny?
    val startTime: JsAny?
    val totalElapsedTime: JsAny?
    val totalTimerTime: JsAny?
    val totalDistance: JsAny?
    val totalAscent: JsAny?
    val avgSpeed: JsAny?
    val enhancedAvgSpeed: JsAny?
    val maxSpeed: JsAny?
    val enhancedMaxSpeed: JsAny?
    val avgHeartRate: JsAny?
    val maxHeartRate: JsAny?
    val avgPower: JsAny?
    val avgCadence: JsAny?
    val avgTemperature: JsAny?
    val maxPower: JsAny?
    val normalizedPower: JsAny?
    val maxCadence: JsAny?
    val totalDescent: JsAny?
    val totalWork: JsAny?
    val frontGearShiftCount: JsAny?
    val rearGearShiftCount: JsAny?
    val jumpCount: JsAny?
    val totalGrit: JsAny?
    val avgFlow: JsAny?
    val totalCalories: JsAny?
    val totalTrainingEffect: JsAny?
    val totalAnaerobicTrainingEffect: JsAny?
    // Lap only.
    val intensity: JsAny?
    val lapTrigger: JsAny?
    // Session only: Garmin's exercise load.
    val trainingLoadPeak: JsAny?
    // Session only: a pool swim's pool length, in meters.
    val poolLength: JsAny?
    // Session only: strides, strokes or revolutions, and the average stroke distance.
    val totalCycles: JsAny?
    val avgStrokeDistance: JsAny?
    val sportProfileName: JsAny?
    // Session only: the FTP that intensity factor and TSS are relative to.
    val thresholdPower: JsAny?
    val intensityFactor: JsAny?
    val trainingStressScore: JsAny?
    // Session only: where a dive went in and came out, and its own dive fields.
    val startPositionLat: JsAny?
    val startPositionLong: JsAny?
    val endPositionLat: JsAny?
    val endPositionLong: JsAny?
    val maxDepth: JsAny?
    val avgDepth: JsAny?
    val diveNumber: JsAny?
}

internal external interface JsRecordMesg : JsAny {
    val timestamp: JsAny?
    val positionLat: JsAny?
    val positionLong: JsAny?
    val altitude: JsAny?
    val enhancedAltitude: JsAny?
    val heartRate: JsAny?
    val cadence: JsAny?
    val power: JsAny?
    val speed: JsAny?
    val enhancedSpeed: JsAny?
    val distance: JsAny?
    val temperature: JsAny?
    val depth: JsAny?
    val po2: JsAny?
    val cnsLoad: JsAny?
    val n2Load: JsAny?
    val ndlTime: JsAny?
    val nextStopDepth: JsAny?
    val nextStopTime: JsAny?
    val timeToSurface: JsAny?
    val respirationRate: JsAny?
    val enhancedRespirationRate: JsAny?
    val verticalOscillation: JsAny?
    val stanceTime: JsAny?
    val stepLength: JsAny?
    val verticalRatio: JsAny?
    val stanceTimeBalance: JsAny?
    val leftRightBalance: JsAny?
    val leftTorqueEffectiveness: JsAny?
    val rightTorqueEffectiveness: JsAny?
    val leftPedalSmoothness: JsAny?
    val rightPedalSmoothness: JsAny?
    val leftPco: JsAny?
    val rightPco: JsAny?
    val coreTemperature: JsAny?
    val saturatedHemoglobinPercent: JsAny?
    val totalHemoglobinConc: JsAny?
    val ebikeBatteryLevel: JsAny?
    val ebikeAssistMode: JsAny?
    val ebikeAssistLevelPercent: JsAny?
    /** Developer fields' values, keyed by their `field_description`'s `key`. */
    val developerFields: JsAny?
}

/** [eventTimestamp] and [filteredBpm] are arrays, or single values in a message with one reading. */
internal external interface JsHrMesg : JsAny {
    val timestamp: JsAny?
    val fractionalTimestamp: JsAny?
    val eventTimestamp: JsAny?
    val filteredBpm: JsAny?
}

/** [time] is an array of RR intervals in seconds, or a single value. */
internal external interface JsHrvMesg : JsAny {
    val time: JsAny?
}

/** [localTimestamp] stays a number of seconds since the FIT epoch: the SDK converts only UTC date fields to Dates. */
internal external interface JsActivityMesg : JsAny {
    val timestamp: JsAny?
    val localTimestamp: JsAny?
}

/** A developer field's description; the SDK gives it a [key] that records' `developerFields` are keyed by. */
internal external interface JsFieldDescriptionMesg : JsAny {
    val key: JsAny?
    val fieldName: JsAny?
    val units: JsAny?
    /** The field's scale and offset, which the SDK doesn't apply to developer field values. */
    val scale: JsAny?
    val offset: JsAny?
}

internal external interface JsDiveSummaryMesg : JsAny {
    val referenceMesg: JsAny?
    val avgDepth: JsAny?
    val maxDepth: JsAny?
    val surfaceInterval: JsAny?
    val diveNumber: JsAny?
    val bottomTime: JsAny?
    val startCns: JsAny?
    val endCns: JsAny?
    val o2Toxicity: JsAny?
    val avgRmv: JsAny?
    val avgVolumeSac: JsAny?
    val avgPressureSac: JsAny?
    val startN2: JsAny?
    val endN2: JsAny?
    val avgAscentRate: JsAny?
    val maxAscentRate: JsAny?
    val avgDescentRate: JsAny?
    val maxDescentRate: JsAny?
    val descentTime: JsAny?
}

internal external interface JsDiveSettingsMesg : JsAny {
    val gfLow: JsAny?
    val gfHigh: JsAny?
    val waterType: JsAny?
    val waterDensity: JsAny?
    val model: JsAny?
    val po2Warn: JsAny?
    val po2Critical: JsAny?
    val po2Deco: JsAny?
    /** FIT's bool: the SDK leaves it a number (or, in a later SDK, a boolean). */
    val safetyStopEnabled: JsAny?
    val safetyStopTime: JsAny?
    val ccrLowSetpoint: JsAny?
    val ccrHighSetpoint: JsAny?
}

internal external interface JsDiveGasMesg : JsAny {
    val messageIndex: JsAny?
    val oxygenContent: JsAny?
    val heliumContent: JsAny?
    val status: JsAny?
    val mode: JsAny?
}

internal external interface JsEventMesg : JsAny {
    /** `data`'s subfield for a `dive_alert` event, as the SDK names the value. */
    val diveAlert: JsAny?
    val timestamp: JsAny?
    val event: JsAny?
    val data: JsAny?
    // Gear change events, expanded from data by the SDK: the gear changed to, by number and in teeth.
    val frontGearNum: JsAny?
    val frontGear: JsAny?
    val rearGearNum: JsAny?
    val rearGear: JsAny?
}

/** The SDK resolves device_type into the subfield for the source: antplusDeviceType, bleDeviceType or localDeviceType. */
internal external interface JsDeviceInfoMesg : JsAny {
    val deviceIndex: JsAny?
    val sourceType: JsAny?
    val antplusDeviceType: JsAny?
    val bleDeviceType: JsAny?
    val localDeviceType: JsAny?
    val manufacturer: JsAny?
    val garminProduct: JsAny?
    val product: JsAny?
    val productName: String?
    val batteryStatus: JsAny?
    val batteryLevel: JsAny?
    val serialNumber: JsAny?
    val antDeviceNumber: JsAny?
    val antTransmissionType: JsAny?
    val softwareVersion: JsAny?
    val batteryVoltage: JsAny?
    val cumOperatingTime: JsAny?
}

internal external interface JsJumpMesg : JsAny {
    val hangTime: JsAny?
    val distance: JsAny?
}

internal external interface JsTankSummaryMesg : JsAny {
    val startPressure: JsAny?
    val endPressure: JsAny?
    val volumeUsed: JsAny?
    val sensor: JsAny?
}

internal external interface JsTankUpdateMesg : JsAny {
    val timestamp: JsAny?
    val sensor: JsAny?
    val pressure: JsAny?
}

internal external interface JsZonesTargetMesg : JsAny {
    val maxHeartRate: JsAny?
    val thresholdHeartRate: JsAny?
    val functionalThresholdPower: JsAny?
}

internal external interface JsUserProfileMesg : JsAny {
    val restingHeartRate: JsAny?
}

internal external interface JsLengthMesg : JsAny {
    val startTime: JsAny?
    val totalElapsedTime: JsAny?
    val swimStroke: JsAny?
    val totalStrokes: JsAny?
    val lengthType: JsAny?
}

/** [category] and [categorySubtype] are arrays (a value each, the subtype's meaning set by its category); a set uses the first. */
internal external interface JsSetMesg : JsAny {
    val startTime: JsAny?
    val timestamp: JsAny?
    val duration: JsAny?
    val setType: JsAny?
    val category: JsAny?
    val categorySubtype: JsAny?
    val repetitions: JsAny?
    val weight: JsAny?
}

internal external interface JsSplitMesg : JsAny {
    val splitType: JsAny?
    val startTime: JsAny?
    val totalElapsedTime: JsAny?
    val totalTimerTime: JsAny?
    val totalDistance: JsAny?
    val totalAscent: JsAny?
    val totalDescent: JsAny?
    val avgSpeed: JsAny?
    val maxSpeed: JsAny?
    val avgHeartRate: JsAny?
    val maxHeartRate: JsAny?
    val totalCalories: JsAny?
}
