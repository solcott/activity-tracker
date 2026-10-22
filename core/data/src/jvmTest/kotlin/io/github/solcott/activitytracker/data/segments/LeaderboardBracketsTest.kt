package io.github.solcott.activitytracker.data.segments

import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

/**
 * Brackets computed from an athlete's own details (segments/leaderboard-brackets): Strava's boundaries, and nothing for a missing detail.
 */
class LeaderboardBracketsTest {
    private val today = LocalDate(2025, 6, 10)

    private fun age(birthDate: String, on: LocalDate = today) = LeaderboardBrackets.of(ProfileDetails(birthDate = birthDate), on).ageGroup

    private fun weight(grams: Long) = LeaderboardBrackets.of(ProfileDetails(weight = grams), today).weightClass

    @Test
    fun ageGroupsByWholeYearsOnTheDay() {
        assertEquals("35-44", age("1990-06-10"), "35 on their birthday")
        assertEquals("25-34", age("1990-06-11"), "34 the day before")
        assertEquals("0-19", age("2010-01-01"))
        assertEquals("20-24", age("2005-06-10"))
        assertEquals("65-69", age("1960-01-01"))
        assertEquals("70-74", age("1955-01-01"))
        assertEquals("75+", age("1930-01-01"))
        assertEquals("45-54", age("1980-06-10", on = LocalDate(2025, 6, 10)), "turning 45")
    }

    @Test
    fun aLeapDayBirthdayCountsFromMarchFirst() {
        assertEquals("35-44", age("1988-02-29", on = LocalDate(2023, 3, 1)))
        assertEquals("25-34", age("1988-02-29", on = LocalDate(2023, 2, 28)))
    }

    @Test
    fun weightClassesByWholeKilogramsRoundedDown() {
        assertEquals("0-54", weight(54_999))
        assertEquals("55-64", weight(55_000))
        assertEquals("55-64", weight(64_900), "64.9 kg")
        assertEquals("65-74", weight(70_000))
        assertEquals("75-84", weight(76_000))
        assertEquals("105-114", weight(114_999))
        assertEquals("115+", weight(150_000))
    }

    @Test
    fun genderOnlyFromTheKnownValues() {
        assertEquals("nonBinary", LeaderboardBrackets.of(ProfileDetails(gender = "nonBinary"), today).gender)
        assertNull(LeaderboardBrackets.of(ProfileDetails(gender = "a description of my own"), today).gender)
    }

    @Test
    fun aMissingOrUnreadableDetailHasNoBracket() {
        assertEquals(LeaderboardBrackets(weightClass = "65-74"), LeaderboardBrackets.of(ProfileDetails(weight = 70_000), today))
        assertNull(age("not a date"))
        assertNull(age("2030-01-01"), "born after today")
        assertEquals(LeaderboardBrackets(), LeaderboardBrackets.of(ProfileDetails(), today))
    }
}
