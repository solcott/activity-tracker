package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * How far the viewer's comment actions this session have moved each subject's comment count, in the order they were made, whichever screen
 * made them (feed/comments). A screen holding a subject's view keeps a [mark] taken when that view loaded, and shows its count moved by the
 * moves made [since] then: a later read of the subject already counts them ([FeedRepository.activity], [FeedRepository.post]), so the mark
 * starts again with each view loaded.
 */
class CommentCountMoves {
    /** One move of [subject]'s count by [by]. */
    data class Move(val subject: AtUri, val by: Int)

    /** Every move this session, oldest first. */
    val moves: StateFlow<List<Move>>
        field = MutableStateFlow(emptyList())

    /** Moves [subject]'s count by [by]; a move of 0 is dropped. */
    fun move(subject: AtUri, by: Int) {
        if (by != 0) moves.update { it + Move(subject, by) }
    }

    /** Where the moves stand now: [since] this mark counts only the moves made after it. */
    fun mark(): Int = moves.value.size

    /** How far [subject]'s count has moved since [mark], read from [moves] as given (the list a screen last collected). */
    fun since(subject: AtUri, mark: Int, moves: List<Move> = this.moves.value): Int =
        moves.drop(mark).sumOf { if (it.subject == subject) it.by else 0 }
}
