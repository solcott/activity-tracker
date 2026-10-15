package io.github.solcott.activitytracker.app.detail

/** What the viewer may do to a comment from its "…" menu. */
internal enum class CommentAction {
    /** Their own comment: Delete, which asks first. */
    Delete,

    /** Someone else's comment on their own activity: Hide, which doesn't ask, since it can be undone. */
    Hide,

    /** A comment they hid on their own activity. */
    Unhide,
}
