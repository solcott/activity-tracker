import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature's contracts (`feature/<name>/api`, `:feature-<name>-api`): its Screens, States and Events, which its presenter and UI modules
 * both depend on and other features navigate to. States are what UIs take as params, so every class must be statically stable.
 */
class FeatureApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("activitytracker.kmp.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("dev.zacsweers.metro")
            extensions.configure<ComposeStabilityExtension> { strictClasses.set(true) }
            extensions.configure<KotlinMultiplatformExtension> {
                optInSubCircuit()
                sourceSets.getByName("commonMain").dependencies {
                    api(project(APP_COMMON_API))
                    api(libs.findLibrary("circuit-runtime").get())
                    api(libs.findLibrary("circuit-serialization").get())
                    api(libs.findLibrary("circuitx-subcircuit").get())
                    // Only for TextFieldState: a State holds its text fields' state, which the presenter owns.
                    api(libs.findLibrary("compose-foundation").get())
                    api(libs.findLibrary("dataresult-uistate").get())
                    api(libs.findLibrary("kotlinx-collections-immutable").get())
                    implementation(libs.findLibrary("compose-runtime").get())
                }
            }
        }
}

/**
 * A feature's presenters (`:feature-<name>-presenter`): Compose runtime and no UI. Depends on its own `-api` module and the shared
 * presenter helpers; its tests get the shared presenter test fixtures.
 */
class FeaturePresenterConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("activitytracker.kmp.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("dev.zacsweers.metro")
            extensions.configure<KotlinMultiplatformExtension> {
                optInSubCircuit()
                sourceSets.getByName("commonMain").dependencies {
                    api(project(siblingApiPath(PRESENTER_SUFFIX)))
                    implementation(project(APP_COMMON_PRESENTER))
                    implementation(libs.findLibrary("circuit-codegen-annotations").get())
                    implementation(libs.findLibrary("circuit-retained").get())
                    implementation(libs.findLibrary("circuit-runtime").get())
                    implementation(libs.findLibrary("circuit-runtime-presenter").get())
                    implementation(libs.findLibrary("compose-runtime").get())
                    implementation(libs.findLibrary("compose-runtime-saveable").get())
                    implementation(libs.findLibrary("dataresult-uistate-circuit").get())
                }
                sourceSets.getByName("jvmTest").dependencies {
                    implementation(project(APP_COMMON_PRESENTER_TESTING))
                    implementation(libs.findLibrary("circuit-test").get())
                    implementation(libs.findLibrary("circuitx-subcircuit-test").get())
                    implementation(libs.findLibrary("ktor-client-mock").get())
                }
            }
        }
}

/**
 * A feature's UIs (`:feature-<name>-ui`): Compose UI built from the design system and the shared widgets, against its own `-api` module.
 * `jvmTest` renders UIs from `:app-fixtures`' samples.
 */
class FeatureUiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("activitytracker.kmp.compose")
            pluginManager.apply("dev.zacsweers.metro")
            extensions.configure<ComposeUiTestsExtension> { jvm.set(true) }
            extensions.configure<KotlinMultiplatformExtension> {
                optInSubCircuit()
                sourceSets.getByName("commonMain").dependencies {
                    implementation(project(siblingApiPath(UI_SUFFIX)))
                    implementation(project(APP_COMMON_UI))
                    implementation(project(UI_DESIGNSYSTEM))
                    implementation(project(UI_RESOURCES))
                    implementation(libs.findLibrary("circuit-codegen-annotations").get())
                    implementation(libs.findLibrary("circuit-foundation").get())
                    implementation(libs.findLibrary("circuit-runtime").get())
                    implementation(libs.findLibrary("circuitx-subcircuit").get())
                }
                sourceSets.getByName("jvmTest").dependencies { implementation(project(APP_FIXTURES)) }
            }
        }
}

// SubCircuit is experimental. Opted in module-wide so Metro's generated sub-factories compile too.
private fun KotlinMultiplatformExtension.optInSubCircuit() = compilerOptions {
    optIn.add("com.slack.circuit.subcircuit.ExperimentalSubCircuitApi")
}

/** `:feature-search-presenter` -> `:feature-search-api`. */
private fun Project.siblingApiPath(suffix: String): String {
    require(path.endsWith(suffix)) { "$path applies a feature $suffix convention, so its path must end in $suffix" }
    return path.removeSuffix(suffix) + API_SUFFIX
}
