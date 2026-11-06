package io.github.solcott.activitytracker.app.posts

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.PostView

/** A tap on a post card (feed/posts), which every host wraps in an event of its own and hands to `rememberPostEventSink`. */
@Immutable
sealed interface PostEvent {
    /** Opens the segment or dive site the post's proposal names. */
    data class Open(val post: PostView) : PostEvent

    /** Opens the post page (feed/post-page): its comments and who liked it. */
    data class OpenPost(val post: PostView) : PostEvent

    /** Gives or takes back the viewer's like of someone else's post. */
    data class Like(val post: PostView) : PostEvent

    /** Opens the post's author's profile. */
    data class OpenAuthor(val post: PostView) : PostEvent

    /** Votes [direction] (a `SegmentNameVoteDirection` value) on the post's proposal, or takes the viewer's vote back when it's null. */
    data class Vote(val post: PostView, val direction: String?) : PostEvent

    /** Opens the report sheet for the post. */
    data class Report(val post: PostView) : PostEvent
}
