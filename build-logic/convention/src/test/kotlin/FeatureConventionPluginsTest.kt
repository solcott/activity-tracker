import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import org.gradle.testkit.runner.GradleRunner

class FeatureConventionPluginsTest {
    private val projectDir: File =
        createTempDirectory("feature").toFile().apply {
            // The shared modules only need to exist for the conventions' project dependencies; they're never built here.
            resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "feature-test"
                    dependencyResolutionManagement { repositories { google(); mavenCentral() } }
                    include(
                        ":feature-x-api",
                        ":feature-x-presenter",
                        ":feature-x-ui",
                        ":app-common-api",
                        ":app-common-presenter",
                        ":app-common-presenter-testing",
                        ":app-common-ui",
                        ":app-fixtures",
                        ":ui-designsystem",
                        ":ui-resources",
                    )
                    """
                        .trimIndent()
                )
            resolve("gradle").mkdirs()
            File(System.getProperty("activitytracker.versionCatalog")).copyTo(resolve("gradle/libs.versions.toml"))
            resolve("config/detekt").mkdirs()
            resolve("config/detekt/detekt.yml").writeText("")
            addDetektRulesStub()
            resolve("config/compose").mkdirs()
            resolve("config/compose/stability.conf").writeText("")
            resolve("local.properties").writeText("sdk.dir=${androidSdk()}")
            listOf(
                    APP_COMMON_API,
                    APP_COMMON_PRESENTER,
                    APP_COMMON_PRESENTER_TESTING,
                    APP_COMMON_UI,
                    APP_FIXTURES,
                    UI_DESIGNSYSTEM,
                    UI_RESOURCES,
                )
                .forEach { resolve(it.removePrefix(":")).mkdirs() }
            feature("api")
            feature("presenter")
            feature("ui")
        }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `api modules expose the shared contracts and hold States to strict stability`() {
        val result = runner(":feature-x-api:printFeatureSetup").build()

        assertContains(result.output, "commonMainApi=[:app-common-api]")
        assertContains(result.output, "strictClasses=true")
    }

    @Test
    fun `presenter modules get their own api module, the presenter helpers and the test fixtures`() {
        val result = runner(":feature-x-presenter:printFeatureSetup").build()

        assertContains(result.output, "commonMainApi=[:feature-x-api]")
        assertContains(result.output, "commonMainImplementation=[:app-common-presenter]")
        assertContains(result.output, "jvmTestImplementation=[:app-common-presenter-testing]")
    }

    @Test
    fun `ui modules get their own api module, the design system and the samples`() {
        val result = runner(":feature-x-ui:printFeatureSetup").build()

        assertContains(result.output, "commonMainImplementation=[:app-common-ui, :feature-x-api, :ui-designsystem, :ui-resources]")
        assertContains(result.output, "jvmTestImplementation=[:app-fixtures]")
        assertContains(result.output, "org.jetbrains.compose.ui:ui-test")
    }

    /** `feature-x-<kind>` applying `activitytracker.feature.<kind>`, with a task printing the project dependencies it was given. */
    private fun File.feature(kind: String) {
        resolve("feature-x-$kind").mkdirs()
        resolve("feature-x-$kind/build.gradle.kts")
            .writeText(
                """
                plugins { id("activitytracker.feature.$kind") }

                tasks.register("printFeatureSetup") {
                    val lines =
                        listOf("commonMainApi", "commonMainImplementation", "jvmTestImplementation").map { name ->
                            val deps = configurations.getByName(name).dependencies
                            val projects = deps.withType<ProjectDependency>().map { it.path }.sorted()
                            "${'$'}name=${'$'}projects " + deps.map { "${'$'}{it.group}:${'$'}{it.name}" }.joinToString()
                        }
                    val strict = project.extensions.findByType<ComposeStabilityExtension>()?.strictClasses?.orNull
                    doLast {
                        lines.forEach(::println)
                        println("strictClasses=${'$'}strict")
                    }
                }
                """
                    .trimIndent()
            )
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*args, "--configuration-cache")

    private fun androidSdk(): String =
        System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: "${System.getProperty("user.home")}/Library/Android/sdk"
}
