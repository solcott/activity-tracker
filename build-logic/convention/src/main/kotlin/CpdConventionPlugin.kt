import de.aaschmid.gradle.plugins.cpd.Cpd
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * PMD's copy-paste detector over every module's hand-written Kotlin, as the root `cpdCheck` task. One task over the whole repo finds
 * duplication across modules, which a per-module one can't. Report-only until `-Pcpd.fail=true`; `-Pcpd.minTokens` sets the smallest
 * duplicate reported. Applied to the root project only.
 *
 * Uses the cpd plugin's task type without applying the plugin: its `apply` walks `allprojects` for Java source sets (KMP modules have none)
 * and wires the task into `check`.
 */
class CpdConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val pmdClasspath = configurations.create("cpd") { isVisible = false }
            dependencies.add(pmdClasspath.name, libs.findLibrary("pmd-kotlin").get())
            val fail = providers.gradleProperty("cpd.fail").map(String::toBoolean).getOrElse(false)

            tasks.register<Cpd>("cpdCheck") {
                group = "verification"
                description = "Reports Kotlin code duplicated across the repo's modules (PMD CPD)."
                this.pmdClasspath = pmdClasspath
                language = "kotlin"
                minimumTokenCount = providers.gradleProperty("cpd.minTokens").map(String::toInt).getOrElse(DEFAULT_MIN_TOKENS)
                encoding = Charsets.UTF_8.name()
                failOnError = true
                failOnViolation = fail
                ignoreFailures = !fail
                skipBlocks = false
                skipBlocksPattern = ""
                // Main source sets only (commonMain, jvmMain, …, and src/main of the JVM modules); tests repeat setup on purpose.
                source(
                    fileTree(layout.projectDirectory) {
                        include("**/src/*Main/kotlin/**/*.kt", "**/src/main/kotlin/**/*.kt")
                        // Generated sources, and other checkouts (agent worktrees) nested in this one.
                        exclude("**/build/**", ".claude/**", ".agents/**", ".gradle/**", "**/node_modules/**")
                    }
                )
                val reportsDir = layout.buildDirectory.dir("reports/cpd")
                // Every report needs a location, required or not; the plugin's apply would otherwise set them.
                reports {
                    all { outputLocation.set(reportsDir.map { it.file("cpd.$name") }) }
                    text.required.set(true)
                    text.outputLocation.set(reportsDir.map { it.file("cpd.txt") })
                    xml.required.set(true)
                    csv.required.set(false)
                    vs.required.set(false)
                }
            }
        }
    }
}

private const val DEFAULT_MIN_TOKENS = 100
