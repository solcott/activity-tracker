import java.io.StringReader
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Single source for the AppView's public URL, which native OAuth uses as its client-metadata host (it must be HTTPS, e.g. a cloudflared
 * tunnel in dev). Set `activitytracker.appviewPublicUrl` as a Gradle property or in the root `local.properties` (read so Xcode's build
 * phase sees it too).
 */
class NativeOAuthConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // `isolated` is incubating (Isolated Projects); it reads the root dir without touching the root Project. Re-check on upgrades.
        @Suppress("UnstableApiUsage")
        val localProperties =
            target.providers.fileContents(target.isolated.rootProject.projectDirectory.file("local.properties")).asText.map { text ->
                Properties().apply { load(StringReader(text)) }
            }
        fun property(name: String): Provider<String> =
            target.providers
                .gradleProperty(name)
                .orElse(localProperties.map { it.getProperty(name).orEmpty() })
                .map { it.trim().trimEnd('/') }
                .filter { it.isNotEmpty() }
        target.extensions.create("nativeOAuth", NativeOAuthExtension::class.java, property(PROPERTY), property(LABELER_PROPERTY))
    }

    private companion object {
        const val PROPERTY = "activitytracker.appviewPublicUrl"

        /** The app's labeler DID, which `scripts/dev.sh` writes for the local Ozone. Every client reads it, not only native ones. */
        const val LABELER_PROPERTY = "activitytracker.labelerDid"
    }
}

open class NativeOAuthExtension(
    /** The URL as configured; absent when unset. */
    val configuredAppviewPublicUrl: Provider<String>,
    configuredLabelerDid: Provider<String>,
) {
    /** The labeler DID as configured; empty when unset, and then clients can't report. */
    val labelerDid: Provider<String> = configuredLabelerDid.orElse("")

    val appviewPublicUrl: Provider<String> = configuredAppviewPublicUrl.orElse("http://localhost:8081")

    /** Reverse-domain of the URL's host, the redirect scheme ATProto OAuth requires (matches the AppView's `OAUTH_REDIRECT_SCHEME`). */
    val redirectScheme: Provider<String> = appviewPublicUrl.map { url ->
        url.substringAfter("://").substringBefore('/').substringBefore(':').split('.').reversed().joinToString(".")
    }
}

/** Writes `GeneratedNativeNetworkConfig` with the native AppView URL, OAuth redirect scheme and labeler DID (empty when unset). */
abstract class GenerateNativeNetworkConfig : DefaultTask() {
    init {
        group = "build"
        description = "Generates GeneratedNativeNetworkConfig from activitytracker.appviewPublicUrl."
        labelerDid.convention("")
    }

    @get:Input abstract val packageName: Property<String>

    @get:Input abstract val appviewPublicUrl: Property<String>

    @get:Input abstract val redirectScheme: Property<String>

    @get:Input abstract val labelerDid: Property<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = packageName.get()
        val dir = outputDir.get().asFile.resolve(pkg.replace('.', '/'))
        dir.deleteRecursively()
        dir.mkdirs()
        dir.resolve("GeneratedNativeNetworkConfig.kt")
            .writeText(
                """
                |package $pkg
                |
                |/** Generated from `activitytracker.appviewPublicUrl`; see `NativeOAuthConventionPlugin`. */
                |internal object GeneratedNativeNetworkConfig {
                |    const val APPVIEW_PUBLIC_URL = "${appviewPublicUrl.get()}"
                |    const val REDIRECT_SCHEME = "${redirectScheme.get()}"
                |    const val LABELER_DID = "${labelerDid.get()}"
                |}
                |"""
                    .trimMargin()
            )
    }
}
