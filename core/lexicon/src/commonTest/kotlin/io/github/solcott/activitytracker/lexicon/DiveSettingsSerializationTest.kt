package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.DecoModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DiveSettingsSerializationTest {
    private val record =
        ActivityRecord(
            sport = Sport.DIVE,
            startedAt = Instant.parse("2026-09-30T14:00:00Z"),
            elapsedTime = 1800,
            createdAt = Instant.parse("2026-09-30T14:31:00Z"),
        )

    @Test
    fun diveSettingsRoundTripAndOlderDivesLackThem() {
        val dive =
            record.copy(
                dive =
                    ActivityDive(
                        decoModel = DecoModel.ZHL16C,
                        ppO2Warn = 1400,
                        ppO2Critical = 1600,
                        ppO2Deco = 1600,
                        lowSetpoint = 700,
                        highSetpoint = 1300,
                        safetyStopTime = 180,
                    )
            )
        val json = AtJson.encodeToString(ActivityRecord.serializer(), dive)
        assertTrue(""""decoModel":"zhl16c","ppO2Warn":1400""" in json, json)
        assertTrue(""""safetyStopTime":180""" in json, json)
        assertEquals(dive, AtJson.decodeFromString(ActivityRecord.serializer(), json))
        val older = AtJson.encodeToString(ActivityRecord.serializer(), record.copy(dive = ActivityDive(maxDepth = 3012)))
        assertFalse("decoModel" in older || "safetyStopTime" in older, older)
        val decoded = AtJson.decodeFromString(ActivityRecord.serializer(), older).dive!!
        assertNull(decoded.decoModel)
        assertNull(decoded.lowSetpoint)
    }
}
