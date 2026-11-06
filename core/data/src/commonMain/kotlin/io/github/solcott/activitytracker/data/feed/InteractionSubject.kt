package io.github.solcott.activitytracker.data.feed

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.PostView

/**
 * What a like, comment or hide is on: an activity, or a post (feed/posts). Writes need only what's here: the subject's strong ref, its
 * author (who may hide comments on it, and can't like it), the [visibility] its view shows (where a like or a top-level comment goes, and
 * the comment's audience), and the viewer's [like] as the view reported it.
 */
@Immutable
data class InteractionSubject(val uri: AtUri, val cid: Cid, val author: Did, val visibility: String, val like: AtUri? = null) {
    /** Whether it's a post, whose cached copies are [PostView]s rather than [ActivityView]s. */
    val isPost: Boolean
        get() = uri.collection == Nsid.FEED_POST
}

fun ActivityView.asSubject(): InteractionSubject = InteractionSubject(uri, cid, author.did, visibilityOrPublic, viewer?.like)

fun PostView.asSubject(): InteractionSubject = InteractionSubject(uri, cid, author.did, visibilityOrPublic, viewer?.like)
