package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState

/**
 * The follow record the athlete last wrote (or null after an unfollow) per followed DID, until the AppView shows it. Without this a profile
 * reloaded right after following still says "not following", and following again writes a second record.
 */
internal class PendingFollows {
    private val pending = mutableMapOf<Did, Pending>()

    private class Pending(val uri: AtUri?)

    /** Records that the athlete now follows [subject] with the record at [uri], or no longer does when it's null. */
    fun wrote(subject: Did, uri: AtUri?) {
        pending[subject] = Pending(uri)
    }

    /** [view] with the athlete's last follow or unfollow of its athlete, and that change to the follower count, until it's indexed. */
    fun applyTo(view: ProfileView): ProfileView {
        val wrote = pending[view.did]?.uri
        val shown = view.viewer?.following
        if (wrote == shown) pending.remove(view.did)
        if (view.did !in pending) return view
        val followers = view.followersCount ?: 0
        val adjusted = if (wrote == null) (followers - 1).coerceAtLeast(0) else followers + if (shown == null) 1 else 0
        return view.copy(viewer = (view.viewer ?: ProfileViewerState()).copy(following = wrote), followersCount = adjusted)
    }
}
