import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Adds Compose Multiplatform on top of [KmpLibraryConventionPlugin], including Compose resources (`src/commonMain/composeResources`, read
 * through the generated `Res` in `<namespace>.resources`).
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("activitytracker.kmp.library")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<KotlinMultiplatformExtension> {
                // AGP's KMP library target packages no Android resources unless asked, and Compose resources ride on them.
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    androidResources { enable = true }
                }
                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.findLibrary("compose-runtime").get())
                    implementation(libs.findLibrary("compose-foundation").get())
                    implementation(libs.findLibrary("compose-ui").get())
                    implementation(libs.findLibrary("compose-material3").get())
                    implementation(libs.findLibrary("compose-components-resources").get())
                }
            }
            (extensions.getByType<ComposeExtension>() as ExtensionAware).extensions.configure<ResourcesExtension> {
                packageOfResClass = "$derivedNamespace.resources"
            }
        }
}
