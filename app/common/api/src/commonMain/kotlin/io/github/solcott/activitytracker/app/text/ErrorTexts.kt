package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.app.FailureReason
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.dataresult.DataError

/** The names of the kinds whose string isn't named by the rule in [ErrorTexts.kindName]. */
private val IRREGULAR_NAMES = mapOf(UiError.Kind.DiscardLeftElsewhere to "error_discard_left")

/** Where a word starts inside a kind's name. */
private val WORD_START = Regex("(?<!^)([A-Z])")

/**
 * What an error says (ui/error-messages): what failed and, when the reason is one the athlete can act on or wait out, why. Never the
 * failure's own text, status code or error name. Chosen here for every UI.
 */
object ErrorTexts {
    /** [error] as text. */
    fun of(error: UiError): AppText = failure(AppText.named(kindName(error.kind)), error.reason)

    /** [what] failed, followed by why when [reason] is one worth saying. */
    fun failure(what: AppText, reason: DataError?): AppText =
        reason?.let(FailureReason::of)?.let { AppText.named("error_with_reason", what, AppText.named(reasonName(it))) } ?: what

    /**
     * The shared string that says [kind] failed: `error_` and the kind's name in snake case (`LoadFeed` is `error_load_feed`), so a new
     * kind needs only its string. `:app-common-ui`'s names test fails for a kind whose string is missing.
     */
    fun kindName(kind: UiError.Kind): String = IRREGULAR_NAMES[kind] ?: ("error_" + kind.name.replace(WORD_START, "_$1").lowercase())

    /** The shared string that says why, for a reason worth saying. */
    fun reasonName(reason: FailureReason): String =
        when (reason) {
            FailureReason.Unreachable -> "error_reason_unreachable"
            FailureReason.Server -> "error_reason_server"
            FailureReason.Unreadable -> "error_reason_unreadable"
        }
}
