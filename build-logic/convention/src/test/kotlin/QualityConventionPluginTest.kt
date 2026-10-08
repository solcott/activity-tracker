import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class QualityConventionPluginTest {
    private val projectDir: File =
        createTempDirectory("quality").toFile().apply {
            resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "quality-test"
                    dependencyResolutionManagement { repositories { mavenCentral() } }
                    """
                        .trimIndent()
                )
            resolve("gradle").mkdirs()
            resolve("gradle/libs.versions.toml")
                .writeText(
                    """
                    [libraries]
                    compose-rules-detekt = { module = "io.nlopez.compose.rules:detekt", version = "$COMPOSE_RULES_VERSION" }
                    """
                        .trimIndent()
                )
            resolve("config/detekt").mkdirs()
            resolve("config/detekt/detekt.yml").writeText("")
            addDetektRulesStub()
            // A generated dir registered as a source root, the way KSP adds its output to each source set.
            resolve("build.gradle.kts")
                .writeText(
                    """
                    plugins {
                        kotlin("jvm")
                        id("activitytracker.quality")
                    }

                    kotlin { sourceSets.main { kotlin.srcDir("build/generated/ksp/main/kotlin") } }
                    """
                        .trimIndent()
                )
        }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `generated sources under the build dir are not analyzed`() {
        writeViolation("build/generated/ksp/main/kotlin/Generated.kt")
        projectDir.resolve("src/main/kotlin/Clean.kt").apply { parentFile.mkdirs() }.writeText("fun clean() = Unit\n")

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":$TASK")?.outcome)
    }

    @Test
    fun `hand-written sources are still analyzed`() {
        writeViolation("build/generated/ksp/main/kotlin/Generated.kt")
        writeViolation("src/main/kotlin/HandWritten.kt")

        val result = runner().buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":$TASK")?.outcome)
        assertContains(result.output, "HandWritten.kt")
        assertContains(result.output, "MagicNumber")
        assertFalse("Generated.kt" in result.output, result.output)
    }

    @Test
    fun `check runs the type-resolution tasks`() {
        val result = runner("check", "--dry-run").build()

        assertContains(result.output, ":detektMain SKIPPED")
        assertContains(result.output, ":detektTest SKIPPED")
    }

    private fun writeViolation(path: String) {
        val file = projectDir.resolve(path).apply { parentFile.mkdirs() }
        val name = file.nameWithoutExtension
        file.writeText("fun $name(seconds: Long): Long = seconds * 37\n")
    }

    private fun runner(vararg tasks: String = arrayOf(TASK)): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*tasks, "--configuration-cache")

    private companion object {
        const val COMPOSE_RULES_VERSION = "0.6.7"
        const val TASK = "detektMainSourceSet"
    }
}
