import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.process.ExecOperations

/** Runs every linter below; `check` runs them too (the Swift ones only on macOS). */
const val LINT_NON_KOTLIN_TASK_NAME = "lintNonKotlin"

/**
 * Linters and formatters for the repo's hand-written code that isn't Kotlin, as ktfmt and detekt are for Kotlin: the iOS app's Swift and
 * the desktop app's macOS helper (swift-format and SwiftLint), shell scripts (ShellCheck and shfmt), the GitHub workflows (actionlint,
 * which also runs ShellCheck on their `run:` steps), the Dockerfiles (Hadolint) and the TOML files, the version catalog among them (Taplo).
 * `format` rewrites what a formatter can, and `check` fails on what's left. Applied to the root project only.
 *
 * Every tool but swift-format, which ships with Xcode, is pinned in `gradle/lint-tools.properties` and fetched once per machine into the
 * Gradle user home ([LintTools]). The Swift tasks run only on macOS, where Xcode is; CI's iOS job runs them, and its Linux `check` the
 * rest. Each config file lives where its tool looks for it: `iosApp/.swift-format`, `iosApp/.swiftlint.yml` (with the baseline beside it),
 * `.shellcheckrc`, `.editorconfig` (shfmt reads its `[*.sh]` section), `.hadolint.yaml` and `.taplo.toml`.
 */
class LintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val root = layout.projectDirectory
            val tools =
                gradle.sharedServices.registerIfAbsent("lintTools", LintTools::class) {
                    parameters.toolsFile.set(root.file("gradle/lint-tools.properties"))
                    parameters.cacheDir.set(
                        providers
                            .gradleProperty("lintTools.cacheDir")
                            .map { root.dir(it) }
                            .orElse(root.dir(gradle.gradleUserHomeDir.resolve("caches/activitytracker-lint-tools").path)),
                    )
                    maxParallelUsages.set(1)
                }
            val macOs = providers.systemProperty("os.name").map { it.startsWith("Mac") }.getOrElse(false)

            // The iOS app and its tests, without what generateIosResources writes, and the desktop app's macOS location helper.
            val swift =
                fileTree(".") {
                    include("iosApp/**/*.swift", "desktopApp/src/macosHelper/**/*.swift")
                    exclude("iosApp/iosApp/Design/Generated/**", "iosApp/build/**")
                }
            val swiftFormatConfig = file("iosApp/.swift-format")
            val swiftLintConfig = file("iosApp/.swiftlint.yml")
            val swiftLintBaseline = file("iosApp/swiftlint-baseline.json")
            // The scripts and the agents' hooks; gradlew is Gradle's own.
            val shell = fileTree(".") { include("scripts/**/*.sh", "docker/scripts/**/*.sh", ".agents/hooks/**/*.sh") }
            val workflows = fileTree(".github/workflows") { include("*.yml", "*.yaml") }
            val dockerfiles = fileTree("docker") { include("*.Dockerfile", "**/Dockerfile") }
            val toml =
                fileTree(".") {
                    include("*.toml", "gradle/*.toml", ".gemini/**/*.toml")
                    exclude("**/build/**")
                }

            fun register(name: String, describe: String, configure: LintToolExec.() -> Unit) =
                tasks.register<LintToolExec>(name) {
                    description = describe
                    this.tools.set(tools)
                    usesService(tools)
                    toolsFile.set(layout.projectDirectory.file("gradle/lint-tools.properties"))
                    workingDir.set(layout.projectDirectory)
                    configure()
                }

            fun lint(name: String, describe: String, configure: LintToolExec.() -> Unit) =
                register(name, describe) {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    stamp.set(layout.buildDirectory.file("lint/$name.stamp"))
                    configure()
                }

            fun format(name: String, describe: String, configure: LintToolExec.() -> Unit) =
                register(name, describe) {
                    group = "formatting"
                    configure()
                }

            val lintSwiftFormat =
                lint("lintSwiftFormat", "Checks the iOS app's Swift against swift-format.") {
                    command.set(listOf("xcrun", "swift-format"))
                    args.set(listOf("lint", "--strict", "--configuration", swiftFormatConfig.path))
                    sources.from(swift)
                    configFiles.from(swiftFormatConfig)
                    onlyIf("swift-format ships with Xcode") { macOs }
                }
            val lintSwiftLint =
                lint("lintSwiftLint", "Checks the iOS app's Swift with SwiftLint, past the findings in its baseline.") {
                    tool.set("swiftlint")
                    args.set(
                        listOf(
                            "lint",
                            "--strict",
                            "--quiet",
                            "--no-cache",
                            "--config",
                            swiftLintConfig.path,
                            "--baseline",
                            swiftLintBaseline.path,
                        ),
                    )
                    sources.from(swift)
                    configFiles.from(swiftLintConfig, swiftLintBaseline)
                    onlyIf("SwiftLint needs the Swift toolchain Xcode brings") { macOs }
                }
            val lintSwift =
                tasks.register("lintSwift") {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Checks the iOS app's Swift (swift-format and SwiftLint)."
                    dependsOn(lintSwiftFormat, lintSwiftLint)
                }
            val formatSwift =
                format("formatSwift", "Formats the iOS app's Swift with swift-format.") {
                    command.set(listOf("xcrun", "swift-format"))
                    args.set(listOf("format", "--in-place", "--configuration", swiftFormatConfig.path))
                    sources.from(swift)
                    configFiles.from(swiftFormatConfig)
                    onlyIf("swift-format ships with Xcode") { macOs }
                }

            val lintShellcheck =
                lint("lintShellcheck", "Checks the shell scripts with ShellCheck.") {
                    tool.set("shellcheck")
                    sources.from(shell)
                    configFiles.from(file(".shellcheckrc"))
                }
            val lintShfmt =
                lint("lintShfmt", "Checks the shell scripts' formatting against shfmt.") {
                    tool.set("shfmt")
                    args.set(listOf("--diff"))
                    sources.from(shell)
                    configFiles.from(file(".editorconfig"))
                }
            val lintShell =
                tasks.register("lintShell") {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Checks the shell scripts (ShellCheck and shfmt)."
                    dependsOn(lintShellcheck, lintShfmt)
                }
            val formatShell =
                format("formatShell", "Formats the shell scripts with shfmt.") {
                    tool.set("shfmt")
                    args.set(listOf("--write"))
                    sources.from(shell)
                    configFiles.from(file(".editorconfig"))
                }

            val lintWorkflows =
                lint("lintWorkflows", "Checks the GitHub workflows with actionlint, and their run: steps with ShellCheck.") {
                    tool.set("actionlint")
                    args.set(listOf("-no-color", "-shellcheck", "{tool:shellcheck}"))
                    sources.from(workflows)
                    configFiles.from(file(".shellcheckrc"), file(".github/actionlint.yaml"))
                }
            val lintDockerfiles =
                lint("lintDockerfiles", "Checks the Dockerfiles with Hadolint.") {
                    tool.set("hadolint")
                    args.set(listOf("--no-color"))
                    sources.from(dockerfiles)
                    configFiles.from(file(".hadolint.yaml"))
                }
            val lintToml =
                lint("lintToml", "Checks the TOML files, the version catalog among them, against Taplo's formatting.") {
                    tool.set("taplo")
                    args.set(listOf("fmt", "--check", "--diff"))
                    sources.from(toml)
                    configFiles.from(file(".taplo.toml"))
                }
            val formatToml =
                format("formatToml", "Formats the TOML files with Taplo.") {
                    tool.set("taplo")
                    args.set(listOf("fmt"))
                    sources.from(toml)
                    configFiles.from(file(".taplo.toml"))
                }

            val all: List<TaskProvider<*>> = listOf(lintSwift, lintShell, lintWorkflows, lintDockerfiles, lintToml)
            tasks.register(LINT_NON_KOTLIN_TASK_NAME) {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description = "Checks the Swift, shell scripts, workflows, Dockerfiles and TOML files."
                dependsOn(all)
            }
            pluginManager.withPlugin("base") { tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(all) } }
            tasks.matching { it.name == FORMAT_TASK_NAME }.configureEach { dependsOn(formatSwift, formatShell, formatToml) }
            // A formatter run in the same build rewrites what its check would read, so the check goes after it.
            listOf(lintSwiftFormat, lintSwiftLint).forEach { it.configure { mustRunAfter(formatSwift) } }
            listOf(lintShellcheck, lintShfmt).forEach { it.configure { mustRunAfter(formatShell) } }
            lintToml.configure { mustRunAfter(formatToml) }
        }
    }
}

/**
 * Runs one linter or formatter over [sources], each passed by its path from [workingDir]. The tool is [tool], a name in
 * `gradle/lint-tools.properties`, or else [command]. An argument `{tool:<name>}` becomes the path of that pinned tool. A task with a
 * [stamp] is a check: it fails on a non-zero exit and is up to date until its sources, configs or tools change.
 */
abstract class LintToolExec : DefaultTask() {
    @get:Input @get:Optional abstract val tool: Property<String>

    @get:Input @get:Optional abstract val command: ListProperty<String>

    @get:Input abstract val args: ListProperty<String>

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Configs the tool reads on its own; a missing one is left out. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val configFiles: ConfigurableFileCollection

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val toolsFile: RegularFileProperty

    @get:Internal abstract val workingDir: DirectoryProperty

    @get:Internal abstract val tools: Property<LintTools>

    @get:OutputFile @get:Optional abstract val stamp: RegularFileProperty

    @get:Inject abstract val exec: ExecOperations

    init {
        args.convention(emptyList())
    }

    @TaskAction
    fun run() {
        val root = workingDir.get().asFile
        val executable = if (tool.isPresent) listOf(tools.get().path(tool.get()).path) else command.get()
        val resolvedArgs = args.get().map { arg -> TOOL_ARG.replace(arg) { tools.get().path(it.groupValues[1]).path } }
        val files = sources.files.map { it.relativeTo(root).path }.sorted()
        val result = exec.exec {
            workingDir = root
            commandLine(executable + resolvedArgs + files)
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            val fix = if (stamp.isPresent) " `./gradlew format` fixes what a formatter can." else ""
            throw GradleException("$name: ${executable.last().substringAfterLast('/')} exited with ${result.exitValue}.$fix")
        }
        stamp.orNull?.asFile?.apply {
            parentFile.mkdirs()
            writeText("ok\n")
        }
    }

    private companion object {
        val TOOL_ARG = Regex("""\{tool:([a-z-]+)}""")
    }
}

/**
 * The pinned tools of `gradle/lint-tools.properties`, each downloaded on first use for this machine's OS and arch, checked against its
 * SHA-256 and unpacked into [Parameters.cacheDir] under its checksum, which every checkout on the machine shares.
 */
abstract class LintTools @Inject constructor(private val archives: ArchiveOperations, private val fs: FileSystemOperations) :
    BuildService<LintTools.Parameters> {
    interface Parameters : BuildServiceParameters {
        val toolsFile: RegularFileProperty
        val cacheDir: DirectoryProperty
    }

    private val properties: Properties by lazy { Properties().apply { parameters.toolsFile.get().asFile.inputStream().use(::load) } }

    @Synchronized
    fun path(tool: String): File {
        val platform = platform()
        fun key(name: String): String? = properties.getProperty("$tool.$platform.$name")
        val url = key("url") ?: throw GradleException("gradle/lint-tools.properties has no $tool for $platform.")
        val sha256 = key("sha256") ?: throw GradleException("gradle/lint-tools.properties has no $tool.$platform.sha256.")
        val entry = key("entry")
        val dir = parameters.cacheDir.get().asFile.resolve("$tool/$sha256")
        val binary = dir.resolve(entry ?: tool)
        if (binary.canExecute()) return binary

        val staging = Files.createTempDirectory(parameters.cacheDir.get().asFile.apply { mkdirs() }.toPath(), "$tool-").toFile()
        try {
            val download = staging.resolve(url.substringAfterLast('/'))
            URI(url).toURL().openStream().use { input -> download.outputStream().use { input.copyTo(it) } }
            val actual = MessageDigest.getInstance("SHA-256").digest(download.readBytes()).joinToString("") { "%02x".format(it) }
            if (actual != sha256) throw GradleException("$url has SHA-256 $actual, not the $sha256 gradle/lint-tools.properties pins.")
            val unpacked = staging.resolve("unpacked")
            when {
                url.endsWith(".zip") -> fs.copy { from(archives.zipTree(download)).into(unpacked) }
                url.endsWith(".tar.gz") -> fs.copy { from(archives.tarTree(archives.gzip(download))).into(unpacked) }
                url.endsWith(".gz") ->
                    unpacked
                        .resolve(tool)
                        .apply { parentFile.mkdirs() }
                        .outputStream()
                        .use { out -> GZIPInputStream(download.inputStream()).use { it.copyTo(out) } }
                else -> download.copyTo(unpacked.resolve(tool))
            }
            unpacked.resolve(entry ?: tool).setExecutable(true)
            dir.parentFile.mkdirs()
            // Another build may have unpacked it meanwhile; either copy is the same file.
            runCatching { Files.move(unpacked.toPath(), dir.toPath(), StandardCopyOption.ATOMIC_MOVE) }
        } finally {
            staging.deleteRecursively()
        }
        if (!binary.canExecute()) throw GradleException("$url unpacked without ${entry ?: tool}.")
        return binary
    }

    private fun platform(): String {
        val os = System.getProperty("os.name")
        val arch = System.getProperty("os.arch")
        val osKey =
            when {
                os.startsWith("Mac") -> "darwin"
                os.startsWith("Linux") -> "linux"
                else -> throw GradleException("The pinned lint tools cover macOS and Linux, not $os.")
            }
        val archKey = if (arch == "aarch64" || arch == "arm64") "arm64" else "x64"
        return "$osKey-$archKey"
    }
}
