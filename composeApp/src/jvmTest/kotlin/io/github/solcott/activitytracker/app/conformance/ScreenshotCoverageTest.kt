package io.github.solcott.activitytracker.app.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every screen and sub-screen UI has a screenshot file in `:androidApp`'s screenshot suite (dev/screenshot-tests). */
class ScreenshotCoverageTest {
    @Test
    fun everyUiHasScreenshots() {
        val uis =
            File("src/commonMain/kotlin")
                .walk()
                .filter { it.name.endsWith("Ui.kt") }
                .filter { file -> file.readText().let { "@CircuitInject(" in it || "@SubCircuitInject(" in it } }
        val screenshots = File(SCREENSHOTS).walk().map { it.name }.toSet()
        val missing = uis.map { it.name.removeSuffix("Ui.kt") }.filter { "${it}Screenshots.kt" !in screenshots }.sorted().toList()
        assertTrue(uis.any(), "no UIs found under ${File("src/commonMain/kotlin").absolutePath}")
        assertTrue(missing.isEmpty(), "UIs without an XScreenshots.kt in $SCREENSHOTS: $missing")
    }

    private companion object {
        const val SCREENSHOTS = "../androidApp/src/screenshotTest/kotlin"
    }
}
