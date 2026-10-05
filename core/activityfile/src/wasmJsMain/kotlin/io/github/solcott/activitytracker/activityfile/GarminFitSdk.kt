@file:JsModule("@garmin/fitsdk")

package io.github.solcott.activitytracker.activityfile

// Bindings for the parts of Garmin's JS FIT SDK (npm @garmin/fitsdk) that GarminJsFitDecoder uses.

internal external class Stream(arrayBuffer: JsAny) : JsAny

internal external class Decoder(stream: Stream) : JsAny {
    fun isFIT(): Boolean

    fun checkIntegrity(): Boolean

    fun read(): FitReadResult
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
}
