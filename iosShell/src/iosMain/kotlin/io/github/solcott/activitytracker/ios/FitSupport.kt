package io.github.solcott.activitytracker.ios

import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.DecodedDive
import io.github.solcott.activitytracker.activityfile.DecodedDiveAlert
import io.github.solcott.activitytracker.activityfile.DecodedGasSwitch
import io.github.solcott.activitytracker.activityfile.DecodedTankUpdate
import io.github.solcott.activitytracker.activityfile.FitDiveMessages
import io.github.solcott.activitytracker.activityfile.FitDives
import io.github.solcott.activitytracker.activityfile.FitHeart
import io.github.solcott.activitytracker.activityfile.FitUnits

/**
 * The shared FIT helpers `SwiftFitDecoder` calls, so the Swift decoder interprets dive messages, heart rate readings and positions the same
 * way the Java and JS decoders do.
 *
 * They are repeated here because Swift export carries a type from `:core-activityfile` only where an exported signature names it:
 * [FitDives], [FitHeart] and [FitUnits] are objects no signature names, so Swift can't see them, while [FitDiveMessages] and the classes it
 * holds come through by being named below. Exporting the module whole instead doesn't build (see the spike findings in
 * `openspec/explorations/ios-native-ui.md`).
 */
object FitSupport {
    /** [FitDives.interpret]. */
    fun dive(messages: FitDiveMessages, sport: String?, subSport: String?): DecodedDive? = FitDives.interpret(messages, sport, subSport)

    /** [FitDives.gasSwitches]. */
    fun gasSwitches(messages: FitDiveMessages): List<DecodedGasSwitch> = FitDives.gasSwitches(messages)

    /** [FitDives.tankUpdates]. */
    fun tankUpdates(messages: FitDiveMessages): List<DecodedTankUpdate> = FitDives.tankUpdates(messages)

    /** [FitDives.diveAlerts]. */
    fun diveAlerts(messages: FitDiveMessages): List<DecodedDiveAlert> = FitDives.diveAlerts(messages)

    /** [FitHeart.isValidRr]. */
    fun isValidRr(seconds: Double): Boolean = FitHeart.isValidRr(seconds)

    /** [FitHeart.isValidReading]. */
    fun isValidHeartRateReading(eventTimestamp: Double, bpm: Int): Boolean = FitHeart.isValidReading(eventTimestamp, bpm)

    /** [FitUnits.semicirclesToDegrees]. */
    fun semicirclesToDegrees(semicircles: Int): Double = FitUnits.semicirclesToDegrees(semicircles)

    /** The failure `FitDecoder.decode` declares, for the Swift decoder to throw: Kotlin catches the same [ActivityFileException]. */
    fun unreadable(message: String): ActivityFileException = ActivityFileException(message)
}
