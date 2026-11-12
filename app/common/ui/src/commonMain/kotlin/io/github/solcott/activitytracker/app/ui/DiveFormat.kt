package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.detail.GasKind
import io.github.solcott.activitytracker.app.text.DiveText

/**
 * Display formatting for a dive's gases, surface interval, tank pressures and air consumption, following [LocalUnitSystem] like [Format].
 */
object DiveFormat {
    /** A tank pressure in whole bar, or psi. */
    @Composable fun pressure(bar: Double?): String = DiveText.pressure(bar, LocalUnitSystem.current).string()

    /** Air consumption by tank pressure: bar per minute to one decimal, or whole psi per minute. */
    @Composable fun pressureRate(barPerMinute: Double): String = DiveText.pressureRate(barPerMinute, LocalUnitSystem.current).string()

    /** Air consumption by volume: liters per minute to one decimal, or cubic feet per minute to two. */
    @Composable fun gasRate(litersPerMinute: Double): String = DiveText.gasRate(litersPerMinute, LocalUnitSystem.current).string()

    /** "Air", "EAN32", "Oxygen" or "Tx 18/45", as [GasKind] names it. */
    @Composable fun gasName(oxygenPercent: Int, heliumPercent: Int): String = DiveText.gasName(oxygenPercent, heliumPercent).string()

    /** "5 h 55 min" within a day, "11 d 22 h" beyond. */
    @Composable fun surfaceInterval(minutes: Int): String = DiveText.surfaceInterval(minutes).string()
}
