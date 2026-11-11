package io.github.solcott.activitytracker.app.post

import io.github.solcott.activitytracker.app.text.AppText

/** What the post page and the quotes list (feed/post-page, feed/quote-posts) say, for the shared and the native pages. */
object PostPageTexts {
    val title: AppText
        get() = AppText.named("post_page_title")

    /** In place of a post the AppView doesn't serve the viewer, or of the quotes of one. */
    val unavailable: AppText
        get() = AppText.named("post_unavailable")

    /** Show likes, or Hide likes while they're [shown]. */
    fun likesToggle(shown: Boolean): AppText = AppText.named(if (shown) "detail_hide_likes" else "detail_show_likes")

    /** Show reposts, or Hide reposts while they're [shown]. */
    fun repostsToggle(shown: Boolean): AppText = AppText.named(if (shown) "post_hide_reposts" else "post_show_reposts")

    val showQuotes: AppText
        get() = AppText.named("post_show_quotes")

    /** A likes or reposts list with no one in it. */
    val noLikes: AppText
        get() = AppText.named("detail_no_likes")

    val quotesTitle: AppText
        get() = AppText.named("post_quotes_title")

    val quotesEmpty: AppText
        get() = AppText.named("post_quotes_empty")
}
