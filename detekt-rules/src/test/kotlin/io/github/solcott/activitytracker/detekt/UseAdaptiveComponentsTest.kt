package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UseAdaptiveComponentsTest {
    private val rule = UseAdaptiveComponents(Config.empty)

    @Test
    fun flagsMaterialWidgetsInAScreen() {
        val code =
            """
            package io.github.solcott.activitytracker.app.feed

            fun FeedUi() {
                TopAppBar(title = { Text("Feed") })
                Button(onClick = {}) { Text("Save") }
                OutlinedTextField(state)
                CircularProgressIndicator()
            }
            """
                .trimIndent()
        assertEquals(4, rule.lint(compileContentForTest(code)).size)
    }

    @Test
    fun allowsTheAdaptiveComponents() {
        val code =
            """
            package io.github.solcott.activitytracker.app.feed

            fun FeedUi() {
                AppTopBar(title = "Feed")
                AppButton(onClick = {}) { Text("Save") }
                AppTextField(state)
                ExtendedFloatingActionButton(onClick = {}, icon = {}, text = {})
            }
            """
                .trimIndent()
        assertEquals(emptyList(), rule.lint(compileContentForTest(code)))
    }

    @Test
    fun allowsMaterialInsideTheAdaptivePackage() {
        val code =
            """
            package io.github.solcott.activitytracker.app.ui.adaptive

            fun AppButton() {
                Button(onClick = {}) {}
            }
            """
                .trimIndent()
        assertEquals(emptyList(), rule.lint(compileContentForTest(code)))
    }
}
