package io.github.solcott.activitytracker.app.detail

/** What the viewer may do to a comment from its "…" menu. */
internal enum class CommentAction {
    /** A listed comment or reply that isn't hidden: Reply, a button under it rather than in its menu. */
    Reply,

    /** Their own comment: Delete, which asks first. */
    Delete,

    /** Someone else's comment on their own activity: Hide, which doesn't ask, since it can be undone. */
    Hide,

    /** A comment they hid on their own activity. */
    Unhide,

    /** Someone else's comment: Mute its author, which doesn't ask. */
    MuteAuthor,

    /** Someone else's comment: Block its author, which asks first. */
    BlockAuthor,

    /** Someone else's comment, with a labeler configured: Report it. */
    Report,
}
