import com.squareup.sort.SortDependenciesTask
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/** Formatting and static analysis for every module: ktfmt, sort-dependencies, and detekt with the Compose rules. */
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
        }
    }
}
