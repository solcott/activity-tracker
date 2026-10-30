import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

const val CHECK_MODULE_BOUNDARIES_TASK_NAME = "checkModuleBoundaries"

internal const val API_SUFFIX = "-api"
internal const val PRESENTER_SUFFIX = "-presenter"
internal const val UI_SUFFIX = "-ui"
internal const val APP_COMMON_API = ":app-common-api"
internal const val APP_COMMON_PRESENTER = ":app-common-presenter"
internal const val APP_COMMON_PRESENTER_TESTING = ":app-common-presenter-testing"
internal const val APP_COMMON_UI = ":app-common-ui"
internal const val APP_FIXTURES = ":app-fixtures"
internal const val UI_DESIGNSYSTEM = ":ui-designsystem"
internal const val UI_RESOURCES = ":ui-resources"

/** What a client module holds, read from its path alone (dev/module-boundaries). */
internal enum class ModuleKind {
    /** Screens, States, Events and shared contracts: `-api` modules, and `:app-screens` until every feature has moved out of it. */
    Api,
    /** Presenters and their helpers: `-presenter` modules, `:app-common-presenter(-testing)`, and `:app-presenters` until it's empty. */
    Presenter,
    /** Compose UI: `-ui` modules and the `:ui-*` modules (design system, resources). */
    Ui,
    /** Everything else (data, fixtures, the apps), which these rules don't restrict. */
    Other;

    companion object {
        fun of(path: String): ModuleKind =
            when {
                path.endsWith(API_SUFFIX) || path == ":app-screens" -> Api
                path.endsWith(PRESENTER_SUFFIX) || path == APP_COMMON_PRESENTER_TESTING || path == ":app-presenters" -> Presenter
                path.endsWith(UI_SUFFIX) || path.startsWith(":ui-") -> Ui
                else -> Other
            }
    }
}

/** `:feature-search-ui` -> `search`; null for modules outside `feature/`. */
internal fun featureOf(path: String): String? =
    path.takeIf { it.startsWith(":feature-") }?.removePrefix(":feature-")?.substringBeforeLast('-')

/** Why [from] may not depend on [to], or null when it may. */
internal fun boundaryViolation(from: String, to: String): String? {
    val fromKind = ModuleKind.of(from)
    val toKind = ModuleKind.of(to)
    val fromFeature = featureOf(from)
    val toFeature = featureOf(to)
    return when {
        fromKind == ModuleKind.Presenter && toKind == ModuleKind.Ui -> "presenter modules never depend on UI modules"
        fromKind == ModuleKind.Ui && toKind == ModuleKind.Presenter -> "UI modules never depend on presenter modules"
        fromKind == ModuleKind.Api && toKind != ModuleKind.Api && !to.startsWith(":core-") ->
            "API modules depend only on other API modules, the shared contracts and :core-*"
        toFeature != null && fromFeature != toFeature && toKind != ModuleKind.Api && from !in APP_SHELLS ->
            "features depend on another feature only through its API module"
        else -> null
    }
}

/**
 * Registers [CHECK_MODULE_BOUNDARIES_TASK_NAME] and wires it into `check`: fails when this module declares a project dependency that
 * [boundaryViolation] forbids. Declared dependencies only (not the resolved graph), so it's cheap and needs no resolution; a transitive
 * violation is still caught at the module that declares the forbidden edge.
 */
internal fun Project.configureModuleBoundaries() {
    val declared = provider {
        configurations
            .filter { configuration -> DECLARING_CONFIGURATIONS.any { configuration.name.endsWith(it, ignoreCase = true) } }
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
            .map { it.path }
            .distinct()
            .sorted()
    }
    val projectPath = path
    val check =
        tasks.register<CheckModuleBoundaries>(CHECK_MODULE_BOUNDARIES_TASK_NAME) {
            group = JavaBasePlugin.VERIFICATION_GROUP
            description = "Fails when this module depends on a module the dev/module-boundaries rules forbid."
            modulePath.set(projectPath)
            dependencyPaths.set(declared)
            marker.set(layout.buildDirectory.file("moduleBoundaries/check.txt"))
        }
    plugins.withType(LifecycleBasePlugin::class.java) { tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(check) } }
}

/** The apps, which depend on every feature's presenter and UI modules so their Metro graphs aggregate the factories. */
private val APP_SHELLS = setOf(":composeApp", ":androidApp", ":webApp", ":desktopApp")

private val DECLARING_CONFIGURATIONS = listOf("api", "implementation", "compileOnly", "runtimeOnly")

@CacheableTask
abstract class CheckModuleBoundaries : DefaultTask() {
    @get:Input abstract val modulePath: Property<String>

    @get:Input abstract val dependencyPaths: ListProperty<String>

    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val from = modulePath.get()
        val violations = dependencyPaths.get().mapNotNull { to -> boundaryViolation(from, to)?.let { "$from -> $to: $it" } }
        if (violations.isNotEmpty()) {
            throw GradleException("Module boundary violations (dev/module-boundaries):\n" + violations.joinToString("\n") { "  $it" })
        }
        marker.get().asFile.writeText("ok\n")
    }
}
