package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A FIT session's exercise load, stamina and sweat loss, as [SessionPhysiology.read] and [DecodedActivityMapper] keep them. */
class SessionPhysiologyTest {
    private val start = Instant.parse("2026-10-19T22:53:00Z")

    private fun summary(sport: String = "cycling") =
        DecodedSummary(sport, null, start.toEpochMilliseconds(), 1800.0, 1700.0, 5000.0, 42.0, null, 4.2, 150, 175, null, null, null)

    private fun record(sec: Long) =
        DecodedSample((start + sec.seconds).toEpochMilliseconds(), 47.6 + sec * 1e-4, -122.3, null, null, null, null, null, null, null)

    private fun fileId() = DecodedSource("activity", "garmin", "edge840", start.toEpochMilliseconds())

    @Test
    fun carriesGarminPhysiology() {
        // The reference ride's session: training_load_peak 12240282 / 65536, and fields 178, 205–207 raw.
        val raw = mapOf(178 to 764L, 205 to 100L, 206 to 70L, 207 to 48L)
        val physiology = SessionPhysiology.read(12_240_282 / SessionFieldNumbers.TRAINING_LOAD_SCALE) { raw[it] }
        val session = summary().copy(physiology = physiology)
        val activity = DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(session), emptyList(), listOf(record(0), record(10))))

        assertEquals(
            SessionPhysiology(exerciseLoad = 187, staminaStart = 100, staminaEnd = 70, staminaMin = 48, sweatLossMl = 764),
            activity.physiology,
        )
    }

    @Test
    fun withoutPhysiologyNoneIsStored() {
        assertNull(SessionPhysiology.read(null) { null })
        val activity = DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(summary()), emptyList(), listOf(record(0), record(10))))
        assertNull(activity.physiology)
    }

    @Test
    fun physiologyOutOfRangeOrInvalidIsDropped() {
        val raw = mapOf(178 to 0xFFFFL, 205 to 150L, 206 to 0xFFL, 207 to 48L)
        assertEquals(SessionPhysiology(staminaMin = 48), SessionPhysiology.read(-3.0) { raw[it] })
        assertNull(
            SessionPhysiology.read(Double.NaN) { if (it == SessionFieldNumbers.SWEAT_LOSS) 0xFFFFL else 0xFFL },
            "all invalid is none",
        )
    }

    @Test
    fun onlyGarminFilesKeepGarminsOwnFields() {
        val physiology = SessionPhysiology(exerciseLoad = 187, staminaStart = 100, sweatLossMl = 764)
        val other = DecodedSource("activity", "wahoo_fitness", "elemnt", start.toEpochMilliseconds())
        val activity =
            DecodedActivityMapper.map(
                DecodedActivity(other, listOf(summary().copy(physiology = physiology)), emptyList(), listOf(record(0), record(10)))
            )
        assertEquals(SessionPhysiology(exerciseLoad = 187), activity.physiology)
    }

    @Test
    fun legsAddTheirLoadAndCarryStaminaThrough() {
        val legs =
            listOf(
                summary("cycling").copy(physiology = SessionPhysiology(10, 100, 90, 85, 300)),
                summary("running").copy(physiology = SessionPhysiology(20, 90, 60, 55, 400)),
            )
        val activity = DecodedActivityMapper.map(DecodedActivity(fileId(), legs, emptyList(), listOf(record(0), record(10))))
        assertEquals(SessionPhysiology(30, 100, 60, 55, 700), activity.physiology)
    }
}
