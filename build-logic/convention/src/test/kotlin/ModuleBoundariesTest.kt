import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class ModuleBoundariesTest {
    @Test
    fun `presenters and UIs never depend on each other`() {
        assertNotNull(boundaryViolation(":feature-search-presenter", ":feature-search-ui"))
        assertNotNull(boundaryViolation(":feature-search-presenter", ":app-common-ui"))
        assertNotNull(boundaryViolation(":feature-search-presenter", ":ui-resources"))
        assertNotNull(boundaryViolation(":app-common-presenter", ":ui-designsystem"))
        assertNotNull(boundaryViolation(":feature-search-ui", ":feature-search-presenter"))
        assertNotNull(boundaryViolation(":feature-search-ui", ":app-common-presenter"))
        assertNotNull(boundaryViolation(":app-common-ui", ":app-presenters"))
    }

    @Test
    fun `features meet only through API modules`() {
        assertNull(boundaryViolation(":feature-search-presenter", ":feature-profile-api"))
        assertNotNull(boundaryViolation(":feature-search-presenter", ":feature-profile-presenter"))
        assertNotNull(boundaryViolation(":feature-search-ui", ":feature-profile-ui"))
        assertNotNull(boundaryViolation(":app-fixtures", ":feature-profile-ui"))
    }

    @Test
    fun `API modules depend only on contracts and core`() {
        assertNull(boundaryViolation(":feature-search-api", ":app-common-api"))
        assertNull(boundaryViolation(":feature-search-api", ":feature-profile-api"))
        assertNull(boundaryViolation(":feature-search-api", ":app-screens"))
        assertNull(boundaryViolation(":app-common-api", ":core-data"))
        assertNotNull(boundaryViolation(":feature-search-api", ":feature-search-presenter"))
        assertNotNull(boundaryViolation(":feature-search-api", ":app-common-ui"))
        assertNotNull(boundaryViolation(":app-common-api", ":app-fixtures"))
    }

    @Test
    fun `a feature's own modules and the shared ones are allowed`() {
        assertNull(boundaryViolation(":feature-search-presenter", ":feature-search-api"))
        assertNull(boundaryViolation(":feature-search-presenter", ":app-common-presenter"))
        assertNull(boundaryViolation(":feature-search-presenter", ":app-common-presenter-testing"))
        assertNull(boundaryViolation(":feature-search-presenter", ":app-screens"))
        assertNull(boundaryViolation(":feature-search-ui", ":feature-search-api"))
        assertNull(boundaryViolation(":feature-search-ui", ":app-common-ui"))
        assertNull(boundaryViolation(":feature-search-ui", ":app-fixtures"))
        assertNull(boundaryViolation(":composeApp", ":feature-search-presenter"))
        assertNull(boundaryViolation(":composeApp", ":feature-search-ui"))
        assertNull(boundaryViolation(":androidApp", ":feature-search-ui"))
    }

    @Test
    fun `feature names may contain hyphens`() {
        assertEquals("gear-list", featureOf(":feature-gear-list-presenter"))
        assertEquals("search", featureOf(":feature-search-api"))
        assertNull(featureOf(":app-common-ui"))
    }

    private val projectDir: File by lazy {
        createTempDirectory("module-boundaries").toFile().apply {
            resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "module-boundaries-test"
                    dependencyResolutionManagement { repositories { mavenCentral() } }
                    include(":feature-x-api", ":feature-x-presenter", ":feature-x-ui")
                    """
                        .trimIndent()
                )
            resolve("gradle").mkdirs()
            resolve("gradle/libs.versions.toml")
                .writeText(
                    """
                    [libraries]
                    compose-rules-detekt = { module = "io.nlopez.compose.rules:detekt", version = "0.5.6" }
                    """
                        .trimIndent()
                )
            resolve("config/detekt").mkdirs()
            resolve("config/detekt/detekt.yml").writeText("")
            addDetektRulesStub()
            module("feature-x-api", dependsOn = emptyList())
            module("feature-x-ui", dependsOn = listOf(":feature-x-api"))
        }
    }

    /** A module with `activitytracker.quality` and a plain `implementation` configuration holding [dependsOn]. */
    private fun File.module(name: String, dependsOn: List<String>) {
        resolve(name).mkdirs()
        resolve("$name/build.gradle.kts")
            .writeText(
                """
                plugins {
                    base
                    id("activitytracker.quality")
                }

                val implementation = configurations.create("implementation")
                dependencies {
                ${dependsOn.joinToString("\n") { "    implementation(project(\"$it\"))" }}
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
    fun `check fails naming both modules when a presenter depends on a UI module`() {
        projectDir.module("feature-x-presenter", dependsOn = listOf(":feature-x-api", ":feature-x-ui"))

        val result = runner(":feature-x-presenter:$CHECK_MODULE_BOUNDARIES_TASK_NAME").buildAndFail()

        assertContains(result.output, ":feature-x-presenter -> :feature-x-ui: presenter modules never depend on UI modules")
    }

    @Test
    fun `check passes allowed dependencies`() {
        projectDir.module("feature-x-presenter", dependsOn = listOf(":feature-x-api"))

        val result =
            runner(":feature-x-presenter:$CHECK_MODULE_BOUNDARIES_TASK_NAME", ":feature-x-ui:$CHECK_MODULE_BOUNDARIES_TASK_NAME").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-x-presenter:$CHECK_MODULE_BOUNDARIES_TASK_NAME")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-x-ui:$CHECK_MODULE_BOUNDARIES_TASK_NAME")?.outcome)
    }

    @Test
    fun `check runs the module boundaries check`() {
        projectDir.module("feature-x-presenter", dependsOn = listOf(":feature-x-api"))

        val result = runner(":feature-x-presenter:check", "--dry-run").build()

        assertContains(result.output, ":feature-x-presenter:$CHECK_MODULE_BOUNDARIES_TASK_NAME SKIPPED")
    }

    private fun runner(vararg tasks: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*tasks, "--configuration-cache")
}
