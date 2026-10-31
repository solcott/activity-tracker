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
 * phase sees it too). `activitytracker.deviceHost` (device mode, `scripts/dev.sh`) moves PLC and the handle resolver to public hosts too.
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
        target.extensions.create(
            "nativeOAuth",
            NativeOAuthExtension::class.java,
            property(PROPERTY),
            property(LABELER_PROPERTY),
            property(DEVICE_HOST_PROPERTY),
        )
    }

    private companion object {
        const val PROPERTY = "activitytracker.appviewPublicUrl"

        /** The app's labeler DID, which `scripts/dev.sh` writes for the local Ozone. Every client reads it, not only native ones. */
        const val LABELER_PROPERTY = "activitytracker.labelerDid"

        /** The local PDS's public host in device mode, e.g. `dev.example.com`; unset means the localhost stack. */
        const val DEVICE_HOST_PROPERTY = "activitytracker.deviceHost"
    }
}

open class NativeOAuthExtension(
    /** The URL as configured; absent when unset. */
    val configuredAppviewPublicUrl: Provider<String>,
    configuredLabelerDid: Provider<String>,
    configuredDeviceHost: Provider<String>,
) {
    /** The labeler DID as configured; empty when unset, and then clients can't report. */
    val labelerDid: Provider<String> = configuredLabelerDid.orElse("")

    val appviewPublicUrl: Provider<String> = configuredAppviewPublicUrl.orElse("http://localhost:8081")

    private val deviceHost: Provider<String> = configuredDeviceHost.map { it.removePrefix("https://") }

    /** The PDS, which also resolves handles: the device host itself, else the local stack's. */
    val handleResolverUrl: Provider<String> = deviceHost.map { "https://$it" }.orElse("http://localhost:2583")

    /** PLC on the device host's sibling (`dev.example.com` → `plc-dev.example.com`, see `scripts/dev.sh`), else the local stack's. */
    val plcUrl: Provider<String> = deviceHost.map { "https://${sibling("plc", it)}" }.orElse("http://localhost:2582")

    /** Reverse-domain of the URL's host, the redirect scheme ATProto OAuth requires (matches the AppView's `OAUTH_REDIRECT_SCHEME`). */
    val redirectScheme: Provider<String> = appviewPublicUrl.map { url ->
        url.substringAfter("://").substringBefore('/').substringBefore(':').split('.').reversed().joinToString(".")
    }
}

/**
 * Prefixes the device host's first label, staying one level below the zone so Cloudflare's free certificate covers it. `scripts/dev.sh`
 * derives the same names.
 */
internal fun sibling(service: String, deviceHost: String): String = "$service-$deviceHost"

/**
 * Writes `GeneratedNativeNetworkConfig` with the native AppView URL, OAuth redirect scheme, labeler DID (empty when unset), and the PLC and
 * handle-resolver URLs.
 */
abstract class GenerateNativeNetworkConfig : DefaultTask() {
    init {
        group = "build"
        description = "Generates GeneratedNativeNetworkConfig from activitytracker.appviewPublicUrl."
        labelerDid.convention("")
        plcUrl.convention("http://localhost:2582")
        handleResolverUrl.convention("http://localhost:2583")
    }

    @get:Input abstract val packageName: Property<String>

    @get:Input abstract val appviewPublicUrl: Property<String>

    @get:Input abstract val redirectScheme: Property<String>

    @get:Input abstract val labelerDid: Property<String>

    @get:Input abstract val plcUrl: Property<String>

    @get:Input abstract val handleResolverUrl: Property<String>

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
                |/** Generated from `local.properties`; see `NativeOAuthConventionPlugin`. */
                |internal object GeneratedNativeNetworkConfig {
                |    const val APPVIEW_PUBLIC_URL = "${appviewPublicUrl.get()}"
                |    const val REDIRECT_SCHEME = "${redirectScheme.get()}"
                |    const val LABELER_DID = "${labelerDid.get()}"
                |    const val PLC_URL = "${plcUrl.get()}"
                |    const val HANDLE_RESOLVER_URL = "${handleResolverUrl.get()}"
                |}
                |"""
                    .trimMargin()
            )
    }
}
