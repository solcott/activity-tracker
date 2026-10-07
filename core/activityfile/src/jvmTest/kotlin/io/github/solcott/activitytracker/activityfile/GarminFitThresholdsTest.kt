package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** The athlete's thresholds as the recording device had them set, decoded by the Java SDK. */
class GarminFitThresholdsTest {
    private val start = Instant.parse("2026-10-02T07:00:00Z")

    private fun read(bytes: ByteArray) = DecodedActivityMapper.map(GarminJavaFitDecoder().decode(bytes))

    @Test
    fun readsZonesTargetAndUserProfile() {
        val run = read(FitFixtures.run(start, FitFixtures.athleteSettings(maxHr = 188, thresholdHr = 170, ftp = 240, restingHr = 48)))
        assertEquals(DeviceThresholds(188, 170, 240, 48), run.deviceThresholds)
    }

    @Test
    fun unsetValuesAreDroppedAndFtpFallsBackToTheSession() {
        // Devices write 0 for a threshold the athlete never set.
        val ride = read(FitFixtures.mtbRide(start))
        assertEquals(DeviceThresholds(functionalThresholdPower = 250), ride.deviceThresholds)
        val unset = read(FitFixtures.run(start, FitFixtures.athleteSettings(maxHr = 0, thresholdHr = 0, ftp = 0, restingHr = 0)))
        assertNull(unset.deviceThresholds)
    }

    @Test
    fun filesWithoutSettingsHaveNone() {
        assertNull(read(FitFixtures.run(start)).deviceThresholds)
    }
}
