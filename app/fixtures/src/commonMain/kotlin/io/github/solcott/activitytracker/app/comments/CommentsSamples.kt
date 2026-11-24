package io.github.solcott.activitytracker.app.comments

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeComment
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.interactions.CommentThread
import io.github.solcott.activitytracker.app.interactions.CommentsPaging
import io.github.solcott.activitytracker.app.interactions.CommentsState
import io.github.solcott.activitytracker.app.interactions.RepliesState
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.LoadStatus
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf

/** The Comments screen (feed/comments): Bob's ride as Alice sees it, with Carol's comment with Bob's reply, and her question. */
object CommentsSamples {
    private val carols = fakeComment("3kc1", fakeProfileBasic(Fixed.CAROL), "Brutal climb up the ridge, and you still held that pace.")
    private val bobs = fakeComment("3kc2", fakeProfileBasic(Fixed.BOB), "Legs were cooked by the top.")
    // Written earlier today, so it reads how long ago ("25 minutes ago"), where the others read their day.
    private val question =
        fakeComment("3kc3", fakeProfileBasic(Fixed.CAROL), "Which way down did you take?", createdAt = Fixed.NOW - 25.minutes)

    /** The screen with two threads; a new draft each time. */
    val page: CommentsPage
        get() =
            CommentsPage(
                comments =
                    CommentsState(
                        paging =
                            CommentsPaging(
                                items =
                                    persistentListOf(
                                        CommentThread(carols.uri, carols, persistentListOf(bobs)),
                                        CommentThread(question.uri, question, persistentListOf()),
                                    ),
                                refresh = LoadStatus.Idle,
                                hasAnswer = true,
                                endReached = true,
                            ),
                        sent = persistentListOf(),
                        draft = textFieldStateOf(),
                        replyingTo = null,
                        viewerDid = Fixed.ALICE,
                        author = fakeProfileBasic(Fixed.BOB),
                        isOwn = false,
                        count = 3,
                    ),
                subtitle = AppText.written("Ridge loop"),
            )

    val all: Map<String, CommentsScreenState>
        get() = contentSamples<CommentsPage?, CommentsScreenState>(null, page) { CommentsScreenState(it) }

    /**
     * What [all]'s content-loading states don't show: no comments yet, a comment Alice hid on her own ride, a reply in progress with sent
     * comments, a thread with more replies and one whose replies failed, a failed comments page and a failed send, and a labeler
     * configured, so others' comments offer Report.
     */
    val variants: Map<String, CommentsScreenState>
        get() {
            fun loaded(page: CommentsPage, reporting: Reporting = Reporting()) =
                CommentsScreenState(ContentStates.loaded(page), reporting = reporting)
            val base = page
            val hidden = fakeComment("3kc4", fakeProfileBasic(Fixed.BOB), "This route is silly.").copy(hidden = true)
            val sent =
                SentComment(AtUri.of(Fixed.ALICE, Nsid.FEED_COMMENT, "3ksent"), fakeProfileBasic(Fixed.ALICE), "Thanks all!", Fixed.NOW)
            val reply =
                SentComment(
                    AtUri.of(Fixed.ALICE, Nsid.FEED_COMMENT, "3ksentreply"),
                    sent.author,
                    "Down the creek.",
                    Fixed.NOW,
                    root = question.uri,
                )
            val comments = base.comments
            return mapOf(
                "empty" to
                    loaded(base.copy(comments = comments.copy(paging = comments.paging.copy(items = persistentListOf()), count = 0))),
                "ownHidden" to
                    loaded(
                        base.copy(
                            comments =
                                comments.copy(
                                    paging =
                                        comments.paging.copy(
                                            items = persistentListOf(CommentThread(hidden.uri, hidden, persistentListOf())),
                                        ),
                                    author = fakeProfileBasic(Fixed.ALICE),
                                    isOwn = true,
                                    count = 0,
                                ),
                        ),
                    ),
                "replying" to
                    loaded(
                        base.copy(
                            comments =
                                comments.copy(
                                    paging =
                                        comments.paging.copy(
                                            items =
                                                persistentListOf(
                                                    comments.paging.items.first(),
                                                    comments.paging.items.last().copy(sentReplies = persistentListOf(reply)),
                                                ),
                                        ),
                                    sent = persistentListOf(sent),
                                    draft = textFieldStateOf("See you out there"),
                                    replyingTo = question,
                                    count = 5,
                                ),
                        ),
                    ),
                "moreReplies" to
                    loaded(
                        base.copy(
                            comments =
                                comments.copy(
                                    paging =
                                        comments.paging.copy(
                                            items =
                                                persistentListOf(
                                                    comments.paging.items.first().copy(moreReplies = 3),
                                                    CommentThread(
                                                        AtUri.of(Fixed.BOB, Nsid.FEED_COMMENT, "3kgone"),
                                                        null,
                                                        persistentListOf(fakeComment("3kc5", fakeProfileBasic(Fixed.CAROL), "Still true.")),
                                                        moreReplies = 2,
                                                        repliesState = RepliesState.Failed(UiError(UiError.Kind.LoadComments)),
                                                    ),
                                                ),
                                        ),
                                ),
                        ),
                    ),
                "commentsFailed" to
                    loaded(
                        base.copy(comments = comments.copy(paging = comments.paging.copy(append = LoadStatus.Failed(DataError.Network)))),
                    ),
                "sendFailed" to
                    loaded(
                        base.copy(comments = comments.copy(draft = textFieldStateOf("Nice"), error = UiError(UiError.Kind.SignInAgain))),
                    ),
                "reportable" to loaded(base, Reporting(available = true)),
            )
        }
}
