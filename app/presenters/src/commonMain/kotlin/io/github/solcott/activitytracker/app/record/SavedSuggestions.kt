package io.github.solcott.activitytracker.app.record

import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Where an import goes after saving one file: a save that combined needs no suggestion, so it leaves (or opens the combined activity, for a
 * recording added from its page); anything else opens the saved activity, asking whether to combine it.
 */
internal fun Navigator.openSaved(saved: SavedSingle, adding: AddingRecording) {
    pop()
    if (saved.combined) adding.opened(saved.uri)?.let(::goTo) else goTo(ActivityDetailScreen(saved.uri.value, suggestOnOpen = true))
}

/**
 * After a logbook's [published] dives (those saved on their own) are saved: leaves when none likely belongs with another of the athlete's
 * activities, else hands the ones that do to [onSuggested] to list. A failed lookup suggests nothing.
 */
internal suspend fun Navigator.afterDives(
    published: List<Pair<AtUri, ActivityDraft>>,
    suggestions: CombineSuggestions,
    onSuggested: (ImmutableList<SavedSuggestion>) -> Unit,
) {
    val found =
        published
            .mapNotNull { (uri, draft) ->
                runSuspendCatching { suggestions.forSaved(uri, draft) }
                    .getOrNull()
                    ?.let { SavedSuggestion(uri, draft.title, draft.startedAt.epochSeconds, it.cue, it.shown) }
            }
            .toImmutableList()
    if (found.isEmpty()) pop() else onSuggested(found)
}

/** Back, or the athlete opens a just-saved dive's page to combine it, or leaves the list of them. */
internal fun Navigator.leave(event: ImportActivityEvent) {
    when (event) {
        is ImportActivityEvent.Saved.Open -> goTo(ActivityDetailScreen(event.uri.value, suggestOnOpen = true))
        else -> pop()
    }
}
