package io.github.solcott.activitytracker.app.post

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.interactions.CommentsState
import io.github.solcott.activitytracker.app.interactions.LikesList
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.uistate.ContentState

/** A post's page (feed/post-page): its card, who liked it, and its comment threads. [uri] is the `feed.post`'s AT-URI. */
@CircuitSerializable(SessionScope::class) data class PostPageScreen(val uri: String) : Screen

/**
 * The post page (ui/content-loading): [content]'s status and origin are the post's load, its data the page, null until the post first
 * loads. [notFound]: the AppView doesn't serve the post to the viewer (withdrawn, taken down, or outside its audience), which the page says
 * in place of an error.
 */
@Immutable
data class PostPageState(
    val content: ContentState<PostPage?> = ContentState(null),
    val notFound: Boolean = false,
    val eventSink: (PostPageEvent) -> Unit,
) : CircuitUiState

/**
 * The loaded post page, as the viewer left it.
 *
 * @property post the post, its like and comment counts already moved by the viewer's own changes the AppView hasn't caught up with.
 * @property likesList who liked it, once the viewer has shown the list; null while it's hidden.
 * @property error why the viewer's last like or vote couldn't be given.
 */
@Immutable
data class PostPage(
    val post: PostView,
    val comments: CommentsState,
    val viewerDid: Did,
    val likesList: LikesList? = null,
    val error: UiError? = null,
    /** Report on someone else's post and on others' comments. */
    val reporting: Reporting = Reporting(),
)

sealed interface PostPageEvent {
    data object Back : PostPageEvent

    data object Retry : PostPageEvent

    /** A tap on the post's card: votes, likes, opening its segment or author, reporting. */
    data class Post(val event: PostEvent) : PostPageEvent

    /** Shows or hides who liked the post. */
    data object ToggleLikesList : PostPageEvent

    data class OpenProfile(val did: Did) : PostPageEvent
}
