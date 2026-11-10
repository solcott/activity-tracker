import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

const val CHECK_SWIFT_EXPORT_STUBS_TASK_NAME = "checkSwiftExportStubs"

/**
 * A declaration Swift export generated with a body that stops the app: [declaration] is its Swift name with the types around it
 * (`…login.LoginScreen.Companion.serializer`), [member] the last part of that, and [line] where its `fatalError()` is (1-based).
 */
internal data class SwiftStub(val line: Int, val declaration: String, val member: String)

/** What [swiftExportStubs] read in one generated file. */
internal data class SwiftStubScan(
    val stubs: List<SwiftStub>,
    /** Private initialisers with a stubbed body: every exported Kotlin object has one, and it is never called. */
    val objectInitialisers: Int,
    /** Lines calling `fatalError()` in a shape this scan doesn't know, so it can't say what they are. */
    val unrecognised: List<Int>,
)

/**
 * The stubs in one generated Swift file, [swift] (dev/ios-swift-interop). Swift export writes `fatalError()` as the whole body of a
 * function, initialiser or accessor whose signature names a type from a module the export hides, and says nothing: the app stops when Swift
 * calls it. The same call is legitimate in three places, which are not stubs: an exported object's private initialiser, the `default:` of
 * the switches generated for an enum, and the continuation of a suspend function that returns `Nothing`. A declaration Swift marks
 * unavailable is left out too: nothing can call it.
 *
 * It reads the file by indentation, which the generator keeps to four spaces a level.
 */
internal fun swiftExportStubs(swift: String): SwiftStubScan {
    val lines = swift.lines()
    val stubs = mutableListOf<SwiftStub>()
    val unrecognised = mutableListOf<Int>()
    var objectInitialisers = 0
    lines.forEachIndexed { index, line ->
        if (!line.contains(FATAL_ERROR)) return@forEachIndexed
        val code = line.trim()
        when {
            code.startsWith("//") || ENUM_DEFAULT.matches(code) || NEVER_CONTINUATION.containsMatchIn(code) -> Unit
            code != FATAL_ERROR -> unrecognised += index + 1
            else -> {
                val header = lines.declarationAround(index)
                when {
                    header == null -> unrecognised += index + 1
                    lines[header].trim() in OBJECT_INITIALISERS -> objectInitialisers++
                    lines.getOrNull(header - 1)?.trim()?.startsWith(UNAVAILABLE) == true -> Unit
                    else -> {
                        val member = memberName(lines[header].trim())
                        if (member == null) unrecognised += index + 1
                        else stubs += SwiftStub(index + 1, (lines.typesAround(header) + member).joinToString("."), member)
                    }
                }
            }
        }
    }
    return SwiftStubScan(stubs, objectInitialisers, unrecognised)
}

private const val FATAL_ERROR = "fatalError()"
// The second is an object whose class extends another exported class (`data object Idle : LoadStatus`).
private val OBJECT_INITIALISERS = setOf("private override init() {", "private init() {")

/** On a Kotlin declaration deprecated as an error: Swift refuses to compile a call to it, so its stubbed body can't run. */
private const val UNAVAILABLE = "@available(*, unavailable"
private val ENUM_DEFAULT = Regex("""default:\s*fatalError\(\)""")

/** In the Swift side of a suspend function Kotlin calls: the continuation of a call that returns `Nothing`, which is never resumed. */
private val NEVER_CONTINUATION = Regex("""\{\s*_\s+in\s+fatalError\(\)\s*\}""")
private val ACCESSOR = Regex("""(get|set)\b.*\{""")
// A function's name is an identifier, a backticked one or an operator (`public static func +(`).
private val MEMBER = Regex("""\b(?:func|var|let)\s+(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*|[^\sA-Za-z_(<`]+)""")
private val INITIALISER = Regex("""\binit[?!]?\s*[(<]""")
private val SUBSCRIPT = Regex("""\bsubscript\s*[(<]""")
private val TYPE = Regex("""\b(?:class|struct|enum|protocol|extension|actor)\s+([A-Za-z_][A-Za-z0-9_.]*)""")

private fun String.indent() = indexOfFirst { it != ' ' }.let { if (it < 0) length else it }

/** The nearest line above [from] that is indented less than it and isn't blank: the line that opens the block [from] is in. */
private fun List<String>.opener(from: Int): Int? {
    val indent = this[from].indent()
    return (from - 1 downTo 0).firstOrNull { this[it].isNotBlank() && this[it].indent() < indent }
}

/**
 * The first line of the declaration whose body holds line [body]: past a `get {` or `set {` to the property, and from the `) -> T {` that
 * ends a signature written over several lines back to the line that starts it.
 */
private fun List<String>.declarationAround(body: Int): Int? {
    var header = opener(body) ?: return null
    if (ACCESSOR.matches(this[header].trim())) header = opener(header) ?: return null
    if (this[header].trim().startsWith(")")) {
        val indent = this[header].indent()
        header = (header - 1 downTo 0).firstOrNull { this[it].isNotBlank() && this[it].indent() <= indent } ?: return null
    }
    return header
}

private fun memberName(header: String): String? =
    MEMBER.find(header)?.groupValues?.get(1)?.trim('`')
        ?: "init".takeIf { INITIALISER.containsMatchIn(header) }
        ?: "subscript".takeIf { SUBSCRIPT.containsMatchIn(header) }

/** The names of the types and extensions line [from] is declared in, outermost first. */
private fun List<String>.typesAround(from: Int): List<String> {
    val names = ArrayDeque<String>()
    var at = opener(from)
    while (at != null) {
        TYPE.find(this[at])?.let { names.addFirst(it.groupValues[1]) }
        at = opener(at)
    }
    return names
}

/**
 * The events in one generated Swift file, [swift], that Swift can't build: `ImportActivityEvent.SaveBatch` for a case of a sealed
 * `ImportActivityEvent` that is a class with no public initialiser. Swift export gives a class no initialiser, and no warning, when its
 * Kotlin constructor takes a type it can't pass in, which an `ImmutableList`, `ImmutableSet` or `ImmutableMap` is; the event then needs a
 * second constructor taking the plain collection.
 *
 * An event is known by its generated name: a case of a sealed interface is a top-level class named for the interface and the case, and the
 * contracts' event interfaces end in `Event`. A state, which Swift only reads, is not looked at.
 */
internal fun swiftEventsWithoutInitialiser(swift: String): List<String> {
    val found = mutableListOf<String>()
    var event: String? = null
    var buildable = false
    swift.lineSequence().forEach { line ->
        val header = EVENT_CLASS.find(line)
        when {
            header != null -> {
                event = header.groupValues[1].replace('_', '.')
                buildable = false
            }
            event == null -> Unit
            line == "}" -> {
                if (!buildable) found += event!!
                event = null
            }
            line.startsWith("    public init(") || line.startsWith("    public static var shared") -> buildable = true
        }
    }
    return found
}

private val EVENT_CLASS = Regex("""^public final class _ExportedKotlinPackages_\S*?_([A-Za-z0-9]*Event_[A-Za-z0-9_]+?)(?:_SealedType)?:""")

/** Whether [stub] is one of [allowed]'s names: its member name, or the end of its declaration from a `.` on. */
internal fun SwiftStub.allowedBy(allowed: Set<String>): String? = allowed.firstOrNull {
    it == member || declaration == it || declaration.endsWith(".$it")
}

/** A project the iOS app exports to Swift whole, and the name of the Swift module it becomes. */
data class SwiftExportedModule(val path: String, val swiftName: String)

/** `:feature-login-api` -> `FeatureLoginApi`: the Swift module name Swift export is told to use, which is also its generated file's. */
fun swiftModuleName(projectPath: String): String =
    projectPath.trim(':').split('-', ':').joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }

/**
 * The modules of a build with [projectPaths] that are exported to Swift whole: the text field state, the shared contracts and every
 * feature's API module. From the build's own module list, so a new feature is exported and checked with no step of its own.
 */
fun swiftContractModules(projectPaths: Iterable<String>): List<SwiftExportedModule> =
    projectPaths.filter { it == CORE_TEXTFIELD || it.endsWith(API_SUFFIX) }.sorted().map { SwiftExportedModule(it, swiftModuleName(it)) }

internal const val CORE_TEXTFIELD = ":core-textfield"

/** What `activitytracker.swift.export` gives the script of the module that exports to Swift, as `swiftContracts`. */
abstract class SwiftContractsExtension(
    /** The contract modules of this build, for the script to depend on and name. See [swiftContractModules]. */
    val modules: List<SwiftExportedModule>,
)

/**
 * `activitytracker.swift.export`: for the module whose Swift export Xcode builds. Adds the `swiftContracts` extension, which lists the
 * build's contract modules, and registers [CHECK_SWIFT_EXPORT_STUBS_TASK_NAME] failing on those modules' stubs.
 *
 * The task reads what the build's export tasks (`<target>SwiftExport`) generate, so it runs after them. Those tasks exist only when Gradle
 * is run from Xcode's build phase: run anywhere else, the task fails saying so, not passing on nothing.
 */
class SwiftExportConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val contracts = swiftContractModules(rootProject.childProjects.values.map { it.path })
            extensions.create("swiftContracts", SwiftContractsExtension::class.java, contracts)
            val exports = tasks.named { it.endsWith("SwiftExport") }
            tasks.register<CheckSwiftExportStubs>(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME) {
                group = JavaBasePlugin.VERIFICATION_GROUP
                description = "Fails when Swift export generated a declaration Swift can reach as a stub that stops the app."
                generatedSwift.from(files(exports).asFileTree.matching { include("**/files/*/*.swift") })
                failOn.addAll(contracts.map { it.swiftName })
                report.set(layout.buildDirectory.file("reports/swift-export/stubs.txt"))
            }
        }
    }
}

/**
 * Fails when a declaration in one of [failOn]'s Swift modules is a stub, or one of their events has no initialiser
 * ([swiftEventsWithoutInitialiser]), unless [allowed] names it with the reason Swift never calls it. Stubs in every other module are only
 * listed in [report], with the allowed ones: a library's or the data layer's stubbed member is a risk only once a contract's signature
 * reaches it, which the build can't know.
 */
abstract class CheckSwiftExportStubs : DefaultTask() {
    /** The generated `files/<Module>/<Module>.swift` of every target this build exports. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val generatedSwift: ConfigurableFileCollection

    /** The Swift modules whose stubs fail the build: the screen contracts, the text field state and the Swift-facing entry points. */
    @get:Input abstract val failOn: SetProperty<String>

    /**
     * A module of [failOn] with at least one exported Kotlin object. Finding no object initialiser in it means the generated Swift no
     * longer looks the way this check reads it, so the check fails instead of passing on a format it can't see stubs in.
     */
    @get:Input abstract val sentinelModule: Property<String>

    /** Declaration name to the reason Swift never calls it. A name is a member (`serializer`) or the end of a declaration. */
    @get:Input abstract val allowed: MapProperty<String, String>

    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val files = generatedSwift.files.sortedBy { it.path }
        if (files.isEmpty()) {
            throw GradleException(
                "No generated Swift to check. Swift export runs only from Xcode's build phase, so run this task there, in the same " +
                    "Gradle invocation as :iosExport:embedSwiftExportForXcode (dev/ios-swift-interop).",
            )
        }
        val scans = files.groupBy(File::nameWithoutExtension).mapValues { (_, sameModule) -> merged(sameModule) }
        val unrecognised = scans.filterValues { it.unrecognised.isNotEmpty() }
        val sentinel = sentinelModule.get()
        val formatChanged = scans[sentinel]?.objectInitialisers.let { it == null || it == 0 }
        val names = allowed.get()
        val failing = failOn.get()
        val refused =
            scans
                .filterKeys { it in failing }
                .flatMap { (module, scan) -> scan.stubs.filter { it.allowedBy(names.keys) == null }.map { "$module: ${it.declaration}" } }
        val unbuildable =
            files
                .filter { it.nameWithoutExtension in failing }
                .flatMap { file -> swiftEventsWithoutInitialiser(file.readText()).map { "${file.nameWithoutExtension}: $it" } }
                .distinct()
                .filter { event -> names.keys.none { event.endsWith(": $it") || event.endsWith(".$it") } }
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(reportText(scans, failing, names))

        val problems = buildList {
            if (unrecognised.isNotEmpty()) {
                add(
                    "fatalError() in a shape this check doesn't know, in " +
                        unrecognised.entries.joinToString { (module, scan) -> "$module.swift line ${scan.unrecognised.first()}" } +
                        ". The generated Swift changed: update swiftExportStubs and its tests.",
                )
            }
            if (formatChanged) {
                add(
                    "$sentinel.swift has no `private override init()` stub, which every exported object has. Either the module is " +
                        "missing from the export or the generated Swift changed: update swiftExportStubs and its tests.",
                )
            }
            if (refused.isNotEmpty()) {
                add(
                    "Swift export generated these as stubs that stop the app when Swift calls them. Each names a type from a module " +
                        "the export hides: stop hiding that module in iosExport/build.gradle.kts, or change the declaration's type " +
                        "to one that exports. Allow one only if Swift never calls it.\n" +
                        refused.sorted().joinToString("\n") { "    $it" },
                )
            }
            if (unbuildable.isNotEmpty()) {
                add(
                    "Swift can't send these events: Swift export gave them no initialiser, which it does without a warning when a " +
                        "constructor takes a type it can't pass in (an ImmutableList, ImmutableSet or ImmutableMap). Give each a " +
                        "second constructor taking the plain List, Set or Map.\n" +
                        unbuildable.sorted().joinToString("\n") { "    $it" },
                )
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Swift export stub check (dev/ios-swift-interop):\n" + problems.joinToString("\n") { "  $it" })
        }
    }

    /** One module's scan across the targets that export it: the same declarations, so each stub once. */
    private fun merged(files: List<File>): SwiftStubScan {
        val scans = files.map { swiftExportStubs(it.readText()) }
        return SwiftStubScan(
            stubs = scans.flatMap { it.stubs }.distinctBy { it.declaration },
            objectInitialisers = scans.maxOf { it.objectInitialisers },
            unrecognised = scans.flatMap { it.unrecognised }.distinct(),
        )
    }

    private fun reportText(scans: Map<String, SwiftStubScan>, failing: Set<String>, names: Map<String, String>): String = buildString {
        appendLine("Stubs in the generated Swift: declarations whose body is fatalError(), which stop the app when Swift calls them.")
        appendLine("A stub in a checked module fails the iOS build unless it is allowed. The others are listed, not checked.")
        scans.toSortedMap().forEach { (module, scan) ->
            if (scan.stubs.isEmpty()) return@forEach
            appendLine()
            appendLine("$module (${if (module in failing) "checked" else "not checked"}): ${scan.stubs.size}")
            scan.stubs
                .sortedBy { it.declaration }
                .forEach { stub ->
                    val reason = stub.allowedBy(names.keys)?.let { " [allowed: ${names.getValue(it)}]" }.orEmpty()
                    appendLine("  ${stub.declaration}$reason")
                }
        }
    }
}
