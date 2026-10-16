package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.ENUM
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.FLOAT32
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.SINT32
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.SINT8
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.UINT16
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.UINT32
import io.github.solcott.activitytracker.activityfile.FitBaseType.Companion.UINT32Z
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
        /** Its data is the `dive_gas` message index of the gas switched to. */
        const val DIVE_GAS_SWITCHED = 57L
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
        /** Percent in the low 7 bits; [BALANCE_RIGHT] says it's the right leg's. */
        val LEFT_RIGHT_BALANCE = FitFieldDef(30, UINT8)
        /** Millimeters × 10. */
        val VERTICAL_OSCILLATION = FitFieldDef(39, UINT16)
        /** Milliseconds × 10. */
        val STANCE_TIME = FitFieldDef(41, UINT16)
        /** Percent × 100. */
        val VERTICAL_RATIO = FitFieldDef(83, UINT16)
        val STANCE_TIME_BALANCE = FitFieldDef(84, UINT16)
        /** Millimeters × 10. */
        val STEP_LENGTH = FitFieldDef(85, UINT16)
        /** Meters × 1000. */
        val NEXT_STOP_DEPTH = FitFieldDef(93, UINT32)
        /** Seconds. */
        val NEXT_STOP_TIME = FitFieldDef(94, UINT32)
        val TIME_TO_SURFACE = FitFieldDef(95, UINT32)
        val NDL_TIME = FitFieldDef(96, UINT32)
        /** Percent. */
        val CNS_LOAD = FitFieldDef(97, UINT8)
        val N2_LOAD = FitFieldDef(98, UINT16)
        /** Breaths per minute × 100. */
        val ENHANCED_RESPIRATION_RATE = FitFieldDef(108, UINT16)
        /** Bar × 100. */
        val PO2 = FitFieldDef(129, UINT8)
        const val BALANCE_RIGHT = 0x80L
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
        val totalCalories: FitFieldDef
        val avgCadence: FitFieldDef
        val maxCadence: FitFieldDef
        val avgPower: FitFieldDef
        val maxPower: FitFieldDef
        val normalizedPower: FitFieldDef
        /** Joules. */
        val totalWork: FitFieldDef
        /** Meters. */
        val totalDescent: FitFieldDef

        /** What only this message has: a lap's trigger, a session's lap count ([laps]) and trigger. */
        fun trailer(laps: Int): List<FitField>
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
        override val totalCalories = FitFieldDef(11, UINT16)
        override val avgCadence = FitFieldDef(17, UINT8)
        override val maxCadence = FitFieldDef(18, UINT8)
        override val avgPower = FitFieldDef(19, UINT16)
        override val maxPower = FitFieldDef(20, UINT16)
        override val normalizedPower = FitFieldDef(33, UINT16)
        override val totalWork = FitFieldDef(41, UINT32)
        override val totalDescent = FitFieldDef(22, UINT16)

        // lap_trigger session_end
        private val TRIGGER_SESSION_END = FitField(24, ENUM, 7)

        override fun trailer(laps: Int) = listOf(TRIGGER_SESSION_END)

        /** A stored lap's trigger: the athlete's (manual), as the app doesn't keep what split it. */
        val MANUAL_TRIGGER = FitField(24, ENUM, 0)
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
        override val totalCalories = FitFieldDef(11, UINT16)
        override val avgCadence = FitFieldDef(18, UINT8)
        override val maxCadence = FitFieldDef(19, UINT8)
        override val avgPower = FitFieldDef(20, UINT16)
        override val maxPower = FitFieldDef(21, UINT16)
        override val normalizedPower = FitFieldDef(34, UINT16)
        override val totalWork = FitFieldDef(48, UINT32)
        override val totalDescent = FitFieldDef(23, UINT16)

        // first_lap_index 0, num_laps, trigger activity_end
        private val FIRST_LAP_INDEX = FitField(25, UINT16, 0)
        private val NUM_LAPS = FitFieldDef(26, UINT16)
        private val TRIGGER_ACTIVITY_END = FitField(28, ENUM, 0)

        override fun trailer(laps: Int) = listOf(FIRST_LAP_INDEX, NUM_LAPS(laps.toLong()), TRIGGER_ACTIVITY_END)

        /** Training effect × 10. */
        val TOTAL_TRAINING_EFFECT = FitFieldDef(24, UINT8)
        val TOTAL_ANAEROBIC_TRAINING_EFFECT = FitFieldDef(137, UINT8)
        /** Tenths. */
        val TRAINING_STRESS_SCORE = FitFieldDef(35, UINT16)
        /** Thousandths. */
        val INTENSITY_FACTOR = FitFieldDef(36, UINT16)
        val THRESHOLD_POWER = FitFieldDef(45, UINT16)
        val FRONT_GEAR_SHIFT_COUNT = FitFieldDef(107, UINT16)
        val REAR_GEAR_SHIFT_COUNT = FitFieldDef(108, UINT16)
        val JUMP_COUNT = FitFieldDef(183, UINT16)
        /** Kilograms of grit. */
        val TOTAL_GRIT = FitFieldDef(181, FLOAT32)
        val AVG_FLOW = FitFieldDef(187, FLOAT32)
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
        val WATER_TYPE = FitFieldDef(4, ENUM)
        /** Kilograms per cubic meter. */
        val WATER_DENSITY = FitFieldDef(5, FLOAT32)
        const val WATER_FRESH = 0L
        const val WATER_SALT = 1L
        const val WATER_EN13319 = 2L
        const val WATER_CUSTOM = 3L
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

    object TankUpdate {
        const val NUM = 319
        val SENSOR = FitFieldDef(0, UINT32Z)
        /** Bar × 100. */
        val PRESSURE = FitFieldDef(1, UINT16)
    }

    object TankSummary {
        const val NUM = 323
        val SENSOR = FitFieldDef(0, UINT32Z)
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
        /** Percent. */
        val START_N2 = FitFieldDef(7, UINT16)
        val END_N2 = FitFieldDef(8, UINT16)
        /** Milliseconds. */
        val DESCENT_TIME = FitFieldDef(15, UINT32)
        /** Millimeters per second. */
        val AVG_ASCENT_RATE = FitFieldDef(17, SINT32)
        val AVG_DESCENT_RATE = FitFieldDef(22, UINT32)
        val MAX_ASCENT_RATE = FitFieldDef(23, UINT32)
        val MAX_DESCENT_RATE = FitFieldDef(24, UINT32)
    }
}
