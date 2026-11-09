import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class CpdConventionPluginTest {
    private val projectDir: File =
        createTempDirectory("cpd").toFile().apply {
            resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "cpd-test"
                    dependencyResolutionManagement { repositories { mavenCentral() } }
                    """
                        .trimIndent(),
                )
            resolve("gradle").mkdirs()
            resolve("gradle/libs.versions.toml")
                .writeText(
                    """
                    [libraries]
                    pmd-kotlin = { module = "net.sourceforge.pmd:pmd-kotlin", version = "$PMD_VERSION" }
                    """
                        .trimIndent(),
                )
            resolve("build.gradle.kts").writeText("plugins { id(\"activitytracker.cpd\") }\n")
        }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `reports code duplicated across modules without failing`() {
        writeDuplicate("core/a/src/commonMain/kotlin/First.kt")
        writeDuplicate("server/b/src/main/kotlin/Second.kt")

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":cpdCheck")?.outcome)
        val report = projectDir.resolve("build/reports/cpd/cpd.txt").readText()
        assertContains(report, "First.kt")
        assertContains(report, "Second.kt")
    }

    @Test
    fun `test sources and generated sources are left out`() {
        writeDuplicate("core/a/src/commonMain/kotlin/First.kt")
        writeDuplicate("core/a/src/commonTest/kotlin/FirstTest.kt")
        writeDuplicate("core/a/build/generated/ksp/commonMain/kotlin/Generated.kt")

        runner().build()

        val report = projectDir.resolve("build/reports/cpd/cpd.txt").readText()
        assertFalse("FirstTest.kt" in report, report)
        assertFalse("Generated.kt" in report, report)
    }

    @Test
    fun `fails on duplicates when asked to`() {
        writeDuplicate("core/a/src/commonMain/kotlin/First.kt")
        writeDuplicate("core/b/src/commonMain/kotlin/Second.kt")

        val result = runner("-Pcpd.fail=true").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":cpdCheck")?.outcome)
    }

    /** A function long enough to pass the default token threshold, identical in every file it's written to. */
    private fun writeDuplicate(path: String) {
        val body = (1..20).joinToString("\n") { "    val v$it = input * $it + (input - $it) / ($it + 1)" }
        projectDir.resolve(path).apply { parentFile.mkdirs() }.writeText("fun compute(input: Int): Int {\n$body\n    return v20\n}\n")
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("cpdCheck", *arguments, "--configuration-cache")

    private companion object {
        const val PMD_VERSION = "7.29.0"
    }
}
