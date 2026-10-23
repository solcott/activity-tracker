import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Adds Compose Multiplatform on top of [KmpLibraryConventionPlugin], including Compose resources (`src/commonMain/composeResources`, read
 * through the generated `Res` in `<namespace>.resources`). A module that renders its UI in tests turns on `composeUiTests { jvm = true }`,
 * which gives `jvmTest` Compose's UI test library and Skia for this OS.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("activitytracker.kmp.library")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            val uiTests = extensions.create<ComposeUiTestsExtension>("composeUiTests").apply { jvm.convention(false) }

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
                val uiTestLibraries = listOf(libs.findLibrary("compose-ui-test").get().get(), ComposePlugin.DesktopDependencies.currentOs)
                configurations.named("jvmTestImplementation") {
                    dependencies.addAllLater(
                        uiTests.jvm.map { on -> if (on) uiTestLibraries.map { project.dependencies.create(it) } else emptyList() }
                    )
                }
            }
            (extensions.getByType<ComposeExtension>() as ExtensionAware).extensions.configure<ResourcesExtension> {
                packageOfResClass = "$derivedNamespace.resources"
            }
        }
}

/** `composeUiTests { }` in a module script. */
interface ComposeUiTestsExtension {
    /** Whether `jvmTest` renders UI: Compose's UI test library, and Skia's native library for the OS the tests run on. */
    val jvm: Property<Boolean>
}
