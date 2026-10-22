package io.github.solcott.activitytracker.data.segments

import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import kotlinx.datetime.LocalDate

/**
 * An athlete's leaderboard brackets (segments/leaderboard-brackets), computed on the device from their own details: the only thing about
 * their age, weight and gender the AppView ever learns. Each is null when the athlete hasn't given its detail.
 */
data class LeaderboardBrackets(val ageGroup: String? = null, val weightClass: String? = null, val gender: String? = null) {
    companion object {
        private const val GRAMS_PER_KG = 1000

        /** Lower bounds, in whole years, of each age group after the first, in order. */
        private val AGE_FROM = listOf(20, 25, 35, 45, 55, 65, 70, 75)

        /** Lower bounds, in whole kilograms, of each weight class after the first, in order. */
        private val WEIGHT_FROM = listOf(55, 65, 75, 85, 95, 105, 115)

        /** [details]' brackets on [today], the athlete's local date. */
        fun of(details: ProfileDetails, today: LocalDate): LeaderboardBrackets =
            LeaderboardBrackets(
                ageGroup = details.birthDate?.let { ageOn(it, today) }?.let { age -> bracket(age, AGE_FROM, LeaderboardAgeGroup.all) },
                weightClass =
                    details.weight
                        ?.takeIf { it > 0 }
                        ?.let { grams -> bracket((grams / GRAMS_PER_KG).toInt(), WEIGHT_FROM, LeaderboardWeightClass.all) },
                gender = details.gender?.takeIf { it in LeaderboardGender.all },
            )

        /** Whole years from [birthDate] (`YYYY-MM-DD`) to [today]; null for a date that doesn't parse or is after [today]. */
        private fun ageOn(birthDate: String, today: LocalDate): Int? = runCatching {
            LocalDate.parse(birthDate)
        }
            .getOrNull()
            ?.takeIf { it <= today }
            ?.let { born ->
                val hadBirthday = today.month > born.month || (today.month == born.month && today.day >= born.day)
                today.year - born.year - if (hadBirthday) 0 else 1
            }

        /** The bracket of [value] among [labels], whose first is everything below [from]'s first bound. */
        private fun bracket(value: Int, from: List<Int>, labels: List<String>): String = labels[from.count { value >= it }]
    }
}
