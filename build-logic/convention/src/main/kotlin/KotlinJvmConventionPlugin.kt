import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Kotlin/JVM modules (the AppView): the Amazon JDK toolchain, quality checks and the repo-wide opt-ins. */
class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("activitytracker.quality")

            extensions.configure<KotlinJvmProjectExtension> {
                amazonJdkToolchain(target)
                compilerOptions { freeCompilerArgs.addAll(OPT_IN_EXPERIMENTAL_TIME) }
            }
        }
    }
}
