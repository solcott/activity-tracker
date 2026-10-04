import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class ComposeStabilityReportTest {
    @Test
    fun `flags unstable and unknown params of restartable composables only`() {
        val report =
            """
            restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun app.Feed(
              unstable items: List<Item>
              graph: Graph
              stable modifier: Modifier? = @static <expression>
            )
            restartable skippable fun app.Empty()
            fun app.FeedPresenter(
              unstable repo: Repo
            )
            restartable inline fun app.Wrapper(
              unstable content: Content
            )
            restartable skippable fun app.Row(
              unused unstable item: Item
              runtime value: T
            )
            """
                .trimIndent()

        assertEquals(
            listOf(
                "fun app.Feed: unstable param items: List<Item>",
                "fun app.Feed: unknown param graph: Graph",
                "fun app.Row: unstable param item: Item",
                "fun app.Row: runtime param value: T",
            ),
            ComposeStabilityReport.unstableParams(report),
        )
    }

    @Test
    fun `flags unstable classes and fields that aren't statically stable`() {
        val report =
            """
            stable class app.FeedState {
              runtime val items: List<Item>
              unstable val author: Author
              stable val error: String?
            }
            unstable class app.Event.Open {
              unstable val item: Item
            }
            stable class app.Box {
              runtime val value: T
              <runtime stability> = Parameter(T)
            }
            stable class app.Screen.${'$'}serializer {
              runtime val descriptor: SerialDescriptor
            }
            """
                .trimIndent()

        assertEquals(
            listOf(
                "class app.FeedState: runtime val items: List<Item>",
                "class app.FeedState: unstable val author: Author",
                "unstable class app.Event.Open",
                "class app.Event.Open: unstable val item: Item",
            ),
            ComposeStabilityReport.unstableClasses(report),
        )
    }
}

class ComposeStabilityCheckTest {
    private val projectDir: File =
        createTempDirectory("compose-stability").toFile().apply {
            resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "compose-stability-test"
                    dependencyResolutionManagement { repositories { google(); mavenCentral() } }
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
            resolve("config/compose").mkdirs()
            resolve("config/compose/stability.conf").writeText("")
            resolve("build.gradle.kts")
                .writeText(
                    """
                    plugins {
                        kotlin("jvm")
                        id("org.jetbrains.kotlin.plugin.compose")
                        id("activitytracker.quality")
                    }

                    dependencies {
                        implementation("org.jetbrains.compose.runtime:runtime:$COMPOSE_VERSION")
                        implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$IMMUTABLE_VERSION")
                    }
                    """
                        .trimIndent()
                )
        }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `an unstable composable param fails the check`() {
        writeComposable("items: List<Item>")

        val result = runner().buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":$TASK")?.outcome)
        assertContains(result.output, "fun Feed: ")
        assertContains(result.output, "param items: List<Item>")
    }

    @Test
    fun `stable composable params pass the check`() {
        writeComposable("items: kotlinx.collections.immutable.ImmutableList<Item>")

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":$TASK")?.outcome)
        assertFalse("unstable param" in result.output, result.output)
    }

    private fun writeComposable(params: String) {
        projectDir
            .resolve("src/main/kotlin/Feed.kt")
            .apply { parentFile.mkdirs() }
            .writeText(
                """
                import androidx.compose.runtime.Composable

                data class Item(val name: String)

                @Composable fun Feed($params) { items.forEach { Row(it) } }

                @Composable fun Row(item: Item) = Unit
                """
                    .trimIndent()
            )
    }

    private fun runner(): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(TASK, "--configuration-cache")

    private companion object {
        const val COMPOSE_RULES_VERSION = "0.6.7"
        const val COMPOSE_VERSION = "1.12.1"
        const val IMMUTABLE_VERSION = "0.5.2"
        const val TASK = COMPOSE_STABILITY_CHECK_TASK_NAME
    }
}
