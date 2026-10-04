import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import org.gradle.testkit.runner.GradleRunner

class KmpComposeConventionPluginTest {
    private val projectDir: File =
        createTempDirectory("kmp-compose").toFile().apply {
            resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "kmp-compose-test"
                    dependencyResolutionManagement { repositories { google(); mavenCentral() } }
                    include(":feature")
                    """
                        .trimIndent()
                )
            resolve("gradle").mkdirs()
            File(System.getProperty("activitytracker.versionCatalog")).copyTo(resolve("gradle/libs.versions.toml"))
            resolve("config/detekt").mkdirs()
            resolve("config/detekt/detekt.yml").writeText("")
            resolve("config/compose").mkdirs()
            resolve("config/compose/stability.conf").writeText("")
            resolve("local.properties").writeText("sdk.dir=${androidSdk()}")
            resolve("feature").mkdirs()
            resolve("feature/build.gradle.kts")
                .writeText(
                    """
                    import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

                    plugins { id("activitytracker.kmp.compose") }

                    tasks.register("printAndroidResources") {
                        val enabled =
                            (kotlin as ExtensionAware).extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>().androidResources.enable
                        doLast { println("androidResources.enable=${'$'}enabled") }
                    }
                    """
                        .trimIndent()
                )
            resolve("feature/src/commonMain/composeResources/values").mkdirs()
            resolve("feature/src/commonMain/composeResources/values/strings.xml")
                .writeText("""<resources><string name="hello">Hello</string></resources>""")
        }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `Res is generated in the module namespace's resources package`() {
        runner(":feature:generateComposeResClass").build()

        val generated = projectDir.resolve("feature/build/generated/compose/resourceGenerator/kotlin")
        val res = generated.walk().single { it.name == "Res.kt" }
        assertContains(res.readText(), "package io.github.solcott.activitytracker.feature.resources")
    }

    @Test
    fun `Android resources are enabled so Compose resources reach the APK`() {
        val result = runner(":feature:printAndroidResources").build()

        assertContains(result.output, "androidResources.enable=true")
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*args, "--configuration-cache")

    private fun androidSdk(): String =
        System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: "${System.getProperty("user.home")}/Library/Android/sdk"
}
