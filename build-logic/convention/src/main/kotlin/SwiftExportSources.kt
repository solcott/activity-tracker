import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

const val CHECK_SWIFT_EXPORT_SOURCES_TASK_NAME = "checkSwiftExportSources"

/** One construct Swift export can't carry, on [line] (1-based) of a source file. */
internal data class SwiftExportSourceViolation(val line: Int, val why: String, val code: String)

/**
 * The lines of a Kotlin source file, [text], that hold a construct Swift export rejects (dev/ios-swift-interop):
 * - a Compose type other than the two stability annotations: exported, Compose's generated bridge doesn't compile, and hidden, every
 *   declaration naming one is a stub that stops the app;
 * - a property or parameter named `self`, which the generated bridge can't tell from its own receiver;
 * - a `Pair` or `Triple` type, whose bridge reads its arguments as `Any?` and doesn't compile inside a collection.
 *
 * A text scan, since each is one token: it runs on Linux, where nothing compiles the iOS targets. Comment lines are skipped.
 */
internal fun swiftExportSourceViolations(text: String): List<SwiftExportSourceViolation> =
    text.lines().mapIndexedNotNull { index, line ->
        val code = line.trim()
        if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
        val scanned = code.withoutTrailingComment()
        FORBIDDEN_IN_SWIFT_EXPORT.firstOrNull { (pattern, _) -> pattern.containsMatchIn(scanned) }
            ?.let { (_, why) -> SwiftExportSourceViolation(index + 1, why, code) }
    }

/** Drops a `// comment` after code, unless the `//` could be inside a string literal (a URL). */
private fun String.withoutTrailingComment(): String {
    val at = indexOf(" //")
    return if (at >= 0 && substring(0, at).count { it == '"' } % 2 == 0) substring(0, at) else this
}

private val FORBIDDEN_IN_SWIFT_EXPORT =
    listOf(
        // runtime's Immutable and Stable are annotations, which no signature carries.
        Regex("""\bandroidx\.compose\.(?!runtime\.(Immutable|Stable)\b)""") to
            "no Compose type here but @Immutable and @Stable: a text field's state is :core-textfield's TextFieldState, and anything " +
                "else Compose belongs in a presenter or UI module",
        Regex("""\b(?:val|var)\s+`?self`?\b|(?:^|[(,])\s*`?self`?\s*:""") to
            "rename `self`: the Swift bridge of a declaration with a property or parameter of that name doesn't compile",
        Regex("""\b(?:Pair|Triple)\s*<""") to "name the values with a data class: Swift export can't bridge a Pair or Triple",
    )

/**
 * `activitytracker.swift.contract`: for a module the iOS app exports to Swift whole (the screen contracts and `:iosShell`). Registers
 * [CHECK_SWIFT_EXPORT_SOURCES_TASK_NAME] over every main source set and wires it into `check`, and keeps SubCircuit's Compose UI
 * dependencies out of its iOS configurations ([excludeComposeUiOnIos]).
 */
class SwiftExportContractConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            excludeComposeUiOnIos()
            val check =
                tasks.register<CheckSwiftExportSources>(CHECK_SWIFT_EXPORT_SOURCES_TASK_NAME) {
                    group = JavaBasePlugin.VERIFICATION_GROUP
                    description = "Fails when this module's sources hold a construct Swift export can't carry (dev/ios-swift-interop)."
                    sources.from(fileTree("src") { include("*Main/**/*.kt") })
                    sourceRoot.set(layout.projectDirectory.asFile.absolutePath)
                    marker.set(layout.buildDirectory.file("swiftExport/sources.txt"))
                }
            plugins.withType(LifecycleBasePlugin::class.java) { tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(check) } }
        }
    }
}

@CacheableTask
abstract class CheckSwiftExportSources : DefaultTask() {
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The module's directory, which failures name files relative to. Not an input: moving the checkout changes no result. */
    @get:Internal abstract val sourceRoot: Property<String>

    /** Written only on success, so the task is up to date until a source changes. */
    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val root = File(sourceRoot.get())
        val violations =
            sources.files
                .sortedBy { it.path }
                .flatMap { file ->
                    swiftExportSourceViolations(file.readText()).map {
                        "${file.relativeTo(root).path}:${it.line}: ${it.why}\n    ${it.code}"
                    }
                }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Swift export can't carry these, and this module is exported to the iOS app whole (dev/ios-swift-interop):\n" +
                    violations.joinToString("\n") { "  $it" },
            )
        }
        marker.get().asFile.writeText("ok\n")
    }
}
