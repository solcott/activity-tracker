import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

internal const val OPT_IN_EXPERIMENTAL_TIME = "-opt-in=kotlin.time.ExperimentalTime"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

/** `:core-atproto` -> `io.github.solcott.activitytracker.core.atproto` */
internal val Project.derivedNamespace: String
    get() = "io.github.solcott.activitytracker" + path.replace(':', '.').replace('-', '.')

/**
 * Compiles with the catalog's `jdk` version from Amazon (Corretto); foojay provisions it if missing. Bytecode targets are set separately.
 */
internal fun KotlinBaseExtension.amazonJdkToolchain(project: Project) = jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(project.libs.version("jdk").toInt()))
    vendor.set(JvmVendorSpec.AMAZON)
}
