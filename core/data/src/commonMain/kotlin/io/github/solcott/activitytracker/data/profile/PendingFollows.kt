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

    /** [requested]: the follow went to an athlete who approves followers, so it waits for them and doesn't count yet. */
    private class Pending(val uri: AtUri?, val requested: Boolean)

    /** Records that the athlete now follows [subject] with the record at [uri], or no longer does when it's null. */
    fun wrote(subject: Did, uri: AtUri?, requested: Boolean = false) {
        pending[subject] = Pending(uri, requested && uri != null)
    }

    /**
     * [view] with the athlete's last follow or unfollow of its athlete, and that change to the follower count, until it's indexed. A
     * request shows as requested and changes no count; for an athlete who approved this one before, the AppView corrects it once indexed.
     */
    fun applyTo(view: ProfileView): ProfileView {
        val wrote = pending[view.did]
        val shown = view.viewer?.following
        if (wrote?.uri == shown) pending.remove(view.did)
        if (wrote == null || view.did !in pending) return view
        val followers = view.followersCount ?: 0
        val shownCounts = shown != null && view.viewer?.followRequested != true
        val adjusted =
            when {
                wrote.uri == null -> if (shownCounts) (followers - 1).coerceAtLeast(0) else followers
                wrote.requested -> followers
                else -> followers + if (shownCounts) 0 else 1
            }
        val viewer = (view.viewer ?: ProfileViewerState()).copy(following = wrote.uri, followRequested = wrote.requested.takeIf { it })
        return view.copy(viewer = viewer, followersCount = adjusted)
    }
}
