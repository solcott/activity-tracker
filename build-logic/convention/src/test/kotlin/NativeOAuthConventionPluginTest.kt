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

                    tasks.register<GenerateNetworkConfig>("generate") {
                        packageName.set("com.example")
                        appviewUrl.set(nativeOAuth.appviewUrl)
                        appviewPublicUrl.set(nativeOAuth.appviewPublicUrl)
                        redirectScheme.set(nativeOAuth.redirectScheme)
                        labelerDid.set(nativeOAuth.labelerDid)
                        plcUrl.set(nativeOAuth.plcUrl)
                        handleResolverUrl.set(nativeOAuth.handleResolverUrl)
                        outputDir.set(layout.buildDirectory.dir("generated"))
                    }
                    """
                        .trimIndent(),
                )
        }

    private val generated: String
        get() = projectDir.resolve("build/generated/com/example/GeneratedNetworkConfig.kt").readText()

    /** Stands in for the Gradle user home (`~/.gradle`), where a developer sets the device host once for every checkout. */
    private val gradleUserHome: File = createTempDirectory("native-oauth-home").toFile()

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
        gradleUserHome.deleteRecursively()
    }

    @Test
    fun `defaults to the local AppView when unset`() {
        generate()

        assertContains(generated, "package com.example")
        assertContains(generated, """APPVIEW_URL = "http://localhost:8081"""")
        assertContains(generated, """APPVIEW_PUBLIC_URL = "http://localhost:8081"""")
        assertContains(generated, """REDIRECT_SCHEME = "localhost"""")
        assertContains(generated, """LABELER_DID = """"")
        assertContains(generated, """PLC_URL = "http://localhost:2582"""")
        assertContains(generated, """HANDLE_RESOLVER_URL = "http://localhost:2583"""")
    }

    @Test
    fun `device host moves PLC and the handle resolver to it and its sibling`() {
        projectDir.resolve("local.properties").writeText("$DEVICE_HOST_PROPERTY=dev.example.com\n")

        generate()

        assertContains(generated, """PLC_URL = "https://plc-dev.example.com"""")
        assertContains(generated, """HANDLE_RESOLVER_URL = "https://dev.example.com"""")
    }

    @Test
    fun `device host moves every client's AppView to its sibling`() {
        projectDir.resolve("local.properties").writeText("$DEVICE_HOST_PROPERTY=dev.example.com\n")

        generate()

        assertContains(generated, """APPVIEW_URL = "https://appview-dev.example.com"""")
        assertContains(generated, """APPVIEW_PUBLIC_URL = "https://appview-dev.example.com"""")
        assertContains(generated, """REDIRECT_SCHEME = "com.example.appview-dev"""")
    }

    @Test
    fun `device host wins over a stale configured AppView URL`() {
        projectDir
            .resolve("local.properties")
            .writeText("$PROPERTY=https://stale-quick.trycloudflare.com\n$DEVICE_HOST_PROPERTY=dev.example.com\n")

        generate()

        assertContains(generated, """APPVIEW_PUBLIC_URL = "https://appview-dev.example.com"""")
    }

    @Test
    fun `device host in the Gradle user home applies to a checkout without one`() {
        gradleUserHome.resolve("gradle.properties").writeText("$DEVICE_HOST_PROPERTY=dev.example.com\n")
        writeLocalProperties("https://stale-quick.trycloudflare.com")

        generate()

        assertContains(generated, """APPVIEW_URL = "https://appview-dev.example.com"""")
        assertContains(generated, """APPVIEW_PUBLIC_URL = "https://appview-dev.example.com"""")
        assertContains(generated, """PLC_URL = "https://plc-dev.example.com"""")
        assertContains(generated, """HANDLE_RESOLVER_URL = "https://dev.example.com"""")
    }

    @Test
    fun `device host gradle property wins and is trimmed`() {
        projectDir.resolve("local.properties").writeText("$DEVICE_HOST_PROPERTY=dev.example.com\n")

        generate("-P$DEVICE_HOST_PROPERTY= https://phone.example.org/ ")

        assertContains(generated, """PLC_URL = "https://plc-phone.example.org"""")
        assertContains(generated, """HANDLE_RESOLVER_URL = "https://phone.example.org"""")
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
        assertContains(generated, """APPVIEW_URL = "http://localhost:8081"""")
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
            // Only the test that writes a user-home gradle.properties pays for a fresh TestKit dir (and daemon).
            .apply { if (gradleUserHome.resolve("gradle.properties").isFile) withTestKitDir(gradleUserHome) }
            .withArguments("generate", "--configuration-cache", *args)
            .build()

    private companion object {
        const val PROPERTY = "activitytracker.appviewPublicUrl"
        const val DEVICE_HOST_PROPERTY = "activitytracker.deviceHost"
    }
}
