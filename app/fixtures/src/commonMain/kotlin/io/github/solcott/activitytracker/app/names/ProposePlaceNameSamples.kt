package io.github.solcott.activitytracker.app.names

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.SampleKind

/** The name sheet (segments/segment-names), proposing a new name for a place or editing the viewer's own proposal. */
object ProposePlaceNameSamples {
    private fun state(name: String, editing: Boolean = false, unchanged: String = "Trail 214") =
        ProposePlaceNameState(TextFieldState(name), editing, unchanged, sending = false, error = null, eventSink = {})

    val propose: ProposePlaceNameState
        get() = state("Wiggly Woods")

    val edit: ProposePlaceNameState
        get() = state("Rooty Ridge Loop", editing = true, unchanged = "Rooty Ridge")

    val invalid: ProposePlaceNameState
        get() = state("trail 214")

    val all: Map<String, ProposePlaceNameState>
        get() = mapOf(SampleKind.LOADED to propose, "edit" to edit, SampleKind.INVALID to invalid)
}
