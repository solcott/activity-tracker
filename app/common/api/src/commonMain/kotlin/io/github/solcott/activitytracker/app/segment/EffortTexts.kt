package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.NotRanked

/**
 * What an effort's row says, on the segment page and the activity page alike (segments/segment-efforts, dev/shared-display-text): its date,
 * time, PR, rank and why leaderboards don't rank it.
 */
object EffortTexts {
    /** The effort's `YYYY-MM-DD` date written out, the label a segment page gives it ([DateText.isoDate]). */
    fun date(effort: EffortView): AppText = DateText.isoDate(effort.startedOn)

    /** How long it took, `m:ss` or `h:mm:ss`. */
    fun time(effort: EffortView): String = StatText.duration(effort.elapsed)

    /** Its place in the top 10 as an ordinal ("3rd"); null outside it. */
    fun rank(effort: EffortView): AppText? =
        effort.rank?.takeIf { it in 1..TOP_RANKS }?.let { AppText.named("segment_rank_ordinals_${it - 1}") }

    /** "PR", on the athlete's fastest effort the viewer may see; null on any other. */
    fun pr(effort: EffortView): AppText? = if (effort.best == true) AppText.named("segment_pr") else null

    /** Why leaderboards don't rank it ([NotRanked]); null when they do, or for a reason this app doesn't know. */
    fun notRanked(effort: EffortView): AppText? =
        when (effort.notRanked) {
            NotRanked.HIDDEN -> AppText.named("segment_not_ranked_hidden")
            NotRanked.EXCLUDED -> AppText.named("segment_not_ranked_excluded")
            NotRanked.IMPLAUSIBLE -> AppText.named("segment_not_ranked_implausible")
            else -> null
        }

    /** "E-bike" for an e-bike ride's effort; null otherwise. */
    fun eBike(effort: EffortView): AppText? = if (effort.eBike == true) AppText.named("segment_ebike") else null

    /** Its segment's status when it isn't active, for a row that shows it ([PlaceTexts.status]). */
    fun status(effort: EffortView): AppText? = PlaceTexts.status(effort.segment.status)

    private const val TOP_RANKS = 10
}
