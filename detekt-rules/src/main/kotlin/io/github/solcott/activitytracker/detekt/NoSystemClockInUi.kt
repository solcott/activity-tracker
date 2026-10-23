package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * UI code reads the current time and time zone from `LocalClock` and `LocalTimeZone` (`app.ui`), so previews and tests can fix them, rather
 * than from `Clock.System` or `TimeZone.currentSystemDefault()`.
 *
 * Applies to files named `*Ui.kt`, the UIs' naming convention, which holds for every task (a source-set task sees paths relative to its
 * source set, so a path pattern can't pick them out). Plain analysis, without types.
 */
class NoSystemClockInUi(config: Config) :
    Rule(config, "UI code reads LocalClock and LocalTimeZone, not the system clock or time zone, so previews and tests can fix them.") {
    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (!expression.containingKtFile.name.endsWith("Ui.kt")) return
        val receiver = expression.receiverExpression.text.substringAfterLast('.')
        val selector = expression.selectorExpression
        val replacement =
            when {
                receiver == "Clock" && selector?.text == "System" -> "LocalClock.current"
                receiver == "TimeZone" && (selector as? KtCallExpression)?.calleeExpression?.text == "currentSystemDefault" ->
                    "LocalTimeZone.current"
                else -> return
            }
        report(
            Finding(
                Entity.from(expression),
                "${expression.text} can't be fixed by a preview or test. Read $replacement (app.ui) instead.",
            )
        )
    }
}
