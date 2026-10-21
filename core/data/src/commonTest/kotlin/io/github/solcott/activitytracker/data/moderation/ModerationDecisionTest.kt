package io.github.solcott.activitytracker.data.moderation

import io.github.solcott.activitytracker.data.moderation.ModerationDecision.Blur
import io.github.solcott.activitytracker.data.moderation.ModerationDecision.Hidden
import io.github.solcott.activitytracker.data.moderation.ModerationDecision.None
import io.github.solcott.activitytracker.data.moderation.ModerationDecision.Takedown
import io.github.solcott.activitytracker.data.moderation.ModerationDecision.Warn
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.LabelView
import kotlin.test.Test
import kotlin.test.assertEquals

/** `moderation/labels`: which treatment each label set gets, the view's own labels with its author's. */
class ModerationDecisionTest {
    private fun labels(vararg values: String) = values.map { LabelView(Did("did:plc:labeler"), it) }

    private data class Case(val own: List<String>, val author: List<String>, val isOwn: Boolean, val expected: ModerationDecision)

    private val cases =
        listOf(
            Case(emptyList(), emptyList(), false, None),
            Case(listOf("rude", "something-new"), emptyList(), false, None),
            Case(listOf("!takedown"), emptyList(), true, Takedown),
            Case(emptyList(), listOf("!takedown"), true, Takedown),
            Case(listOf("!takedown"), emptyList(), false, Hidden),
            Case(listOf("!hide"), emptyList(), false, Hidden),
            Case(emptyList(), listOf("!hide"), false, Hidden),
            Case(listOf("!hide"), emptyList(), true, None),
            Case(listOf("!hide", "nudity"), emptyList(), true, Blur(BlurReason.Nudity)),
            Case(listOf("nudity"), emptyList(), false, Blur(BlurReason.Nudity)),
            Case(listOf("nudity"), emptyList(), true, Blur(BlurReason.Nudity)),
            Case(listOf("graphic-media", "porn"), emptyList(), false, Blur(BlurReason.Porn)),
            Case(emptyList(), listOf("sexual"), false, Blur(BlurReason.Sexual)),
            Case(listOf("graphic-media"), listOf("spam"), false, Blur(BlurReason.GraphicMedia)),
            Case(emptyList(), listOf("spam"), false, Warn(WarnReason.Spam)),
            Case(listOf("!warn"), listOf("impersonation"), false, Warn(WarnReason.Warn)),
            Case(emptyList(), listOf("impersonation"), true, Warn(WarnReason.Impersonation)),
        )

    @Test
    fun eachLabelSetMapsToItsTreatment() {
        for (case in cases) {
            assertEquals(
                case.expected,
                ModerationDecision.of(labels(*case.own.toTypedArray()), labels(*case.author.toTypedArray()), case.isOwn),
                "${case.own} on the view, ${case.author} on the author, own: ${case.isOwn}",
            )
        }
    }

    @Test
    fun noLabelsAtAll() {
        assertEquals(None, ModerationDecision.of(null, null, isOwn = false))
    }

    @Test
    fun mediaBlursForMediaLabelsOnly() {
        assertEquals(BlurReason.Nudity, ModerationDecision.mediaOf(labels("nudity", "spam"), null))
        assertEquals(BlurReason.GraphicMedia, ModerationDecision.mediaOf(null, labels("graphic-media")))
        assertEquals(null, ModerationDecision.mediaOf(labels("!warn", "!hide"), labels("spam")))
    }

    @Test
    fun aWarningIsKeptBesideABlur() {
        assertEquals(WarnReason.Spam, ModerationDecision.warnOf(labels("nudity"), labels("spam")))
        assertEquals(null, ModerationDecision.warnOf(labels("nudity"), null))
    }
}
