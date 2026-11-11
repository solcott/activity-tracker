package io.github.solcott.activitytracker.app.moderation

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.WarnReason

/** A line a labeled subject shows above its content (`moderation/labels`): its words, and whether it reads as an error (a takedown). */
@Immutable data class NoticeText(val text: AppText, val isError: Boolean)

/** How labels read (`moderation/labels`, dev/shared-display-text): their names, and the notices a labeled subject shows. */
object ModerationTexts {
    /** A media label's name, as a blurred photo or avatar shows it. */
    fun blur(reason: BlurReason): AppText =
        AppText.named(
            when (reason) {
                BlurReason.Porn -> "label_porn"
                BlurReason.Sexual -> "label_sexual"
                BlurReason.Nudity -> "label_nudity"
                BlurReason.GraphicMedia -> "label_graphic_media"
            },
        )

    /** A warning label's name. */
    fun warn(reason: WarnReason): AppText =
        AppText.named(
            when (reason) {
                WarnReason.Warn -> "label_warn"
                WarnReason.Spam -> "label_spam"
                WarnReason.Impersonation -> "label_impersonation"
            },
        )

    /**
     * The lines a labeled subject shows above its content, in order: a warning naming the label, the athlete's own note that their media is
     * blurred for everyone, or that their content was taken down; then a [warn]ing on the content or its author beside whatever else
     * applies (beside a blur, or once a hidden subject is opened), since warnings never hide anything. None when no label calls for one.
     */
    fun notices(decision: ModerationDecision, isOwn: Boolean, warn: WarnReason? = null): List<NoticeText> {
        val primary =
            when (decision) {
                is ModerationDecision.Warn -> NoticeText(warn(decision.reason), isError = false)
                is ModerationDecision.Blur ->
                    if (isOwn) NoticeText(AppText.named("label_own_blurred", blur(decision.reason)), isError = false) else null
                ModerationDecision.Takedown -> NoticeText(AppText.named("label_taken_down"), isError = true)
                ModerationDecision.Hidden,
                ModerationDecision.None -> null
            }
        val warning =
            warn
                ?.takeUnless { decision is ModerationDecision.Warn || decision == ModerationDecision.Takedown }
                ?.let { NoticeText(warn(it), isError = false) }
        return listOfNotNull(primary, warning)
    }
}
