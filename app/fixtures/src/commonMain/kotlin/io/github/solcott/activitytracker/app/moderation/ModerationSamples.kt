package io.github.solcott.activitytracker.app.moderation

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeComment
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError

object ModerationSamples {
    private val activity = fakeActivity()

    val all: Map<String, ModerationState>
        get() =
            mapOf(
                SampleKind.LOADED to ModerationState(textFieldStateOf(), loading = false, subject = null, error = null, eventSink = {}),
                "activity" to
                    ModerationState(
                        textFieldStateOf(activity.uri.value),
                        loading = false,
                        subject = ModerationSubject.Activity(activity, Visibility.PUBLIC),
                        error = null,
                        eventSink = {},
                    ),
            )

    /** The rest of what the page shows, which the native iOS page draws: a comment, nothing under review, opening and the failures. */
    val variants: Map<String, ModerationState>
        get() {
            val comment = fakeComment(text = "Nobody should ride this line. It's closed for nesting season and you know it.")
            val unnamed =
                fakeComment(rkey = "3kcomment2", author = fakeProfileBasic(Fixed.CAROL, displayName = null), text = "Spam spam spam")
            fun state(uri: String, subject: ModerationSubject? = null, loading: Boolean = false, error: UiError? = null) =
                ModerationState(textFieldStateOf(uri), loading = loading, subject = subject, error = error, eventSink = {})
            return mapOf(
                "comment" to state(comment.uri.value, ModerationSubject.Comment(comment, Visibility.FOLLOWERS)),
                "commentNoName" to state(unnamed.uri.value, ModerationSubject.Comment(unnamed, Visibility.SELF)),
                "notUnderReview" to state(activity.uri.value, ModerationSubject.NotUnderReview),
                "opening" to state(activity.uri.value, loading = true),
                "invalidUri" to state("hello", error = UiError(UiError.Kind.InvalidSubjectUri)),
                "openFailed" to state(activity.uri.value, error = UiError(UiError.Kind.OpenSubject, DataError.Network)),
            )
        }
}
