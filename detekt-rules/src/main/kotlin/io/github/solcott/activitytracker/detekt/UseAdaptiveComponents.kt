package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Screens use the app's adaptive components (`app.ui.adaptive`: `AppScaffold`, `AppTopBar`, `AppButton`, `AppTextField`,
 * `AppBottomSheet`…), which follow each platform's conventions, rather than the Material 3 widgets they wrap, which look like Android
 * everywhere.
 *
 * Plain analysis, without types: a call to one of the wrapped Material names is flagged anywhere outside the `ui.adaptive` package.
 */
class UseAdaptiveComponents(config: Config) :
    Rule(config, "Screens use the adaptive components in app.ui.adaptive, not the Material 3 widgets they wrap.") {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val name = expression.calleeExpression?.text
        val replacement = REPLACEMENTS[name]
        if (replacement == null || expression.containingKtFile.packageFqName.asString().endsWith(ADAPTIVE_PACKAGE)) return
        report(
            Finding(
                Entity.from(expression),
                "$name looks like Android on every platform. " +
                    "Use $replacement (app.ui.adaptive), which follows each platform's conventions.",
            )
        )
    }

    private companion object {
        const val ADAPTIVE_PACKAGE = ".ui.adaptive"
        val REPLACEMENTS =
            mapOf(
                "Scaffold" to "AppScaffold",
                "TopAppBar" to "AppTopBar",
                "CenterAlignedTopAppBar" to "AppTopBar",
                "MediumTopAppBar" to "AppTopBar",
                "LargeTopAppBar" to "AppTopBar",
                "Button" to "AppButton",
                "FilledTonalButton" to "AppSecondaryButton",
                "OutlinedButton" to "AppSecondaryButton",
                "ElevatedButton" to "AppSecondaryButton",
                "TextButton" to "AppTextButton",
                "IconButton" to "AppIconButton",
                "OutlinedTextField" to "AppTextField",
                "TextField" to "AppTextField",
                "CircularProgressIndicator" to "AppProgressIndicator",
                "ModalBottomSheet" to "AppBottomSheet",
                "DatePickerDialog" to "AppDatePickerDialog",
                "AlertDialog" to "AppAlertDialog",
                "BasicAlertDialog" to "AppAlertDialog",
                "DropdownMenu" to "AppMenu",
                "SingleChoiceSegmentedButtonRow" to "AppSegmentedControl",
                "Switch" to "AppSwitch",
                "Checkbox" to "AppCheckbox",
                "FilterChip" to "AppFilterChip",
            )
    }
}
