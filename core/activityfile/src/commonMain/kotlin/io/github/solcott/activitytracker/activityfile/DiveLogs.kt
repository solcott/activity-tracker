package io.github.solcott.activitytracker.activityfile

import kotlin.math.roundToInt

/** What the XML dive logs (Shearwater, UDDF, Subsurface) share once a dive's header and samples are read. */
internal object DiveLogs {
    /** FIT's spelling, which the mapper already knows. */
    fun subSport(dive: DecodedDive) =
        when {
            dive.closedCircuit -> "ccr_diving"
            dive.gases.size > 1 -> "multi_gas_diving"
            else -> "single_gas_diving"
        }

    fun summary(dive: DecodedDive, start: Long, elapsedSeconds: Double?, avgTemperatureCelsius: Double? = null) =
        DecodedSummary(
            sport = "diving",
            subSport = subSport(dive),
            startTimeMillis = start,
            totalElapsedSeconds = elapsedSeconds,
            totalTimerSeconds = null,
            totalDistanceMeters = null,
            totalAscentMeters = null,
            avgSpeedMps = null,
            maxSpeedMps = null,
            avgHeartRate = null,
            maxHeartRate = null,
            avgPowerWatts = null,
            avgCadence = null,
            // Null for the mapper to average the records' water temperatures.
            avgTemperatureCelsius = avgTemperatureCelsius,
        )

    /**
     * A device name the mapper keeps as written: a model with its maker ("Shearwater Petrel") has a space, which stops the mapper
     * lowercasing it as a lone FIT product name.
     */
    fun source(model: String?, manufacturer: String? = null, serial: String? = null, firmware: String? = null): DecodedSource? {
        val name = listOfNotNull(manufacturer?.trim()?.ifEmpty { null }, model?.trim()?.ifEmpty { null }).distinct().joinToString(" ")
        if (name.isEmpty()) return null
        val serialNumber = serial?.trim()?.ifEmpty { null }
        val softwareVersion = firmware?.trim()?.ifEmpty { null }
        return if (' ' in name) {
            DecodedSource(
                null,
                null,
                product = name,
                timeCreatedMillis = null,
                serialNumber = serialNumber,
                softwareVersion = softwareVersion,
            )
        } else {
            DecodedSource(null, manufacturer = name, product = null, timeCreatedMillis = null, serialNumber, softwareVersion)
        }
    }

    /** A gas from oxygen and helium fractions (0.32), or null without oxygen. */
    fun gasFromFractions(oxygen: Double?, helium: Double?): DiveGas? {
        val o2 = oxygen?.takeIf { it > 0 } ?: return null
        return DiveGas((o2 * PERCENT).roundToInt(), ((helium ?: 0.0) * PERCENT).roundToInt())
    }

    const val PERCENT = 100
    const val MILLIS_PER_SECOND = 1000.0
    const val SECONDS_PER_MINUTE = 60
    const val PASCALS_PER_BAR = 100_000.0
    const val KELVIN_OFFSET = 273.15
}
