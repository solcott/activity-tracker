package io.github.solcott.activitytracker.app.divesite

import io.github.solcott.activitytracker.app.boards.BoardChoice
import io.github.solcott.activitytracker.app.boards.RankBoardTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import kotlinx.collections.immutable.persistentListOf

/** What a dive site's Regulars board offers and says (dive-sites/site-regulars, dev/shared-display-text). */
object DiveSiteRegularsTexts {
    /** All time, This year or Last year, a [RegularsPeriod] value [chosen]. */
    fun period(chosen: String): BoardChoice =
        BoardChoice.of(RegularsPeriod.all, chosen, narrows = true) {
            AppText.named(
                when (it) {
                    RegularsPeriod.THIS_YEAR -> "leaderboard_period_year"
                    RegularsPeriod.LAST_YEAR -> "mayor_period_last_year"
                    else -> "leaderboard_period_all_time"
                },
            )
        }

    /** "Reached" the day an entry reached its count. */
    fun reached(entry: DiveSiteRegularEntry): AppText = RankBoardTexts.reached(DateText.isoDate(entry.reachedOn))

    /** An entry's count: "31 dives". */
    fun visits(entry: DiveSiteRegularEntry): AppText = visits(entry.visits)

    /** A count of dives: "31 dives", grouped from a thousand (ui/number-format). */
    fun visits(count: Int): AppText = AppText.Plural("dive_site_visits", count, persistentListOf(AppText.written(formatWhole(count))))
}
