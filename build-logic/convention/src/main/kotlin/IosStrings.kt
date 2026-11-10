import groovy.json.JsonSlurper
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.xml.sax.InputSource

const val CHECK_IOS_STRINGS_TASK_NAME = "checkIosStrings"

/**
 * What is wrong with the iOS app's String Catalog ([catalogJson], `Localizable.xcstrings`) against the shared UI's strings ([sharedXml],
 * `strings.xml`), one line per string, or nothing when every entry of the catalog has the text of the shared string of the same name
 * (ui/ios-native-shell). The catalog holds only the strings the native views show, so a shared string it lacks is not a fault.
 *
 * A shared `%1$s` is `%1$@` in the catalog, since a Swift string argument is an object.
 */
internal fun iosStringFaults(sharedXml: String, catalogJson: String): List<String> {
    val shared = sharedStrings(sharedXml)
    return catalogStrings(catalogJson).mapNotNull { (name, text) ->
        val expected = shared[name]?.let(::asSwiftFormat)
        when {
            expected == null -> "$name: no shared string has this name any more"
            text == null -> "$name: has no plain English text"
            text != expected -> "$name: is \"$text\" but the shared string is \"$expected\""
            else -> null
        }
    }
}

/** Each `<string name="…">` of a `strings.xml`, with Android's backslash escapes undone. Plurals are left out: the catalog has none. */
private fun sharedStrings(xml: String): Map<String, String> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(xml.reader()))
    val strings = document.getElementsByTagName("string")
    return (0 until strings.length).associate { index ->
        val element = strings.item(index)
        element.attributes.getNamedItem("name").nodeValue to unescaped(element.textContent)
    }
}

private fun unescaped(text: String): String =
    Regex("""\\(.)""").replace(text) { match ->
        when (val escaped = match.groupValues[1]) {
            "n" -> "\n"
            "t" -> "\t"
            else -> escaped
        }
    }

/** Kotlin's string arguments (`%s`, `%1$s`) as Swift's (`%@`, `%1$@`); numbers keep their specifier. */
private fun asSwiftFormat(text: String): String = Regex("""%(\d+\$)?s""").replace(text) { "%${it.groupValues[1]}@" }

/** Each entry of a String Catalog with its English text, or null for an entry that has none (a plural, or a text left out). */
private fun catalogStrings(json: String): Map<String, String?> {
    val strings = (JsonSlurper().parseText(json) as Map<*, *>)["strings"] as? Map<*, *> ?: return emptyMap()
    return strings.entries.associate { (name, entry) ->
        val english = ((entry as? Map<*, *>)?.get("localizations") as? Map<*, *>)?.get("en") as? Map<*, *>
        name.toString() to ((english?.get("stringUnit") as? Map<*, *>)?.get("value") as? String)
    }
}

/**
 * Fails when a string the iOS app holds for a native view no longer has the text of the shared string of the same name, naming each. Until
 * the catalog is generated from `strings.xml`, this is what keeps the two from drifting. Reads two files and runs anywhere, so it is part
 * of `check`.
 */
@CacheableTask
abstract class CheckIosStrings : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val sharedStrings: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val catalog: RegularFileProperty

    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val faults = iosStringFaults(sharedStrings.get().asFile.readText(), catalog.get().asFile.readText())
        if (faults.isNotEmpty()) {
            throw GradleException(
                "iOS strings that differ from the shared UI's (ui/ios-native-shell). Each entry of ${catalog.get().asFile.name} has " +
                    "the text of the string of the same name in ${sharedStrings.get().asFile.name}:\n" +
                    faults.joinToString("\n") { "  $it" },
            )
        }
        marker.get().asFile.writeText("ok\n")
    }
}
