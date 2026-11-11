package io.github.solcott.activitytracker.app.record

import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.uploads.UploadsScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.data.importsave.SavedActivity
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
 * Which of a batch's [saved] activities, with their records as queued, likely belong with another of the athlete's activities, to list. A
 * failed lookup suggests nothing.
 */
internal suspend fun savedSuggestionsFor(
    saved: List<SavedActivity>,
    suggestions: CombineSuggestions,
): ImmutableList<SavedSuggestion> =
    saved
        .mapNotNull { (uri, record) ->
            runSuspendCatching { suggestions.forSaved(uri, record) }
                .getOrNull()
                ?.let { SavedSuggestion(uri, record.title, record.startedAt.epochSeconds, it.cue, it.shown) }
        }
        .toImmutableList()

/** Back, or the athlete opens a just-saved activity's page to combine it or the upload queue, or leaves the batch's summary. */
internal fun Navigator.leave(event: ImportActivityEvent) {
    when (event) {
        is ImportActivityEvent.Saved.Open -> goTo(ActivityDetailScreen(event.uri.value, suggestOnOpen = true))
        ImportActivityEvent.Saved.ViewUploads -> {
            pop()
            goTo(UploadsScreen)
        }
        else -> pop()
    }
}
