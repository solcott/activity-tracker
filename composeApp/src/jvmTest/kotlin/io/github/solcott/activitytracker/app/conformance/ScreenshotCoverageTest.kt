package io.github.solcott.activitytracker.app.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every screen and sub-screen UI, in this module or a feature's UI module, has a screenshot file in `:androidApp`'s screenshot suite
 * (dev/screenshot-tests, dev/module-boundaries).
 */
class ScreenshotCoverageTest {
    @Test
    fun everyUiHasScreenshots() {
        val roots = listOf(File("src/commonMain/kotlin")) + featureUiSources()
        val uis =
            roots
                .asSequence()
                .flatMap { it.walk() }
                .filter { it.name.endsWith("Ui.kt") }
                .filter { file -> file.readText().let { "@CircuitInject(" in it || "@SubCircuitInject(" in it } }
        val screenshots = File(SCREENSHOTS).walk().map { it.name }.toSet()
        val missing = uis.map { it.name.removeSuffix("Ui.kt") }.filter { "${it}Screenshots.kt" !in screenshots }.sorted().toList()
        assertTrue(uis.any(), "no UIs found under ${roots.map { it.absolutePath }}")
        assertTrue(missing.isEmpty(), "UIs without an XScreenshots.kt in $SCREENSHOTS: $missing")
    }

    /** `feature/<name>/ui/src/commonMain/kotlin` for every feature. */
    private fun featureUiSources(): List<File> =
        File(FEATURES).listFiles().orEmpty().map { it.resolve("ui/src/commonMain/kotlin") }.filter { it.isDirectory }

    private companion object {
        const val SCREENSHOTS = "../androidApp/src/screenshotTest/kotlin"
        const val FEATURES = "../feature"
    }
}
