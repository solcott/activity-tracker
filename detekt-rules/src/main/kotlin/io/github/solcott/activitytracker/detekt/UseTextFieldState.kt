package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Text fields take a `TextFieldState`, not the `value` + `onValueChange` overloads (`String` or `TextFieldValue`). The state is
 * `rememberTextFieldState()`: in the presenter, held in the screen's `State`, when the presenter needs the text, otherwise in the UI.
 *
 * Plain analysis, without types: a call to one of the foundation or Material 3 text fields is flagged when it names `value` or
 * `onValueChange`, or passes a second positional argument that isn't a modifier (the state overloads take only the state positionally).
 */
class UseTextFieldState(config: Config) : Rule(config, "Text fields take a TextFieldState, not value and onValueChange.") {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text !in TEXT_FIELDS) return
        val args = expression.valueArgumentList?.arguments.orEmpty()
        val named = args.mapNotNull { it.getArgumentName()?.asName?.asString() }
        val positional = args.filterNot { it.isNamed() }
        val secondPositional = positional.getOrNull(1)?.getArgumentExpression()?.text
        val valueOverload =
            named.any { it in VALUE_PARAMS } || (secondPositional != null && !secondPositional.startsWith("modifier", ignoreCase = true))
        if (valueOverload) {
            report(
                Finding(
                    Entity.from(expression),
                    "${expression.calleeExpression?.text} takes a value and onValueChange. Pass a TextFieldState instead " +
                        "(rememberTextFieldState(), in the presenter's State if it reads the text), and name every argument after it.",
                )
            )
        }
    }

    private companion object {
        val TEXT_FIELDS = setOf("TextField", "OutlinedTextField", "BasicTextField")
        val VALUE_PARAMS = setOf("value", "onValueChange")
    }
}
