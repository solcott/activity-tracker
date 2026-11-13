package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * A text field's state is the app's `TextFieldState` (`:core-textfield`, package `textfield`), with its `rememberTextFieldState`,
 * `textFieldStateOf`, `setTextAndPlaceCursorAtEnd` and `clearText`, not Compose's of the same names. Off iOS the two are one class, so
 * Compose's import compiles on the JVM, Android and the web and fails only in an iOS compile, which CI's Linux job doesn't run
 * (dev/ios-swift-interop).
 *
 * Files named `*Test.kt` are left alone: the tests that edit a state with Compose's API run on the JVM, where it is Compose's state. The
 * file name holds for every task, where a path pattern in `detekt.yml` doesn't (a source-set task sees paths relative to its source set).
 * The few files that bridge the two types (`:core-textfield`'s actuals) suppress the rule by name.
 */
class UseAppTextFieldState(config: Config) :
    Rule(config, "A text field's state is the app's TextFieldState (:core-textfield), which Swift export can carry, not Compose's.") {
    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val imported = importDirective.importedFqName?.asString().orEmpty()
        val name = imported.removePrefix(COMPOSE_PACKAGE)
        val composes = imported.startsWith(COMPOSE_PACKAGE) && name in APP_OWNED
        if (!composes || importDirective.containingKtFile.name.endsWith("Test.kt")) return
        report(
            Finding(
                Entity.from(importDirective),
                "Import $APP_PACKAGE$name instead of Compose's: off iOS it is the same class, and on iOS it is the type the screen " +
                    "contracts hold and Swift reads. Build a state outside composition with textFieldStateOf().",
            ),
        )
    }

    private companion object {
        const val COMPOSE_PACKAGE = "androidx.compose.foundation.text.input."
        const val APP_PACKAGE = "io.github.solcott.activitytracker.textfield."
        val APP_OWNED = setOf("TextFieldState", "rememberTextFieldState", "setTextAndPlaceCursorAtEnd", "clearText")
    }
}
