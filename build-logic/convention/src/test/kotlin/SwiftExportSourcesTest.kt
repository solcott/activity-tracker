import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class SwiftExportSourcesTest {
    private fun violations(code: String) = swiftExportSourceViolations(code.trimIndent())

    @Test
    fun `a Compose import is refused, with its line`() {
        val found =
            violations(
                """
                package x

                import androidx.compose.foundation.text.input.TextFieldState

                data class XState(val name: TextFieldState)
                """,
            )

        assertEquals(listOf(3), found.map { it.line })
        assertContains(found.single().why, ":core-textfield")
    }

    @Test
    fun `a Compose type named in full is refused`() {
        assertEquals(1, violations("data class XState(val colour: androidx.compose.ui.graphics.Color)").size)
    }

    @Test
    fun `the stability annotations are allowed`() {
        val found =
            violations(
                """
                import androidx.compose.runtime.Immutable
                import androidx.compose.runtime.Stable

                @Immutable data class XState(val name: String)
                """,
            )

        assertEquals(emptyList(), found)
    }

    @Test
    fun `another runtime type is refused`() {
        assertEquals(1, violations("import androidx.compose.runtime.compositionLocalOf").size)
        assertEquals(1, violations("import androidx.compose.runtime.ImmutableThing").size)
    }

    @Test
    fun `a property named self is refused`() {
        assertEquals(1, violations("data class PlaceNameFields(val self: String? = null)").size)
        assertEquals(1, violations("    var self = 1").size)
        assertEquals(1, violations("    val `self`: String,").size)
    }

    @Test
    fun `a parameter named self is refused`() {
        assertEquals(1, violations("fun own(proposal: AtUri, self: Did): Boolean = true").size)
        assertEquals(1, violations("fun own(self: Did): Boolean = true").size)
        // A parameter on its own line.
        assertEquals(listOf(2), violations("fun own(\n    self: Did,\n): Boolean = true").map { it.line })
    }

    @Test
    fun `names that only contain self are allowed`() {
        val found =
            violations(
                """
                data class XState(val selfSpace: String, val isSelf: Boolean, val myself: String)
                val space = AthleteSpace.SELF
                fun viewer(x: X) = x.copy(viewerDid = names.self.value)
                """,
            )

        assertEquals(emptyList(), found)
    }

    @Test
    fun `a Pair or Triple type is refused`() {
        assertEquals(1, violations("data class RouteEnds(val start: Pair<Double, Double>?)").size)
        assertEquals(1, violations("data class SaveBatch(val picks: List<Triple<String, Int, Int>>)").size)
    }

    @Test
    fun `comments are skipped`() {
        val found =
            violations(
                """
                // Not androidx.compose.foundation's TextFieldState.
                /** Was a Pair<Double, Double>. */
                /**
                 * The viewer's DID (was `val self: String`).
                 */
                data class XState(val name: String) // no androidx.compose.ui type, no Pair<A, B>
                """,
            )

        assertEquals(emptyList(), found)
    }

    @Test
    fun `a comment after a string that holds two slashes doesn't hide the code before it`() {
        assertEquals(1, violations("""val docs = "https://example.org"; val p: Pair<Int, Int> = 1 to 2 // why""").size)
    }

    private val projectDir: File by lazy {
        createTempDirectory("swift-export-sources").toFile().apply {
            resolve("settings.gradle.kts").writeText("rootProject.name = \"swift-export-sources-test\"\ninclude(\":feature-x-api\")\n")
            resolve("feature-x-api").mkdirs()
            resolve("feature-x-api/build.gradle.kts")
                .writeText(
                    """
                    plugins {
                        base
                        id("activitytracker.swift.contract")
                    }
                    """
                        .trimIndent(),
                )
            resolve("feature-x-api/src/commonMain/kotlin/x")
                .apply { mkdirs() }
                .resolve("XScreen.kt")
                .writeText("package x\n\nimport androidx.compose.runtime.Immutable\n\n@Immutable data class XState(val name: String)\n")
        }
    }

    private fun source(sourceSet: String, name: String, code: String) {
        projectDir.resolve("feature-x-api/src/$sourceSet/kotlin/x").apply { mkdirs() }.resolve(name).writeText(code)
    }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `the task passes clean contracts`() {
        val result = runner(":feature-x-api:$CHECK_SWIFT_EXPORT_SOURCES_TASK_NAME").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-x-api:$CHECK_SWIFT_EXPORT_SOURCES_TASK_NAME")?.outcome)
    }

    @Test
    fun `the task fails naming the file and line of each construct`() {
        source(
            "commonMain",
            "YScreen.kt",
            "package x\n\nimport androidx.compose.foundation.text.input.TextFieldState\n\n" +
                "data class YState(val name: TextFieldState, val self: String, val ends: Pair<Double, Double>)\n",
        )

        val result = runner(":feature-x-api:$CHECK_SWIFT_EXPORT_SOURCES_TASK_NAME").buildAndFail()

        assertContains(result.output, "src/commonMain/kotlin/x/YScreen.kt:3: no Compose type here")
        // One finding a line: the first construct on it.
        assertContains(result.output, "src/commonMain/kotlin/x/YScreen.kt:5: rename `self`")
    }

    @Test
    fun `the task reads every main source set and no test one`() {
        source("iosMain", "X.ios.kt", "package x\n\nfun ends(): Pair<Double, Double> = 0.0 to 0.0\n")
        source("jvmTest", "XTest.kt", "package x\n\nimport androidx.compose.foundation.text.input.TextFieldState\n")

        val result = runner(":feature-x-api:$CHECK_SWIFT_EXPORT_SOURCES_TASK_NAME").buildAndFail()

        assertContains(result.output, "src/iosMain/kotlin/x/X.ios.kt:3: name the values with a data class")
        assertEquals(false, "XTest.kt" in result.output)
    }

    @Test
    fun `check runs the task`() {
        val result = runner(":feature-x-api:check", "--dry-run").build()

        assertContains(result.output, ":feature-x-api:$CHECK_SWIFT_EXPORT_SOURCES_TASK_NAME SKIPPED")
    }

    private fun runner(vararg tasks: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*tasks, "--configuration-cache")
}
