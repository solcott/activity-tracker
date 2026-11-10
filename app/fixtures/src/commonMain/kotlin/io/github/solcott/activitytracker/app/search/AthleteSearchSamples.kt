package io.github.solcott.activitytracker.app.search

import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.app.profile.FollowListSamples
import io.github.solcott.activitytracker.textfield.textFieldStateOf

object AthleteSearchSamples {
    /** Results for a typed query; each state has its own text field. */
    val all: Map<String, AthleteSearchState>
        get() = pagedSamples(FollowListSamples.accounts, Unit) { AthleteSearchState(textFieldStateOf("bo"), it, eventSink = {}) }

    /** Nothing typed yet. */
    val blank: AthleteSearchState
        get() = all.getValue(SampleKind.END).copy(query = textFieldStateOf(), results = PagedStates.end(emptyList(), Unit))
}
