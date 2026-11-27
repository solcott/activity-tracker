package io.github.solcott.activitytracker.data.activity.trim

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * An unsaved activity offered for trimming (feed/activity-trim): what the trim screen works over, its [totals] before any trim, its [sport]
 * (the token the totals' rate reads by), the UTC offset its clock times read in (null: the device's), and the [span] chosen for it.
 */
class TrimDraft(
    val input: TrimInput,
    val totals: TrimTotals,
    val sport: String,
    val utcOffsetMinutes: Int?,
    val suggestion: TrimSuggestion?,
    val span: KeptSpan?,
)

/**
 * Hands unsaved activities between the screen that holds one (a recording's finish, an import's preview) and the trim screen, both ways
 * (feed/activity-trim): the holder [offer]s a draft under an id it chooses and follows its [span]; the trim screen reads the draft and
 * [apply]s the kept span. A session's, in memory only, since a draft is too. The presenter host passes no pop result, so this is how the
 * span comes back on every client.
 */
@SingleIn(SessionScope::class)
@Inject
class TrimDrafts {
    private val drafts = MutableStateFlow<Map<String, TrimDraft>>(emptyMap())

    /** Offers [input] under [id], keeping any span already chosen for it. */
    fun offer(id: String, input: TrimInput, totals: TrimTotals, sport: String, utcOffsetMinutes: Int? = null) {
        drafts.update { all -> all + (id to TrimDraft(input, totals, sport, utcOffsetMinutes, TrimSuggestion.of(input), all[id]?.span)) }
    }

    /** Offers [draft] under [id] when it can be trimmed ([TrimRules.canTrim]), else forgets any draft there; returns whether it did. */
    fun offer(id: String, draft: ActivityDraft): Boolean {
        if (!TrimRules.canTrim(draft) || draft.elapsed.inWholeSeconds < KeptSpan.MIN_SECONDS * 2) {
            drop(id)
            return false
        }
        val totals = TrimTotals(draft.distanceMeters, draft.movingSeconds, draft.elapsed.inWholeSeconds, draft.elevationGainMeters)
        offer(id, draft.trimInput(), totals, draft.type.sport, draft.utcOffsetMinutes)
        return true
    }

    /** [draft] cut to the span chosen for it under [id], or as it is while it's kept whole. */
    fun trimmed(id: String, draft: ActivityDraft): ActivityDraft = drafts.value[id]?.span?.let { draft.trimmed(it) } ?: draft

    /** The draft offered under [id], or null when none is. */
    fun draft(id: String): TrimDraft? = drafts.value[id]

    /** The span chosen for the draft under [id], null while it's kept whole. */
    fun span(id: String): Flow<KeptSpan?> = drafts.map { it[id]?.span }.distinctUntilChanged()

    /** Keeps [span] of the draft under [id], or all of it when null; nothing when no draft is offered under it. */
    fun apply(id: String, span: KeptSpan?) {
        drafts.update { all ->
            val draft = all[id] ?: return@update all
            val kept = span?.takeUnless { it.isWhole(draft.input) }
            all + (id to TrimDraft(draft.input, draft.totals, draft.sport, draft.utcOffsetMinutes, draft.suggestion, kept))
        }
    }

    /** Forgets the draft under [id], once it's saved or left. */
    fun drop(id: String) {
        drafts.update { it - id }
    }
}
