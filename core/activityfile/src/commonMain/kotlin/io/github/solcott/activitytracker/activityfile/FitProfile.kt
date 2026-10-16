package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.ENUM
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.SINT32
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.SINT8
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.UINT16
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.UINT32
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.UINT8

/** A field of a FIT message, as Garmin's profile numbers and types it; call it with a raw (already scaled) value. */
internal class FitFieldDef(val num: Int, val type: FitBaseType) {
    operator fun invoke(value: Long?) = FitField(num, type, value)
}

/**
 * The parts of Garmin's FIT profile (SDK 21.218) that [FitWriter] writes: message numbers, field numbers and types, and enum values. Scales
 * are noted where the profile has one; [FitWriter] applies them. `FitWriterTest` decodes the result with Garmin's Java SDK.
 */
internal object FitProfile {
    val TIMESTAMP = FitFieldDef(253, UINT32)
    val MESSAGE_INDEX = FitFieldDef(254, UINT16)

    object FileId {
        const val NUM = 0
        val TYPE = FitFieldDef(0, ENUM)
        val MANUFACTURER = FitFieldDef(1, UINT16)
        val PRODUCT = FitFieldDef(2, UINT16)
        val TIME_CREATED = FitFieldDef(4, UINT32)
        const val TYPE_ACTIVITY = 4L
        const val MANUFACTURER_DEVELOPMENT = 255L
    }

    object FileCreator {
        const val NUM = 49
        val SOFTWARE_VERSION = FitFieldDef(0, UINT16)
    }

    object EventMessage {
        const val NUM = 21
        val EVENT = FitFieldDef(0, ENUM)
        val EVENT_TYPE = FitFieldDef(1, ENUM)
        /** Gear changes pack rear gear number, rear teeth, front gear number and front teeth into its four bytes, low to high. */
        val DATA = FitFieldDef(3, UINT32)
        val EVENT_GROUP = FitFieldDef(4, UINT8)
        const val TIMER = 0L
        const val SESSION = 8L
        const val LAP = 9L
        const val ACTIVITY = 26L
        const val FRONT_GEAR_CHANGE = 42L
        const val REAR_GEAR_CHANGE = 43L
        const val TYPE_START = 0L
        const val TYPE_STOP = 1L
        const val TYPE_MARKER = 3L
        const val TYPE_STOP_ALL = 4L
        const val FRONT_GEAR_SHIFT = 16
    }

    object Record {
        const val NUM = 20
        /** Semicircles. */
        val POSITION_LAT = FitFieldDef(0, SINT32)
        val POSITION_LONG = FitFieldDef(1, SINT32)
        val HEART_RATE = FitFieldDef(3, UINT8)
        val CADENCE = FitFieldDef(4, UINT8)
        /** Meters × 100. */
        val DISTANCE = FitFieldDef(5, UINT32)
        val POWER = FitFieldDef(7, UINT16)
        /** Whole °C. */
        val TEMPERATURE = FitFieldDef(13, SINT8)
        /** Meters per second × 1000. */
        val ENHANCED_SPEED = FitFieldDef(73, UINT32)
        /** (Meters + 500) × 5. */
        val ENHANCED_ALTITUDE = FitFieldDef(78, UINT32)
        /** Meters × 1000. */
        val DEPTH = FitFieldDef(92, UINT32)
        const val ALTITUDE_OFFSET = 500.0
        const val ALTITUDE_SCALE = 5.0
    }

    /** The fields a lap and a session share, at each one's own numbers. Times are milliseconds, distance centimeters, speeds mm/s. */
    sealed interface Summary {
        val num: Int
        val event: Long
        val sport: FitFieldDef
        val subSport: FitFieldDef
        val avgHeartRate: FitFieldDef
        val maxHeartRate: FitFieldDef
        val totalAscent: FitFieldDef
        val avgTemperature: FitFieldDef
        val enhancedAvgSpeed: FitFieldDef
        val enhancedMaxSpeed: FitFieldDef
        /** What only this message has: a lap's trigger, a session's lap count and trigger. */
        val trailer: List<FitField>
    }

    val EVENT = FitFieldDef(0, ENUM)
    val EVENT_TYPE = FitFieldDef(1, ENUM)
    val START_TIME = FitFieldDef(2, UINT32)
    val TOTAL_ELAPSED_TIME = FitFieldDef(7, UINT32)
    val TOTAL_TIMER_TIME = FitFieldDef(8, UINT32)
    val TOTAL_DISTANCE = FitFieldDef(9, UINT32)

    object Lap : Summary {
        override val num = 19
        override val event = EventMessage.LAP
        override val sport = FitFieldDef(25, ENUM)
        override val subSport = FitFieldDef(39, ENUM)
        override val avgHeartRate = FitFieldDef(15, UINT8)
        override val maxHeartRate = FitFieldDef(16, UINT8)
        override val totalAscent = FitFieldDef(21, UINT16)
        override val avgTemperature = FitFieldDef(50, SINT8)
        override val enhancedAvgSpeed = FitFieldDef(110, UINT32)
        override val enhancedMaxSpeed = FitFieldDef(111, UINT32)
        // lap_trigger session_end
        override val trailer = listOf(FitField(24, ENUM, 7))
    }

    object Session : Summary {
        override val num = 18
        override val event = EventMessage.SESSION
        override val sport = FitFieldDef(5, ENUM)
        override val subSport = FitFieldDef(6, ENUM)
        override val avgHeartRate = FitFieldDef(16, UINT8)
        override val maxHeartRate = FitFieldDef(17, UINT8)
        override val totalAscent = FitFieldDef(22, UINT16)
        override val avgTemperature = FitFieldDef(57, SINT8)
        override val enhancedAvgSpeed = FitFieldDef(124, UINT32)
        override val enhancedMaxSpeed = FitFieldDef(125, UINT32)
        // first_lap_index 0, num_laps 1, trigger activity_end
        override val trailer = listOf(FitField(25, UINT16, 0), FitField(26, UINT16, 1), FitField(28, ENUM, 0))
    }

    object Activity {
        const val NUM = 34
        /** Milliseconds. */
        val TOTAL_TIMER_TIME = FitFieldDef(0, UINT32)
        val NUM_SESSIONS = FitFieldDef(1, UINT16)
        val TYPE = FitFieldDef(2, ENUM)
        val EVENT = FitFieldDef(3, ENUM)
        val EVENT_TYPE = FitFieldDef(4, ENUM)
        val LOCAL_TIMESTAMP = FitFieldDef(5, UINT32)
        const val TYPE_MANUAL = 0L
    }

    object DiveSettings {
        const val NUM = 258
        val GF_LOW = FitFieldDef(2, UINT8)
        val GF_HIGH = FitFieldDef(3, UINT8)
    }

    object DiveGas {
        const val NUM = 259
        val HELIUM_CONTENT = FitFieldDef(0, UINT8)
        val OXYGEN_CONTENT = FitFieldDef(1, UINT8)
        val STATUS = FitFieldDef(2, ENUM)
        val MODE = FitFieldDef(3, ENUM)
        const val STATUS_ENABLED = 1L
        const val MODE_OPEN_CIRCUIT = 0L
        const val MODE_CLOSED_CIRCUIT_DILUENT = 1L
    }

    object TankSummary {
        const val NUM = 323
        /** Bar × 100. */
        val START_PRESSURE = FitFieldDef(1, UINT16)
        val END_PRESSURE = FitFieldDef(2, UINT16)
        /** Liters × 100. */
        val VOLUME_USED = FitFieldDef(3, UINT32)
    }

    object DiveSummary {
        const val NUM = 268
        val REFERENCE_MESG = FitFieldDef(0, UINT16)
        val REFERENCE_INDEX = FitFieldDef(1, UINT16)
        /** Meters × 1000. */
        val AVG_DEPTH = FitFieldDef(2, UINT32)
        val MAX_DEPTH = FitFieldDef(3, UINT32)
        /** Seconds. */
        val SURFACE_INTERVAL = FitFieldDef(4, UINT32)
        val START_CNS = FitFieldDef(5, UINT8)
        val END_CNS = FitFieldDef(6, UINT8)
        val O2_TOXICITY = FitFieldDef(9, UINT16)
        val DIVE_NUMBER = FitFieldDef(10, UINT32)
        /** Milliseconds. */
        val BOTTOM_TIME = FitFieldDef(11, UINT32)
    }
}
