import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

const val CHECK_IOS_SCREEN_REGISTRY_TASK_NAME = "checkIosScreenRegistry"

/**
 * The screens a Kotlin source file, [text], declares that a navigator can push: each `@CircuitSerializable` `data object` or `data class`.
 * Sub-screens aren't serializable; their parent's native view draws them. A text scan, like `checkSwiftExportSources`, so it runs where
 * nothing compiles the iOS targets.
 */
internal fun serializableScreens(text: String): List<String> =
    SERIALIZABLE_SCREEN.findAll(withoutCommentLines(text)).map { it.groupValues[1] }.toList()

/** The screens `ScreenRegistry.swift`, [swift], registers a native view for: each `register(Name.self, ...)`. */
internal fun registeredScreens(swift: String): Set<String> =
    REGISTERED_SCREEN.findAll(withoutCommentLines(swift)).map { it.groupValues[1] }.toSet()

/** [text] without its lines that are comments only: line comments, and the lines of block and doc comments. */
private fun withoutCommentLines(text: String): String =
    text
        .lines()
        .filterNot { line -> line.trim().let { it.startsWith("//") || it.startsWith("/*") || it.startsWith("*") } }
        .joinToString("\n")

private val SERIALIZABLE_SCREEN =
    Regex("""@CircuitSerializable\b(?:\([^)]*\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*data\s+(?:object|class)\s+(\w+)""")
private val REGISTERED_SCREEN = Regex("""\bregister\(\s*(\w+)\.self\b""")

/**
 * Fails when a screen the iOS app can show (a serializable screen of a contract module, [contractSources]) has no native view in [registry]
 * (ui/ios-native-shell): a screen without one would need the shared Compose UI, which the iOS app is to stop drawing.
 */
@CacheableTask
abstract class CheckIosScreenRegistry : DefaultTask() {
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractSources: ConfigurableFileCollection

    /** `iosApp/iosApp/Shell/ScreenRegistry.swift`. */
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val registry: RegularFileProperty

    /** The build's root, which failures name files relative to. Not an input: moving the checkout changes no result. */
    @get:Internal abstract val sourceRoot: Property<String>

    /** Written only on success, so the task is up to date until a source changes. */
    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val root = File(sourceRoot.get())
        val registered = registeredScreens(registry.get().asFile.readText())
        val missing =
            contractSources.files
                .sortedBy { it.path }
                .flatMap { file -> serializableScreens(file.readText()).map { it to file } }
                .filterNot { (screen, _) -> screen in registered }
                .map { (screen, file) -> "$screen (${file.relativeTo(root).path})" }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "These screens have no native view in ${registry.get().asFile.relativeTo(root).path}, and every screen the iOS app can " +
                    "show needs one, since it is to stop drawing the shared Compose UI (ui/ios-native-shell). Register a view for each:\n" +
                    missing.joinToString("\n") { "  $it" },
            )
        }
        marker.get().asFile.writeText("ok\n")
    }
}
