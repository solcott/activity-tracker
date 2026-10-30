import com.squareup.sort.SortDependenciesTask
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Formatting and static analysis for every module: ktfmt, sort-dependencies, detekt with the Compose rules, the module-boundaries check,
 * and (in modules that apply the Compose compiler) the Compose stability check.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("activitytracker.ktfmt")
            pluginManager.apply("com.squareup.sort-dependencies")
            tasks.named(FORMAT_TASK_NAME) { dependsOn(tasks.withType<SortDependenciesTask>()) }

            pluginManager.apply("dev.detekt")
            extensions.configure<DetektExtension> {
                // Plain (no type resolution) analysis of every source set; the default only covers
                // src/main and src/test, which KMP modules don't have.
                source.setFrom(fileTree("src") { include("**/*.kt") })
                buildUponDefaultConfig.set(true)
                // `isolated` is incubating (Isolated Projects); re-check on Gradle upgrades.
                @Suppress("UnstableApiUsage") config.setFrom(isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"))
                baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
                parallel.set(true)
            }
            dependencies.add("detektPlugins", libs.findLibrary("compose-rules-detekt").get())
            // The repo's own rules (UseTextFieldState). Not in that module itself, which detekt then analyzes with only the stock rules.
            if (path != DETEKT_RULES_PATH) dependencies.add("detektPlugins", project(DETEKT_RULES_PATH))

            // The per-source-set and type-resolution tasks analyze every Kotlin source dir, which
            // includes generated ones (KSP output). A pattern can't match those: each generated dir is
            // itself a source root, so excluded paths are matched on the absolute file instead.
            val buildDir = layout.buildDirectory
            val generated = Spec<FileTreeElement> { it.file.startsWith(buildDir.get().asFile) }
            tasks.withType<Detekt>().configureEach { exclude(generated) }
            tasks.withType<DetektCreateBaselineTask>().configureEach { exclude(generated) }

            val detektAll =
                tasks.register("detektAll") {
                    group = "verification"
                    description = "Runs every detekt task in this module, with and without type resolution."
                    dependsOn(tasks.withType<Detekt>().matching { it.name != "detektDevJvm" })
                }
            // Detekt only wires the plain task into check; the type-resolution ones find what it can't (`!!`, unused properties).
            plugins.withType<LifecycleBasePlugin> { tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(detektAll) } }

            pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") { configureComposeStability() }
            configureModuleBoundaries()
        }
    }
}

private const val DETEKT_RULES_PATH = ":detekt-rules"
