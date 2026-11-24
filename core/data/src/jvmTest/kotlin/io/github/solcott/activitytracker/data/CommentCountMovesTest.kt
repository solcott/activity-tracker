package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.feed.CommentCountMoves
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals

/** [CommentCountMoves]: a screen's count moves only by the moves made on its subject since its view loaded. */
class CommentCountMovesTest {
    private val alice = Did("did:plc:alice")
    private val run = AtUri.of(alice, Nsid.FEED_ACTIVITY, "3krun")
    private val post = AtUri.of(alice, Nsid.FEED_POST, "3kpost")

    @Test
    fun movesSinceTheMarkOnTheSubjectOnly() {
        val moves = CommentCountMoves()
        moves.move(run, 1)
        val mark = moves.mark()
        moves.move(run, 1)
        moves.move(post, 1)
        moves.move(run, -1)
        moves.move(run, -3)

        assertEquals(-3, moves.since(run, mark))
        assertEquals(1, moves.since(post, mark))
        assertEquals(-2, moves.since(run, 0))
    }

    @Test
    fun aNewMarkStartsAgain() {
        val moves = CommentCountMoves()
        moves.move(run, 1)
        moves.move(run, 1)

        assertEquals(0, moves.since(run, moves.mark()))
    }

    @Test
    fun noMoveIsDropped() {
        val moves = CommentCountMoves()
        moves.move(run, 0)

        assertEquals(0, moves.mark())
    }
}
