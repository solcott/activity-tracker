package io.github.solcott.activitytracker.app.moderation

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the Moderation page says and offers, for the shared and the native page (moderation/private-subjects, dev/shared-display-text). */
class ModerationSubjectTextsTest {
    private val samples = ModerationSamples.all + ModerationSamples.variants

    @Test
    fun theVisibilityLineNamesTheTier() {
        assertEquals(
            AppText.named("moderation_visibility", AppText.named("visibility_followers")),
            ModerationSubjectTexts.visibility(Visibility.FOLLOWERS),
        )
        // A value the app doesn't know reads as the narrowest.
        assertEquals(
            AppText.named("moderation_visibility", AppText.named("visibility_self")),
            ModerationSubjectTexts.visibility("friends-of-friends"),
        )
    }

    @Test
    fun aCommentsAuthorIsTheirDisplayNameElseTheirHandle() {
        assertEquals(AppText.written("Bob"), ModerationSubjectTexts.commentAuthor(comment("comment")))
        assertEquals(LabelText.handle("carol.test"), ModerationSubjectTexts.commentAuthor(comment("commentNoName")))
    }

    @Test
    fun openIsHeldOnlyWhileOpening() {
        assertTrue(ModerationSubjectTexts.canOpen(sample(SampleKind.LOADED)))
        assertTrue(ModerationSubjectTexts.canOpen(sample("invalidUri")))
        assertFalse(ModerationSubjectTexts.canOpen(sample("opening")))
    }

    @Test
    fun everyNameThePageChoosesHasItsString() {
        val texts =
            Visibility.all.map(ModerationSubjectTexts::visibility) +
                samples.values.mapNotNull { (it.subject as? ModerationSubject.Comment)?.comment }.map(ModerationSubjectTexts::commentAuthor)
        val names = texts.flatMap(::names)
        val missing = names.filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        if (text is AppText.Named) listOf(text.name) + text.args.flatMap(::names) else emptyList()

    private fun comment(name: String) = checkNotNull(sample(name).subject as? ModerationSubject.Comment).comment

    private fun sample(name: String) = checkNotNull(samples[name]) { "no sample $name" }
}
