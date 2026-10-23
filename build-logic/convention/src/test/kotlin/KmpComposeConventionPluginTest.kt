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
            addDetektRulesStub()
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

    @Test
    fun `jvmTest gets no UI test libraries unless the module asks`() {
        printUiTestDependencies(enabled = null)
        val result = runner(":feature:printUiTestDependencies").build()

        assertContains(result.output, "uiTestDependencies=[]")
    }

    @Test
    fun `composeUiTests gives jvmTest Compose's UI test library and Skia for this OS`() {
        printUiTestDependencies(enabled = true)
        val result = runner(":feature:printUiTestDependencies").build()

        assertContains(result.output, "org.jetbrains.compose.ui:ui-test")
        assertContains(result.output, "org.jetbrains.compose.desktop:desktop-jvm-")
    }

    /** Adds a task to the module printing the UI test libraries on `jvmTestImplementation`, with `composeUiTests { jvm = [enabled] }`. */
    private fun printUiTestDependencies(enabled: Boolean?) {
        val script = projectDir.resolve("feature/build.gradle.kts")
        script.appendText(
            """

            ${enabled?.let { "composeUiTests { jvm = $it }" }.orEmpty()}

            tasks.register("printUiTestDependencies") {
                val deps =
                    configurations.getByName("jvmTestImplementation").dependencies
                        .map { "${'$'}{it.group}:${'$'}{it.name}" }
                        .filter { it.startsWith("org.jetbrains.compose.ui:ui-test") || it.startsWith("org.jetbrains.compose.desktop") }
                doLast { println("uiTestDependencies=${'$'}deps") }
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
