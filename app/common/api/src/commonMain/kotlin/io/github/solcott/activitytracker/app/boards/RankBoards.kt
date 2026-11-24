package io.github.solcott.activitytracker.app.boards

import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic

/** What every rank board (a segment's leaderboard and Mayors, a dive site's Regulars) decides alike about its entries. */
object RankBoards {
    /**
     * Whether an entry by [athlete] is the viewer's own, marked as theirs wherever it shows: [viewer] is the athlete of the viewer's own
     * entry, the loaded page's when it has one, else the one pinned apart; null when they aren't on the board.
     */
    fun isOwn(viewer: ProfileViewBasic?, athlete: ProfileViewBasic): Boolean = viewer?.did == athlete.did

    /**
     * Whether the pinned own entry, at [ownRank], shows how far it is from the board's first entry, at [firstRank]: when it isn't first.
     */
    fun showsGap(ownRank: Int, firstRank: Int): Boolean = ownRank != 1 && firstRank == 1
}
