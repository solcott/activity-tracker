package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import kotlin.math.roundToInt

/**
 * A FIT session's exercise load, stamina and sweat loss, as Garmin devices record them. Each is null where the file doesn't hold it or
 * holds something out of range.
 */
data class SessionPhysiology(
    /** Garmin's exercise load (`training_load_peak`), rounded to a whole unit. */
    val exerciseLoad: Int? = null,
    /** Stamina potential at the start, in percent. */
    val staminaStart: Int? = null,
    /** Stamina potential at the end, in percent. */
    val staminaEnd: Int? = null,
    /** The lowest stamina during the activity, in percent. */
    val staminaMin: Int? = null,
    /** Estimated sweat loss, in milliliters. */
    val sweatLossMl: Int? = null,
) {
    /** These values with the ones only Garmin defines dropped: other makers may write something else in those fields. */
    fun documentedOnly(): SessionPhysiology? = SessionPhysiology(exerciseLoad = exerciseLoad).takeUnless { it.isEmpty }

    val isEmpty: Boolean
        get() = this == SessionPhysiology()

    companion object {
        /**
         * The session's values from [exerciseLoad], the SDK's scaled `training_load_peak`, and [field], which fetches a session field's raw
         * value by number (null when the session lacks it). Invalid markers and values out of range are dropped.
         */
        fun read(exerciseLoad: Double?, field: (Int) -> Long?): SessionPhysiology? {
            fun percent(number: Int) = field(number)?.takeIf { it != SessionFieldNumbers.INVALID_UINT8 }?.within(TrainingLoad.MAX_STAMINA)
            return SessionPhysiology(
                    exerciseLoad = exerciseLoad?.takeIf { it.isFinite() }?.roundToInt()?.takeIf { it in 0..TrainingLoad.MAX_EXERCISE_LOAD },
                    staminaStart = percent(SessionFieldNumbers.STAMINA_START),
                    staminaEnd = percent(SessionFieldNumbers.STAMINA_END),
                    staminaMin = percent(SessionFieldNumbers.STAMINA_MIN),
                    sweatLossMl =
                        field(SessionFieldNumbers.SWEAT_LOSS)
                            ?.takeIf { it != SessionFieldNumbers.INVALID_UINT16 }
                            ?.within(TrainingLoad.MAX_SWEAT_LOSS),
                )
                .takeUnless { it.isEmpty }
        }

        private fun Long.within(max: Int): Int? = takeIf { it in 0..max }?.toInt()
    }
}

/** Session fields Garmin writes that the public FIT profile (SDK 21.218) doesn't name, so decoders read them by number. */
object SessionFieldNumbers {
    /** `training_load_peak`, in the profile: exercise load, scaled by 65,536. */
    const val TRAINING_LOAD_PEAK = 168

    /** Estimated sweat loss, ml, `uint16`. */
    const val SWEAT_LOSS = 178

    /** Beginning stamina potential, %, `uint8`. */
    const val STAMINA_START = 205

    /** Ending stamina potential, %, `uint8`. */
    const val STAMINA_END = 206

    /** Minimum stamina, %, `uint8`. */
    const val STAMINA_MIN = 207

    /** `training_load_peak`'s scale. */
    const val TRAINING_LOAD_SCALE = 65_536.0

    const val INVALID_UINT8 = 0xFFL
    const val INVALID_UINT16 = 0xFFFFL
}
