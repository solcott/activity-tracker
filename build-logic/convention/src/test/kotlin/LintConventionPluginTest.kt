import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class LintConventionPluginTest {
    private val projectDir: File =
        createTempDirectory("lint").toFile().apply {
            resolve("settings.gradle.kts").writeText("rootProject.name = \"lint-test\"\n")
            resolve("build.gradle.kts")
                .writeText(
                    """
                    plugins {
                        base
                        id("activitytracker.lint")
                    }
                    // The ktfmt convention's, which the lint convention adds its formatters to.
                    tasks.register("format")
                    """
                        .trimIndent(),
                )
            resolve("gradle").mkdirs()
            File(System.getProperty("activitytracker.lintTools")).copyTo(resolve("gradle/lint-tools.properties"))
            resolve(".editorconfig").writeText("root = true\n\n[*]\nindent_style = space\n\n[*.sh]\nindent_size = 2\n")
        }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `check runs the linters and format the formatters`() {
        val check = runner("check", "--dry-run").build().output
        listOf(":lintShellcheck", ":lintShfmt", ":lintWorkflows", ":lintDockerfiles", ":lintToml", ":lintSwiftFormat", ":lintSwiftLint")
            .forEach { assertContains(check, it) }

        val format = runner("format", "--dry-run").build().output
        listOf(":formatSwift", ":formatShell", ":formatToml").forEach { assertContains(format, it) }
    }

    @Test
    fun `a clean script passes and stays up to date`() {
        writeScript("scripts/ok.sh", "#!/usr/bin/env bash\nset -eu\necho \"\$1\"\n")

        assertEquals(TaskOutcome.SUCCESS, runner("lintShell").build().task(":lintShellcheck")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, runner("lintShell").build().task(":lintShellcheck")?.outcome)
    }

    @Test
    fun `ShellCheck fails naming the file and line`() {
        writeScript("scripts/bad.sh", "#!/usr/bin/env bash\nset -eu\nname=\$1\nrm -rf \$name/build\n")

        val result = runner("lintShellcheck").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":lintShellcheck")?.outcome)
        assertContains(result.output, "scripts/bad.sh line 4")
        assertContains(result.output, "SC2086")
    }

    @Test
    fun `format rewrites a script shfmt would change`() {
        writeScript("scripts/messy.sh", "#!/usr/bin/env bash\nif true; then\n        echo hi\nfi\n")

        assertEquals(TaskOutcome.FAILED, runner("lintShfmt").buildAndFail().task(":lintShfmt")?.outcome)
        runner("formatShell").build()

        assertEquals("#!/usr/bin/env bash\nif true; then\n  echo hi\nfi\n", projectDir.resolve("scripts/messy.sh").readText())
        assertEquals(TaskOutcome.SUCCESS, runner("lintShfmt").build().task(":lintShfmt")?.outcome)
    }

    @Test
    fun `a linter with nothing to read is skipped`() {
        val result = runner("lintDockerfiles").build()

        assertEquals(TaskOutcome.NO_SOURCE, result.task(":lintDockerfiles")?.outcome)
    }

    @Test
    fun `the Swift linters run only on macOS`() {
        projectDir.resolve("iosApp/iosApp/App.swift").apply { parentFile.mkdirs() }.writeText("let answer = 42\n")
        projectDir.resolve("iosApp/.swift-format").writeText("{ \"version\": 1, \"indentation\": { \"spaces\": 4 } }\n")

        val outcome = runner("lintSwiftFormat").build().task(":lintSwiftFormat")?.outcome
        val macOs = System.getProperty("os.name").startsWith("Mac")
        assertTrue(if (macOs) outcome == TaskOutcome.SUCCESS else outcome == TaskOutcome.SKIPPED, "$outcome")
    }

    private fun writeScript(path: String, text: String) {
        projectDir.resolve(path).apply { parentFile.mkdirs() }.writeText(text)
    }

    /** The tools are cached where the real build keeps them, so each test doesn't fetch them again into TestKit's own Gradle home. */
    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                *arguments,
                "--configuration-cache",
                "-PlintTools.cacheDir=${System.getProperty("activitytracker.lintToolsCache")}",
            )
}
