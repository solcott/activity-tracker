import de.aaschmid.gradle.plugins.cpd.Cpd
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.kotlin.dsl.register

/**
 * PMD's copy-paste detector over the repo's hand-written code, as the root `cpdCheck` task: `cpdKotlin` over every module's Kotlin and
 * `cpdSwift` over the iOS app's Swift, one task per language since CPD reads one language at a time. One task over the whole repo finds
 * duplication across modules, which a per-module one can't. Report-only until `-Pcpd.fail=true`; `-Pcpd.minTokens` sets the smallest
 * duplicate reported. Applied to the root project only.
 *
 * Uses the cpd plugin's task type without applying the plugin: its `apply` walks `allprojects` for Java source sets (KMP modules have none)
 * and wires the task into `check`.
 */
class CpdConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val pmdClasspath = configurations.create("cpd") { isCanBeConsumed = false }
            dependencies.add(pmdClasspath.name, libs.findLibrary("pmd-kotlin").get())
            dependencies.add(pmdClasspath.name, libs.findLibrary("pmd-swift").get())

            // Main source sets only (commonMain, jvmMain, …, and src/main of the JVM modules); tests repeat setup on purpose.
            val kotlin =
                registerCpd(pmdClasspath, taskName = "cpdKotlin", language = "kotlin", report = "cpd") {
                    include("**/src/*Main/kotlin/**/*.kt", "**/src/main/kotlin/**/*.kt")
                    // Generated sources, and other checkouts (agent worktrees) nested in this one.
                    exclude("**/build/**", ".claude/**", ".agents/**", ".gradle/**", "**/node_modules/**")
                }
            // The iOS app's own Swift, without its tests or what generateIosResources writes.
            val swift =
                registerCpd(pmdClasspath, taskName = "cpdSwift", language = "swift", report = "cpd-swift") {
                    include("iosApp/iosApp/**/*.swift")
                    exclude("iosApp/iosApp/Design/Generated/**")
                }
            tasks.register("cpdCheck") {
                group = "verification"
                description = "Reports Kotlin and Swift code duplicated across the repo (PMD CPD)."
                dependsOn(kotlin, swift)
            }
        }
    }

    private fun Project.registerCpd(
        pmdClasspath: Configuration,
        taskName: String,
        language: String,
        report: String,
        sources: ConfigurableFileTree.() -> Unit,
    ) =
        tasks.register<Cpd>(taskName) {
            val fail = providers.gradleProperty("cpd.fail").map(String::toBoolean).getOrElse(false)
            group = "verification"
            description = "Reports $language code duplicated across the repo (PMD CPD)."
            this.pmdClasspath = pmdClasspath
            this.language = language
            minimumTokenCount = providers.gradleProperty("cpd.minTokens").map(String::toInt).getOrElse(DEFAULT_MIN_TOKENS)
            encoding = Charsets.UTF_8.name()
            failOnError = true
            failOnViolation = fail
            ignoreFailures = !fail
            skipBlocks = false
            skipBlocksPattern = ""
            source(fileTree(layout.projectDirectory, sources))
            val reportsDir = layout.buildDirectory.dir("reports/cpd")
            // Every report needs a location, required or not; the plugin's apply would otherwise set them.
            reports {
                all { outputLocation.set(reportsDir.map { it.file("$report.$name") }) }
                text.required.set(true)
                text.outputLocation.set(reportsDir.map { it.file("$report.txt") })
                xml.required.set(true)
                xml.outputLocation.set(reportsDir.map { it.file("$report.xml") })
                csv.required.set(false)
                vs.required.set(false)
            }
        }
}

private const val DEFAULT_MIN_TOKENS = 100
