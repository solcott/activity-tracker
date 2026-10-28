import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import org.gradle.testkit.runner.GradleRunner

class KmpLibraryConventionPluginTest {
    private val projectDir: File =
        createTempDirectory("kmp-library").toFile().apply {
            resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "kmp-library-test"
                    dependencyResolutionManagement { repositories { google(); mavenCentral() } }
                    include(":lib")
                    """
                        .trimIndent()
                )
            resolve("gradle").mkdirs()
            File(System.getProperty("activitytracker.versionCatalog")).copyTo(resolve("gradle/libs.versions.toml"))
            resolve("config/detekt").mkdirs()
            resolve("config/detekt/detekt.yml").writeText("")
            addDetektRulesStub()
            resolve("local.properties").writeText("sdk.dir=${androidSdk()}")
            resolve("lib").mkdirs()
            resolve("lib/build.gradle.kts")
                .writeText(
                    """
                    plugins {
                        id("activitytracker.kmp.library")
                        id("com.google.devtools.ksp")
                    }

                    // Stands in for AGP's lint tasks, which can't be realized here: their JDK toolchain is provisioned
                    // by the real build's foojay resolver, and TestKit has its own Gradle home.
                    val lintProbe = tasks.register("lintProbe")
                    tasks.register("printLintOrder") {
                        val after = provider { lintProbe.get().let { t -> t.mustRunAfter.getDependencies(t).map { it.name }.sorted() } }
                        doLast { println("lintRunsAfter=${'$'}{after.get()}") }
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
    fun `lint tasks run after the module's KSP tasks`() {
        val result = runner(":lib:printLintOrder").build()

        val after = result.output.lineSequence().single { it.startsWith("lintRunsAfter=") }
        assertContains(after, "kspAndroidMain")
        assertContains(after, "kspKotlinJvm")
        assertContains(after, "kspKotlinWasmJs")
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*args, "--configuration-cache")

    private fun androidSdk(): String =
        System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: "${System.getProperty("user.home")}/Library/Android/sdk"
}
