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
        @Suppress("UnstableApiUsage")
        val localProperties =
            target.providers.fileContents(target.isolated.rootProject.projectDirectory.file("local.properties")).asText.map { text ->
                Properties().apply { load(StringReader(text)) }
            }
        @Suppress("UnstableApiUsage")
        val configured =
            target.providers
                .gradleProperty(PROPERTY)
                .orElse(localProperties.map { it.getProperty(PROPERTY).orEmpty() })
                .map { it.trim().trimEnd('/') }
                .filter { it.isNotEmpty() }
        target.extensions.create("nativeOAuth", NativeOAuthExtension::class.java, configured)
    }

    private companion object {
        const val PROPERTY = "activitytracker.appviewPublicUrl"
    }
}

open class NativeOAuthExtension(
    /** The URL as configured; absent when unset. */
    val configuredAppviewPublicUrl: Provider<String>
) {
    val appviewPublicUrl: Provider<String> = configuredAppviewPublicUrl.orElse("http://localhost:8081")

    /** Reverse-domain of the URL's host, the redirect scheme ATProto OAuth requires (matches the AppView's `OAUTH_REDIRECT_SCHEME`). */
    val redirectScheme: Provider<String> = appviewPublicUrl.map { url ->
        url.substringAfter("://").substringBefore('/').substringBefore(':').split('.').reversed().joinToString(".")
    }
}

/** Writes `GeneratedNativeNetworkConfig` with the native AppView URL and OAuth redirect scheme. */
abstract class GenerateNativeNetworkConfig : DefaultTask() {
    init {
        group = "build"
        description = "Generates GeneratedNativeNetworkConfig from activitytracker.appviewPublicUrl."
    }

    @get:Input abstract val packageName: Property<String>

    @get:Input abstract val appviewPublicUrl: Property<String>

    @get:Input abstract val redirectScheme: Property<String>

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
                |}
                |"""
                    .trimMargin()
            )
    }
}
