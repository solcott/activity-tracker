package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class UseAppTextFieldStateTest {
    private val rule = UseAppTextFieldState(Config.empty)

    private fun importing(vararg names: String) = names.joinToString("\n") { "import $it" } + "\n\nclass X\n"

    @Test
    fun flagsEachOfComposesFourNames() {
        val code =
            importing(
                "androidx.compose.foundation.text.input.TextFieldState",
                "androidx.compose.foundation.text.input.rememberTextFieldState",
                "androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd",
                "androidx.compose.foundation.text.input.clearText",
            )

        assertEquals(4, rule.lint(compileContentForTest(code, "XPresenter.kt")).size)
    }

    @Test
    fun namesTheAppsImportInItsMessage() {
        val code = importing("androidx.compose.foundation.text.input.TextFieldState")

        val finding = rule.lint(compileContentForTest(code, "XScreen.kt")).single()

        assertContains(finding.message, "io.github.solcott.activitytracker.textfield.TextFieldState")
    }

    @Test
    fun flagsAnAliasedImport() {
        val code = "import androidx.compose.foundation.text.input.TextFieldState as ComposeState\n\nclass X\n"

        assertEquals(1, rule.lint(compileContentForTest(code, "XUi.kt")).size)
    }

    @Test
    fun allowsTheAppsNames() {
        val code =
            importing(
                "io.github.solcott.activitytracker.textfield.TextFieldState",
                "io.github.solcott.activitytracker.textfield.rememberTextFieldState",
                "io.github.solcott.activitytracker.textfield.textFieldStateOf",
            )

        assertEquals(emptyList(), rule.lint(compileContentForTest(code, "XPresenter.kt")))
    }

    @Test
    fun allowsComposesUiOnlyTextFieldTypes() {
        val code =
            importing(
                "androidx.compose.foundation.text.input.TextFieldLineLimits",
                "androidx.compose.foundation.text.input.InputTransformation",
                "androidx.compose.foundation.text.input.KeyboardActionHandler",
                "androidx.compose.foundation.text.input.maxLength",
            )

        assertEquals(emptyList(), rule.lint(compileContentForTest(code, "XUi.kt")))
    }

    @Test
    fun ignoresTestFiles() {
        val code = importing("androidx.compose.foundation.text.input.TextFieldState", "androidx.compose.foundation.text.input.clearText")

        assertEquals(emptyList(), rule.lint(compileContentForTest(code, "XPresenterTest.kt")))
    }
}
