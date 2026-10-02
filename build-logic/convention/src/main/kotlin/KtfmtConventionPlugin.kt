import com.ncorti.ktfmt.gradle.KtfmtExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** ktfmt in the repo's style. build-logic's own build script repeats it, since it can't apply its own plugin. */
class KtfmtConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.ncorti.ktfmt.gradle")
            extensions.configure<KtfmtExtension> {
                kotlinLangStyle()
                maxWidth.set(140)
            }
        }
    }
}
