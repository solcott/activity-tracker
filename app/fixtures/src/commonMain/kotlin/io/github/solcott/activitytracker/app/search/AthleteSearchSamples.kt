package io.github.solcott.activitytracker.app.search

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.app.profile.FollowListSamples

object AthleteSearchSamples {
    /** Results for a typed query; each state has its own text field. */
    val all: Map<String, AthleteSearchState>
        get() = pagedSamples(FollowListSamples.accounts, Unit) { AthleteSearchState(TextFieldState("bo"), it, eventSink = {}) }

    /** Nothing typed yet. */
    val blank: AthleteSearchState
        get() = all.getValue(SampleKind.END).copy(query = TextFieldState(), results = PagedStates.end(emptyList(), Unit))
}
