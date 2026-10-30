package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Presenters launch event-driven work through `rememberSingleFlight()`, not a bare `rememberCoroutineScope()`, so a double tap can't run a
 * save, follow or like twice.
 *
 * Applies to files named `*Presenter.kt`, the presenters' naming convention. That holds for every task (a source-set task sees paths
 * relative to its own source dir, so a path filter in `detekt.yml` can't pick out the presenter modules reliably), and UIs, which can
 * launch animations from a `rememberCoroutineScope()`, never match.
 */
class UseSingleFlight(config: Config) :
    Rule(config, "Presenters launch event work through rememberSingleFlight(), which drops a repeat while the first runs.") {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != "rememberCoroutineScope") return
        if (!expression.containingKtFile.name.endsWith("Presenter.kt")) return
        report(
            Finding(
                Entity.from(expression),
                "Launch event work with rememberSingleFlight().launch(key) { ... } so a repeated tap is dropped while the first runs " +
                    "(launchLatest to replace it instead), and read isRunning(key) for a saving or pending flag.",
            )
        )
    }
}
