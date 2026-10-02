import com.ncorti.ktfmt.gradle.KtfmtExtension
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/** Lifecycle task that runs every formatter applied to a project; `./gradlew format` formats the whole repo. */
const val FORMAT_TASK_NAME = "format"

/**
 * ktfmt in the repo's style, plus the [FORMAT_TASK_NAME] lifecycle task. build-logic's own build script repeats it, since it can't apply
 * its own plugin.
 */
class KtfmtConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.ncorti.ktfmt.gradle")
            extensions.configure<KtfmtExtension> {
                kotlinLangStyle()
                maxWidth.set(140)
            }
            tasks.register(FORMAT_TASK_NAME) {
                group = "formatting"
                description = "Formats this project's sources and build scripts."
                dependsOn(tasks.withType<KtfmtFormatTask>())
            }
        }
    }
}
