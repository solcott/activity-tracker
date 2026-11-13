import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

// The iOS app draws every screen natively and ships no Compose UI (ui/ios-native-shell). Presenters still run on the Compose runtime,
// which stays; what may not reach an iOS build is below.

internal const val CHECK_IOS_DEPENDENCIES_TASK_NAME = "checkIosDependencies"

/**
 * Groups that reach iOS only by drawing Compose UI: Compose UI, Foundation, Material and their androidx twins, Compose resources, Skia, and
 * the Compose-only UI libraries. A group matches itself and its subgroups (`org.jetbrains.compose.material3.adaptive`).
 */
internal val IOS_FORBIDDEN_GROUPS =
    listOf(
        "org.jetbrains.compose.ui",
        "org.jetbrains.compose.foundation",
        "org.jetbrains.compose.material",
        "org.jetbrains.compose.material3",
        "org.jetbrains.compose.animation",
        "org.jetbrains.compose.components",
        "androidx.compose.ui",
        "androidx.compose.foundation",
        "androidx.compose.material",
        "androidx.compose.material3",
        "androidx.compose.animation",
        "org.jetbrains.skiko",
        "com.mohamedrejeb.calf",
        "dev.chrisbanes.haze",
        "com.patrykandpatrick.vico",
        "io.coil-kt.coil3",
        "org.maplibre.compose",
    )

/** Modules of otherwise allowed groups that draw Compose UI: Circuit's UI half and FileKit's Compose dialogs. Prefixes of the name. */
internal val IOS_FORBIDDEN_MODULES =
    listOf(
        "com.slack.circuit:circuit-foundation",
        "com.slack.circuit:circuit-backstack",
        "com.slack.circuit:circuit-overlay",
        "com.slack.circuit:circuit-runtime-ui",
        "com.slack.circuit:circuit-sharedelements",
        "com.slack.circuit:circuitx-gesture-navigation",
        "io.github.vinceglb:filekit-dialogs",
    )

/**
 * Groups excluded from every iOS configuration. SubCircuit (`circuitx-subcircuit`), which the contracts and the presenter host expose,
 * publishes Compose UI and Foundation as `api` dependencies for every target, though only `SubCircuitContent` and `SubUi` use them; iOS
 * uses its runtime interfaces alone, so the linker never reaches the declarations that name them.
 */
internal val IOS_EXCLUDED_GROUPS =
    listOf("org.jetbrains.compose.ui", "org.jetbrains.compose.foundation", "androidx.compose.ui", "androidx.compose.foundation")

/** Whether the `group:module` coordinates name something [IOS_FORBIDDEN_GROUPS] or [IOS_FORBIDDEN_MODULES] keep out of iOS. */
internal fun isForbiddenOnIos(group: String, module: String): Boolean =
    IOS_FORBIDDEN_GROUPS.any { group == it || group.startsWith("$it.") } || IOS_FORBIDDEN_MODULES.any { "$group:$module".startsWith(it) }

/** Excludes [IOS_EXCLUDED_GROUPS] from this module's iOS configurations (each target's and the shared iOS and native source sets'). */
internal fun Project.excludeComposeUiOnIos() {
    configurations.configureEach {
        if (name.startsWith("ios") || name.startsWith("native") || name.startsWith("apple")) {
            IOS_EXCLUDED_GROUPS.forEach { exclude(mapOf("group" to it)) }
        }
    }
}

/**
 * Each forbidden component in the graph under [root], with the shortest path of modules that brought it, from [root] down:
 * `org.jetbrains.compose.ui:ui (:iosExport > :iosShell > :app-common-ui)`.
 */
internal fun forbiddenOnIos(root: ResolvedComponentResult): List<String> {
    val found = mutableListOf<String>()
    val seen = mutableSetOf(root.id)
    val queue = ArrayDeque(listOf(root to listOf(root.label())))
    while (queue.isNotEmpty()) {
        val (component, path) = queue.removeFirst()
        component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { dependency ->
            val selected = dependency.selected
            if (!seen.add(selected.id)) return@forEach
            val id = selected.id
            if (id is ModuleComponentIdentifier && isForbiddenOnIos(id.group, id.module)) {
                found += "${id.group}:${id.module} (${path.joinToString(" > ")})"
            } else {
                queue.addLast(selected to path + selected.label())
            }
        }
    }
    return found.sorted()
}

private fun ResolvedComponentResult.label(): String =
    when (val id = id) {
        is ProjectComponentIdentifier -> id.projectPath
        is ModuleComponentIdentifier -> "${id.group}:${id.module}"
        else -> id.displayName
    }

/**
 * Registers [CHECK_IOS_DEPENDENCIES_TASK_NAME] over this module's iOS link classpaths (`ios*CompileKlibraries`, the test ones aside) and
 * wires it into `check`.
 */
internal fun Project.registerIosDependencyCheck() {
    val check =
        tasks.register<CheckIosDependencies>(CHECK_IOS_DEPENDENCIES_TASK_NAME) {
            group = JavaBasePlugin.VERIFICATION_GROUP
            description = "Fails when the iOS app would resolve Compose UI, Skia or a Compose-only UI library (ui/ios-native-shell)."
            configurations
                .matching { it.name.startsWith("ios") && it.name.endsWith("CompileKlibraries") && !it.name.contains("Test") }
                .forEach { configuration ->
                    val name = configuration.name
                    forbidden.addAll(
                        configuration.incoming.resolutionResult.rootComponent.map { root -> forbiddenOnIos(root).map { "$name: $it" } },
                    )
                }
            marker.set(layout.buildDirectory.file("iosDependencies/check.txt"))
        }
    plugins.withType(LifecycleBasePlugin::class.java) { tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(check) } }
}

/** Fails on each entry of [forbidden]: a component kept out of iOS that an iOS configuration resolves, with the path that brought it. */
@CacheableTask
abstract class CheckIosDependencies : DefaultTask() {
    @get:Input abstract val forbidden: ListProperty<String>

    /** Written only on success, so the task is up to date until the dependencies change. */
    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val found = forbidden.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "The iOS app draws natively and ships no Compose UI (ui/ios-native-shell), but its build resolves:\n" +
                    found.joinToString("\n") { "  $it" } +
                    "\nDepend on a presenter or contract module instead of a UI one, or exclude the library on iOS.",
            )
        }
        marker.get().asFile.writeText("ok\n")
    }
}
