import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import org.gradle.testkit.runner.GradleRunner

class AndroidApplicationConventionPluginTest {
    private val projectDir: File =
        createTempDirectory("android-app").toFile().apply {
            resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "android-app-test"
                    dependencyResolutionManagement { repositories { google(); mavenCentral() } }
                    include(":app")
                    """
                        .trimIndent()
                )
            resolve("gradle").mkdirs()
            File(System.getProperty("activitytracker.versionCatalog")).copyTo(resolve("gradle/libs.versions.toml"))
            resolve("config/detekt").mkdirs()
            resolve("config/detekt/detekt.yml").writeText("")
            addDetektRulesStub()
            resolve("local.properties").writeText("sdk.dir=${androidSdk()}")
            resolve("app").mkdirs()
            resolve("app/build.gradle.kts")
                .writeText(
                    """
                    plugins { id("activitytracker.android.application") }

                    android { namespace = "com.example.app" }

                    androidComponents {
                        onVariants { variant -> println("${'$'}{variant.name}.isMinifyEnabled=${'$'}{variant.isMinifyEnabled}") }
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
    fun `R8 minifies release builds but not debug ones`() {
        val result = GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(":app:help").build()

        assertContains(result.output, "release.isMinifyEnabled=true")
        assertContains(result.output, "debug.isMinifyEnabled=false")
    }

    private fun androidSdk(): String =
        System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: "${System.getProperty("user.home")}/Library/Android/sdk"
}
