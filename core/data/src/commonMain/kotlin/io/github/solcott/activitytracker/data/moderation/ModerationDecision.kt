package io.github.solcott.activitytracker.data.moderation

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.LabelView

/**
 * What the app does with a labeled activity, comment, photo or profile (`moderation/labels`), from the labels on the view and on its
 * author's account. The AppView already left out what it doesn't serve; this decides how the rest shows. Fixed in v1; viewer preferences
 * come later.
 */
@Immutable
sealed interface ModerationDecision {
    /** Shown as usual. Unknown label values end here. */
    @Immutable data object None : ModerationDecision

    /** Shown with a warning naming [reason], not hidden. */
    @Immutable data class Warn(val reason: WarnReason) : ModerationDecision

    /** Media blurred until tapped, with [reason] shown; the rest of the content as usual. */
    @Immutable data class Blur(val reason: BlurReason) : ModerationDecision

    /** Reached by URI: collapsed behind a warning the viewer can open. Lists never carry it. */
    @Immutable data object Hidden : ModerationDecision

    /** The author's own content, which no one else is served: marked as taken down, its photos a placeholder. */
    @Immutable data object Takedown : ModerationDecision

    companion object {
        /**
         * The strongest treatment [labels] (the view's own) and [authorLabels] (its author's account) call for, for a viewer who is the
         * author ([isOwn]) or not: Takedown, then Hidden, then Blur, then Warn. An athlete sees their own hidden content as usual, and
         * their own labeled media blurred like anyone, with the note saying why.
         */
        fun of(labels: List<LabelView>?, authorLabels: List<LabelView>?, isOwn: Boolean): ModerationDecision {
            val values = (labels.orEmpty() + authorLabels.orEmpty()).map { it.value }.toSet()
            return when {
                TAKEDOWN in values -> if (isOwn) Takedown else Hidden
                HIDE in values && !isOwn -> Hidden
                else ->
                    BlurReason.entries.firstOrNull { it.value in values }?.let(::Blur)
                        ?: WarnReason.entries.firstOrNull { it.value in values }?.let(::Warn)
                        ?: None
            }
        }

        /** How media under [labels] (on the activity or profile) and [authorLabels] show: blurred for a media label, else as usual. */
        fun mediaOf(labels: List<LabelView>?, authorLabels: List<LabelView>?): BlurReason? {
            val values = (labels.orEmpty() + authorLabels.orEmpty()).map { it.value }.toSet()
            return BlurReason.entries.firstOrNull { it.value in values }
        }

        /** The warning [labels] and [authorLabels] carry, if any, whatever stronger treatment applies too. */
        fun warnOf(labels: List<LabelView>?, authorLabels: List<LabelView>?): WarnReason? {
            val values = (labels.orEmpty() + authorLabels.orEmpty()).map { it.value }.toSet()
            return WarnReason.entries.firstOrNull { it.value in values }
        }

        private const val TAKEDOWN = "!takedown"
        private const val HIDE = "!hide"
    }
}

/** The media label values, strongest first. */
enum class BlurReason(val value: String) {
    Porn("porn"),
    Sexual("sexual"),
    GraphicMedia("graphic-media"),
    Nudity("nudity"),
}

/** The warning label values, strongest first. */
enum class WarnReason(val value: String) {
    Warn("!warn"),
    Impersonation("impersonation"),
    Spam("spam"),
}
