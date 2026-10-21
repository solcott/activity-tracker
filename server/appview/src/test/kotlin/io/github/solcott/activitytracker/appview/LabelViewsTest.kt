package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.LabelView
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/** `Labels in views` (`moderation/labels`): each view carries the labels that apply to it, and its author's on `author`. */
class LabelViewsTest : ModerationTestBase() {
    private fun labels(vararg values: String) = values.map { LabelView(Did(labeler), it) }

    @Test
    fun anActivityViewCarriesItsLabelsAndItsAuthorsOnAuthor() = testApplication {
        start()
        listOf(alice, bob).forEach(::profile)
        post(bob, "3kb")
        label(activityUri(bob, "3kb"), "graphic-media")
        label(bob, "spam")
        val view = client.query(GetActivity.method, alice, "?uri=${activityUri(bob, "3kb").encodeURLParameter()}").activity
        assertEquals(labels("graphic-media"), view.labels)
        assertEquals(labels("spam"), view.author.labels)
    }

    @Test
    fun negatedPinnedElsewhereAndUnknownLabels() = testApplication {
        start()
        profile(bob)
        post(bob, "3kb")
        label(activityUri(bob, "3kb"), "nudity")
        label(activityUri(bob, "3kb"), "nudity", neg = true)
        label(activityUri(bob, "3kb"), "porn", cid = "bafyolder")
        label(activityUri(bob, "3kb"), "rude")
        val view = client.query(GetActivity.method, null, "?uri=${activityUri(bob, "3kb").encodeURLParameter()}").activity
        assertEquals(labels("rude"), view.labels, "an unknown value is passed on for clients to ignore")
    }

    @Test
    fun noLabelsMeansNoField() = testApplication {
        start()
        profile(bob)
        post(bob, "3kb")
        val view = client.query(GetActivity.method, null, "?uri=${activityUri(bob, "3kb").encodeURLParameter()}").activity
        assertEquals(null, view.labels)
        assertEquals(null, view.author.labels)
    }

    @Test
    fun aCommentViewCarriesItsLabels() = testApplication {
        start()
        listOf(alice, bob).forEach(::profile)
        post(alice, "3ka")
        comment(bob, alice, "3ka")
        label(commentUri(bob, alice), "!warn")
        val comments = client.query(GetComments.method, alice, "?uri=${activityUri(alice, "3ka").encodeURLParameter()}").comments
        assertEquals(labels("!warn"), comments.single().labels)
    }

    @Test
    fun aProfileViewCarriesItsAccountsAndProfileRecordsLabels() = testApplication {
        start()
        listOf(alice, bob).forEach(::profile)
        label(bob, "impersonation")
        label("at://$bob/${Nsid.ACTOR_PROFILE}/self", "porn")
        val profile = client.query(GetProfile.method, alice, "?actor=$bob")
        assertEquals(labels("impersonation", "porn").toSet(), profile.labels.orEmpty().toSet())
    }
}
