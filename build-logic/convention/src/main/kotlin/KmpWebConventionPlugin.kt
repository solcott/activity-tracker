import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Browser-only wasmJs modules (the web app shell and its SQLite worker), which don't need the full [KmpLibraryConventionPlugin] targets.
 */
class KmpWebConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("activitytracker.quality")

            extensions.configure<KotlinMultiplatformExtension> {
                // wasmJs is still @ExperimentalWasmDsl in KGP; re-check on Kotlin upgrades.
                @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }
            }
        }
    }
}
