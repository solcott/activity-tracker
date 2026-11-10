package io.github.solcott.activitytracker.app.names

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlinx.collections.immutable.persistentListOf

/** The name sheet (segments/segment-names), proposing a new name for a place or editing the viewer's own proposal. */
object ProposePlaceNameSamples {
    private fun state(name: String, editing: Boolean = false, unchanged: String = "Trail 214") =
        ProposePlaceNameState(textFieldStateOf(name), editing, unchanged, sending = false, error = null, eventSink = {})

    val propose: ProposePlaceNameState
        get() = state("Wiggly Woods")

    val edit: ProposePlaceNameState
        get() = state("Rooty Ridge Loop", editing = true, unchanged = "Rooty Ridge")

    val invalid: ProposePlaceNameState
        get() = state("trail 214")

    /** Proposing with Share to feed on, for Followers, with a reason (feed/posts). */
    val sharing: ProposePlaceNameState
        get() =
            state("Wiggly Woods")
                .copy(
                    sharing = true,
                    audience = Visibility.FOLLOWERS,
                    shareText = textFieldStateOf("Everyone at the trailhead already calls it this."),
                    audiences = persistentListOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS),
                )

    /** Sharing on a session whose grant predates posts in spaces: only Public, and why. */
    val sharingPublicOnly: ProposePlaceNameState
        get() = state("Wiggly Woods").copy(sharing = true, shareLimit = ShareLimit.SignInAgain)

    val all: Map<String, ProposePlaceNameState>
        get() =
            mapOf(
                SampleKind.LOADED to propose,
                "edit" to edit,
                SampleKind.INVALID to invalid,
                "sharing" to sharing,
                "sharingPublicOnly" to sharingPublicOnly,
            )
}
