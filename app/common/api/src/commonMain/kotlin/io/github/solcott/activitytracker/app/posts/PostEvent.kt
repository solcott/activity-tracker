package io.github.solcott.activitytracker.app.posts

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic

/** A tap on a post card (feed/posts), which every host wraps in an event of its own and hands to `rememberPostEventSink`. */
@Immutable
sealed interface PostEvent {
    /** Opens the segment or dive site the post's proposal names. */
    data class Open(val post: PostView) : PostEvent

    /** Opens the post page (feed/post-page): its comments and who liked it. */
    data class OpenPost(val post: PostView) : PostEvent

    /** Gives or takes back the viewer's like of someone else's post. */
    data class Like(val post: PostView) : PostEvent

    /** Reposts someone else's post, or undoes the viewer's repost of it (feed/reposts). */
    data class Repost(val post: PostView) : PostEvent

    /** Opens the quote sheet for the post (feed/quote-posts), the viewer's own included. */
    data class Quote(val post: PostView) : PostEvent

    /** Opens the page of the post at [uri], quoted in the post (feed/quote-posts). */
    data class OpenQuoted(val uri: AtUri) : PostEvent

    /** Deletes the viewer's own quote, once they confirm (feed/quote-posts). */
    data class Delete(val post: PostView) : PostEvent

    /** Opens the profile of [reposter], who put the post in the viewer's timeline (feed/reposts). */
    data class OpenReposter(val reposter: ProfileViewBasic) : PostEvent

    /** Opens the post's author's profile. */
    data class OpenAuthor(val post: PostView) : PostEvent

    /** Votes [direction] (a `SegmentNameVoteDirection` value) on the post's proposal, or takes the viewer's vote back when it's null. */
    data class Vote(val post: PostView, val direction: String?) : PostEvent

    /** Opens the report sheet for the post. */
    data class Report(val post: PostView) : PostEvent
}
