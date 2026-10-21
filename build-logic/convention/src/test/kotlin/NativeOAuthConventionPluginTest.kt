import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class NativeOAuthConventionPluginTest {
    private val projectDir: File =
        createTempDirectory("native-oauth").toFile().apply {
            resolve("settings.gradle.kts").writeText("""rootProject.name = "native-oauth-test"""")
            resolve("build.gradle.kts")
                .writeText(
                    """
                    plugins { id("activitytracker.native.oauth") }

                    tasks.register<GenerateNativeNetworkConfig>("generate") {
                        packageName.set("com.example")
                        appviewPublicUrl.set(nativeOAuth.appviewPublicUrl)
                        redirectScheme.set(nativeOAuth.redirectScheme)
                        labelerDid.set(nativeOAuth.labelerDid)
                        outputDir.set(layout.buildDirectory.dir("generated"))
                    }
                    """
                        .trimIndent()
                )
        }

    private val generated: String
        get() = projectDir.resolve("build/generated/com/example/GeneratedNativeNetworkConfig.kt").readText()

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `defaults to the local AppView when unset`() {
        generate()

        assertContains(generated, "package com.example")
        assertContains(generated, """APPVIEW_PUBLIC_URL = "http://localhost:8081"""")
        assertContains(generated, """REDIRECT_SCHEME = "localhost"""")
        assertContains(generated, """LABELER_DID = """"")
    }

    @Test
    fun `labeler DID comes from local properties`() {
        projectDir.resolve("local.properties").writeText("activitytracker.labelerDid=did:plc:labeler\n")

        generate()

        assertContains(generated, """LABELER_DID = "did:plc:labeler"""")
    }

    @Test
    fun `gradle property is trimmed and reversed into the redirect scheme`() {
        generate("-P$PROPERTY= https://fifty-club.trycloudflare.com/ ")

        assertContains(generated, """APPVIEW_PUBLIC_URL = "https://fifty-club.trycloudflare.com"""")
        assertContains(generated, """REDIRECT_SCHEME = "com.trycloudflare.fifty-club"""")
    }

    @Test
    fun `falls back to local properties`() {
        writeLocalProperties("https://appview.example.org:8443")

        generate()

        assertContains(generated, """APPVIEW_PUBLIC_URL = "https://appview.example.org:8443"""")
        assertContains(generated, """REDIRECT_SCHEME = "org.example.appview"""")
    }

    @Test
    fun `gradle property wins over local properties`() {
        writeLocalProperties("https://from-file.example.org")

        generate("-P$PROPERTY=https://from-flag.example.org")

        assertContains(generated, """APPVIEW_PUBLIC_URL = "https://from-flag.example.org"""")
    }

    @Test
    fun `local properties is read lazily so editing it reuses the configuration cache`() {
        writeLocalProperties("https://one.example.org")
        assertContains(generate().output, "Configuration cache entry stored")

        val unchanged = generate()
        assertContains(unchanged.output, "Configuration cache entry reused")
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":generate")?.outcome)

        writeLocalProperties("https://two.example.org")
        val edited = generate()
        assertContains(edited.output, "Configuration cache entry reused")
        assertEquals(TaskOutcome.SUCCESS, edited.task(":generate")?.outcome)
        assertContains(generated, """APPVIEW_PUBLIC_URL = "https://two.example.org"""")
    }

    private fun writeLocalProperties(url: String) {
        projectDir.resolve("local.properties").writeText("$PROPERTY=$url\n")
    }

    private fun generate(vararg args: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generate", "--configuration-cache", *args)
            .build()

    private companion object {
        const val PROPERTY = "activitytracker.appviewPublicUrl"
    }
}
