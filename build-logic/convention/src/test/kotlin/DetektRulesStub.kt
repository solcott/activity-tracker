import java.io.File

/**
 * Adds an empty `:detekt-rules` project to a TestKit build: `activitytracker.quality` puts that project on every module's `detektPlugins`,
 * and the real one (the repo's rules) isn't part of these builds.
 */
fun File.addDetektRulesStub() {
    resolve("settings.gradle.kts").appendText("\ninclude(\":detekt-rules\")\n")
    resolve("detekt-rules").mkdirs()
    resolve("detekt-rules/build.gradle.kts").writeText("plugins { java }\n")
}
