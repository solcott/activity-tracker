import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

const val COMPOSE_STABILITY_CHECK_TASK_NAME = "composeStabilityCheck"

/**
 * The main compilation that writes the reports the check reads, first match wins: a KMP module's JVM target, a JVM module, the Android
 * app's debug variant, then wasm for a module with no JVM target (`:webApp`), then the iOS simulator for an iOS-only one (`:iosExport`).
 */
private val REPORT_COMPILE_TASKS =
    listOf("compileKotlinJvm", "compileKotlin", "compileDebugKotlin", "compileKotlinWasmJs", "compileKotlinIosSimulatorArm64")

private const val COMPOSE_COMPILER_PLUGIN_ID = "androidx.compose.compiler.plugins.kotlin"

/** `composeStability { }` in a module script. */
abstract class ComposeStabilityExtension {
    /**
     * Also fail on any unstable class, or class field that isn't statically stable (`unstable` or `runtime`, e.g. a `List`), in this
     * module's classes report. For modules that only declare UI-facing types, like the Circuit State contracts in `-api` modules.
     */
    abstract val strictClasses: Property<Boolean>
}

/**
 * Has one main compilation write the Compose compiler's stability reports, points the compiler at the repo's stability config, and fails
 * `check` when a restartable composable has a param that isn't statically stable (plus the [ComposeStabilityExtension.strictClasses] check
 * where enabled). Applied by `activitytracker.quality` to every module that applies the Compose compiler plugin.
 */
internal fun Project.configureComposeStability() {
    val extension = extensions.create<ComposeStabilityExtension>("composeStability").apply { strictClasses.convention(false) }
    val reportsDir = layout.buildDirectory.dir("compose/stability")
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
        // `isolated` is incubating (Isolated Projects); re-check on Gradle upgrades.
        @Suppress("UnstableApiUsage")
        stabilityConfigurationFiles.add(isolated.rootProject.projectDirectory.file("config/compose/stability.conf"))
    }

    // Not the extension's reportsDestination: that has every compilation of every target write the same file names into one
    // directory, as a side effect the compile tasks don't declare, so a compilation restored from the build cache leaves no
    // reports and the check passes without reading any. Here the one compilation the check reads gets the option, with the
    // directory as its output: cached and restored with its classes, and written by nothing else.
    val taskNames = provider { tasks.names }
    val reportTask = taskNames.map { names -> REPORT_COMPILE_TASKS.firstOrNull { it in names } ?: error("$path has no main compilation") }
    tasks
        .withType<BaseKotlinCompile>()
        .named { it in REPORT_COMPILE_TASKS }
        .configureEach {
            if (name == reportTask.get()) {
                val reports = FilesSubpluginOption("reportsDestination", listOf(reportsDir.get().asFile))
                pluginOptions.add(CompilerPluginConfig().apply { addPluginArgument(COMPOSE_COMPILER_PLUGIN_ID, reports) })
                outputs.dir(reportsDir).withPropertyName("composeStabilityReports")
            }
        }

    val stabilityCheck =
        tasks.register<ComposeStabilityCheck>(COMPOSE_STABILITY_CHECK_TASK_NAME) {
            group = JavaBasePlugin.VERIFICATION_GROUP
            description = "Fails on unstable classes or unstable restartable-composable params in the Compose compiler reports."
            reports.from(reportsDir.map { dir -> dir.asFileTree.matching { include("*-classes.txt", "*-composables.txt") } })
            // By name: a filtered KotlinCompilationTask collection would also match the wasm executable links, the metadata
            // compilations and the iosArm64 one, none of which check otherwise needs.
            dependsOn(reportTask.map { listOf(it) })
            strictClasses.set(extension.strictClasses)
            marker.set(layout.buildDirectory.file("compose/stabilityCheck.txt"))
        }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(stabilityCheck) }
}

@CacheableTask
abstract class ComposeStabilityCheck : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:IgnoreEmptyDirectories
    abstract val reports: ConfigurableFileCollection

    @get:Input abstract val strictClasses: Property<Boolean>

    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val violations =
            reports.files
                .sortedBy { it.name }
                .flatMap { file ->
                    val text = file.readText()
                    when {
                        !file.name.endsWith("-classes.txt") -> ComposeStabilityReport.unstableParams(text)
                        strictClasses.get() -> ComposeStabilityReport.unstableClasses(text)
                        else -> emptyList()
                    }.map { "${file.name}: $it" }
                }
                // The same finding can appear in more than one report (a main class used by the test compilation).
                .distinctBy { it.substringAfter(": ") }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Compose stability check failed (${violations.size}):")
                    violations.forEach { appendLine("  $it") }
                    append(
                        "Annotate the type @Immutable or @Stable, use ImmutableList instead of List, or (for types we don't own) " +
                            "add it to config/compose/stability.conf. Reports: build/compose/stability.",
                    )
                },
            )
        }
        marker.get().asFile.writeText("OK\n")
    }
}

/** Parses the Compose compiler's `*-classes.txt` and `*-composables.txt` reports. */
internal object ComposeStabilityReport {
    private val functionHeader = Regex("""^(.*?)\bfun (\S+?)\((.*)$""")
    // A param with no stability prefix (e.g. an interface type) is of unknown stability, which is no better than unstable.
    private val param = Regex("""^\s+(?:unused )?(?:(stable|unstable|runtime) )?(\S+): (.+?)(?: = .*)?$""")
    private val classHeader = Regex("""^(\w+) class (\S+) \{""")
    private val field = Regex("""^\s+(unstable|runtime) (?:val|var) (\S+): (.+)$""")

    /**
     * Unstable classes, and fields that aren't statically stable. kotlinx-serialization's generated `$serializer`s are never Compose
     * params, so they're skipped. A field typed by the class's own type parameter is `runtime` by design and also skipped.
     */
    fun unstableClasses(text: String): List<String> {
        val found = mutableListOf<String>()
        var current: String? = null
        for (line in text.lineSequence()) {
            val header = classHeader.find(line)
            when {
                header != null -> {
                    val (stability, name) = header.destructured
                    current = name.takeUnless { it.endsWith("\$serializer") }
                    if (current != null && stability == "unstable") found += "unstable class $current"
                }
                line == "}" -> current = null
                current != null -> {
                    val (stability, name, type) = field.find(line)?.destructured ?: continue
                    if (!type.matches(Regex("""[A-Z]\??"""))) found += "class $current: $stability val $name: $type"
                }
            }
        }
        return found
    }

    /**
     * Params of restartable composables that aren't statically stable. Non-restartable ones (presenters and other value-returning
     * composables, inline functions) never skip, so their params don't matter.
     */
    fun unstableParams(text: String): List<String> {
        val found = mutableListOf<String>()
        var current: String? = null
        for (line in text.lineSequence()) {
            val header = functionHeader.find(line)
            when {
                header != null -> {
                    val modifiers = header.groupValues[1].split(' ')
                    current = header.groupValues[2].takeIf { "restartable" in modifiers && "inline" !in modifiers }
                    if (header.groupValues[3].trim().endsWith(")")) current = null
                }
                line.trim() == ")" -> current = null
                current != null -> {
                    val (stability, name, type) = param.find(line)?.destructured ?: continue
                    if (stability != "stable") found += "fun $current: ${stability.ifEmpty { "unknown" }} param $name: $type"
                }
            }
        }
        return found
    }
}
