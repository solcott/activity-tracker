package io.github.solcott.activitytracker.data.activity.trim

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.Sport

/** Which activities can be trimmed (feed/activity-trim). */
object TrimRules {
    /** Why an activity can't be trimmed. */
    enum class Refusal {
        NotOwner,
        Combined,
        CombinedMember,
        Multisport,
        Dive,
        Sets,
    }

    /**
     * Why [record] can't be trimmed, or null when it can: only its owner trims it, and never a combined activity, one of its members, a
     * multisport activity, a dive or a workout with sets. [streams] are its streams when known.
     */
    fun refusal(record: ActivityRecord, isOwner: Boolean, isCombinedMember: Boolean, streams: ActivityStreamData?): Refusal? =
        when {
            !isOwner -> Refusal.NotOwner
            record.combined != null -> Refusal.Combined
            isCombinedMember -> Refusal.CombinedMember
            record.sport == Sport.MULTISPORT || !streams?.legs.isNullOrEmpty() -> Refusal.Multisport
            record.dive != null -> Refusal.Dive
            !streams?.sets.isNullOrEmpty() -> Refusal.Sets
            else -> null
        }

    /** Whether [record] can be trimmed ([refusal]). */
    fun canTrim(record: ActivityRecord, isOwner: Boolean, isCombinedMember: Boolean, streams: ActivityStreamData?): Boolean =
        refusal(record, isOwner, isCombinedMember, streams) == null

    /** Whether [draft], not yet saved, can be trimmed: the same rule, for an activity that isn't combined yet. */
    fun canTrim(draft: ActivityDraft): Boolean =
        draft.type.sport != Sport.MULTISPORT &&
            draft.streams?.legs.isNullOrEmpty() &&
            draft.dive == null &&
            draft.streams?.sets.isNullOrEmpty()
}
