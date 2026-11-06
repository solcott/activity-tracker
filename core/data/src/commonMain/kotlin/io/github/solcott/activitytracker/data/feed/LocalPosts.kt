package io.github.solcott.activitytracker.data.feed

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Posts the athlete shared, changed or took back this session (feed/posts), by post URI: the post as they left it, or null once removed.
 * Laid over the AppView's reads until it catches up, so a refresh can't undo them: a new post shows first on the unfiltered timeline and on
 * their profile until listed, a changed one keeps the AppView's live proposal but the athlete's text and audience, and a removed one is
 * left out.
 */
@SingleIn(SessionScope::class)
@Inject
class LocalPosts {
    val changes: StateFlow<Map<String, PostView?>>
        field = MutableStateFlow(emptyMap())

    fun put(view: PostView) = changes.update { it + (view.uri.value to view) }

    fun remove(uri: String) = changes.update { it + (uri to null) }

    /** Puts the changes back as they were before a write that failed to queue. */
    fun restore(previous: Map<String, PostView?>) {
        changes.value = previous
    }

    /** [posts] (newest first) with [changes] laid over them; with [includeNew], the athlete's unlisted new posts go first. */
    fun applyTo(posts: List<PostView>, includeNew: Boolean = true, at: Map<String, PostView?> = changes.value): List<PostView> {
        val listed = posts.map { it.uri.value }.toSet()
        val shown = posts.mapNotNull { post ->
            if (post.uri.value !in at) return@mapNotNull post
            at[post.uri.value]?.let { local -> post.copy(text = local.text, visibility = local.visibility) }
        }
        val sent =
            if (includeNew) at.values.filterNotNull().filter { it.uri.value !in listed }.sortedByDescending { it.createdAt }
            else emptyList()
        return sent + shown
    }

    /** A timeline's [items] with [changes] laid over its posts, as [applyTo]. */
    fun applyToTimeline(items: List<TimelineItem>, includeNew: Boolean, at: Map<String, PostView?> = changes.value): List<TimelineItem> {
        val listed = items.mapNotNull { (it as? TimelineItem.Post)?.post?.uri?.value }.toSet()
        val shown = items.mapNotNull { item ->
            if (item !is TimelineItem.Post || item.post.uri.value !in at) return@mapNotNull item
            at[item.post.uri.value]?.let { local -> TimelineItem.Post(item.post.copy(text = local.text, visibility = local.visibility)) }
        }
        if (!includeNew) return shown
        val sent = at.values.filterNotNull().filter { it.uri.value !in listed }.sortedByDescending { it.createdAt }
        return sent.map(TimelineItem::Post) + shown
    }
}
