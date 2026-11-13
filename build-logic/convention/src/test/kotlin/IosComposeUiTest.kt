import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner

class IosComposeUiTest {
    private val projectDir: File =
        createTempDirectory("ios-compose-ui").toFile().apply {
            resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "ios-compose-ui-test"
                    dependencyResolutionManagement { repositories { google(); mavenCentral() } }
                    include(":export")
                    """
                        .trimIndent(),
                )
            resolve("gradle").mkdirs()
            File(System.getProperty("activitytracker.versionCatalog")).copyTo(resolve("gradle/libs.versions.toml"))
            resolve("export").mkdirs()
        }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `UI libraries and Circuit's UI half are forbidden on iOS, the runtime pieces are not`() {
        assertTrue(isForbiddenOnIos("org.jetbrains.compose.ui", "ui"))
        assertTrue(isForbiddenOnIos("org.jetbrains.compose.material3.adaptive", "adaptive"))
        assertTrue(isForbiddenOnIos("org.jetbrains.skiko", "skiko"))
        assertTrue(isForbiddenOnIos("com.slack.circuit", "circuit-foundation"))
        assertTrue(isForbiddenOnIos("io.github.vinceglb", "filekit-dialogs-compose"))

        assertFalse(isForbiddenOnIos("org.jetbrains.compose.runtime", "runtime"))
        assertFalse(isForbiddenOnIos("androidx.compose.runtime", "runtime-saveable"))
        assertFalse(isForbiddenOnIos("com.slack.circuit", "circuit-runtime-presenter"))
        assertFalse(isForbiddenOnIos("com.slack.circuit", "circuitx-subcircuit"))
        assertFalse(isForbiddenOnIos("io.github.vinceglb", "filekit-core"))
        // A group that only starts with a forbidden one's name.
        assertFalse(isForbiddenOnIos("org.jetbrains.compose.uikit", "x"))
    }

    @Test
    fun `the check passes when iOS resolves no UI library`() {
        exportScript("""implementation(libs.compose.runtime)""")

        val result = runner(":export:checkIosDependencies").build()

        assertContains(result.output, "BUILD SUCCESSFUL")
    }

    @Test
    fun `the check fails naming a UI library iOS resolves and what brought it`() {
        exportScript("""implementation(libs.vico.multiplatform)""")

        val result = runner(":export:checkIosDependencies").buildAndFail()

        assertContains(result.output, "iosSimulatorArm64CompileKlibraries: com.patrykandpatrick.vico:multiplatform (:export)")
        assertContains(result.output, "iosArm64CompileKlibraries: com.patrykandpatrick.vico:multiplatform (:export)")
    }

    @Test
    fun `SubCircuit reaches iOS without the Compose UI it publishes`() {
        exportScript("""implementation(libs.circuitx.subcircuit)""")

        val result =
            runner(":export:checkIosDependencies", ":export:dependencies", "--configuration", "iosSimulatorArm64CompileKlibraries").build()

        assertContains(result.output, "com.slack.circuit:circuitx-subcircuit")
        assertFalse("org.jetbrains.compose.ui:ui" in result.output, result.output)
        assertFalse("org.jetbrains.compose.foundation" in result.output, result.output)
    }

    @Test
    fun `the check is part of check`() {
        exportScript("")

        val result = runner(":export:check", "--dry-run").build()

        assertContains(result.output, ":export:checkIosDependencies")
    }

    private fun exportScript(iosDependency: String) {
        projectDir
            .resolve("export/build.gradle.kts")
            .writeText(
                """
                plugins {
                    kotlin("multiplatform")
                    id("activitytracker.swift.export")
                }

                kotlin {
                    iosArm64()
                    iosSimulatorArm64()
                    sourceSets { iosMain.dependencies { $iosDependency } }
                }
                """
                    .trimIndent(),
            )
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*args, "--configuration-cache")
}
