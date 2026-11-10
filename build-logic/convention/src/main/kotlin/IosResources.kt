import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import org.xml.sax.InputSource

const val GENERATE_IOS_RESOURCES_TASK_NAME = "generateIosResources"
const val CHECK_IOS_RESOURCES_TASK_NAME = "checkIosResources"

/*
 * What the native iOS UI draws with is generated from what the shared UI draws with (dev/ios-native-resources): its String Catalog
 * from `strings.xml`, its `AppIcon` from the Kotlin one, and its sport images from the vendored drawables. The generated files are
 * committed, because Xcode fixes a target's files before any build phase could write them, and `checkIosResources` (in `check`) fails
 * when one differs from what its source now produces. Everything here reads and writes text, so it runs on any machine.
 */

// ---- Strings ----

/** A placeholder of a shared string: `%1$s`, `%2$d`, or without a position, `%s`. */
private val PLACEHOLDER = Regex("""%(?:(\d+)\$)?[sd]""")

/** An Android string resource's backslash escape. */
private val ESCAPE = Regex("""\\(.)""")

/**
 * The iOS String Catalog (`Localizable.xcstrings`) for the shared strings in [sharedXml] (`strings.xml`), as Xcode itself lays one out, so
 * opening it in Xcode doesn't rewrite it. Every `<string>` is an entry under its own name, every `<plurals>` an entry whose form the
 * quantity selects, and every item of a `<string-array>` an entry named with the array's name and the item's index.
 *
 * Entries are `manual`, which stops Xcode removing one no Swift names yet or rewriting one. Every placeholder becomes `%@`, numbers
 * included: an argument reaches the catalog as text shared code has already formatted, since Foundation would group a `%lld`'s digits by
 * its own rule and not ui/number-format's. Compose replaces only its own placeholders, so a `%` beside one is literal there; a format
 * string needs it doubled, and an entry without placeholders is never formatted and keeps it single.
 */
internal fun iosStringCatalog(sharedXml: String): String {
    val resources = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(sharedXml.reader())).documentElement
    val entries = sortedMapOf<String, Any>()
    fun add(name: String, localization: Map<String, Any>) {
        if (entries.put(name, sortedMapOf("extractionState" to "manual", "localizations" to mapOf("en" to localization))) != null) {
            throw GradleException("Two shared strings would both be \"$name\" in the iOS String Catalog. Rename one.")
        }
    }
    resources.children().forEach { element ->
        val name = element.getAttribute("name")
        when (element.tagName) {
            "string" -> add(name, stringUnit(swiftFormat(androidText(element), firstPosition = 1)))
            "plurals" -> add(name, plural(element))
            "string-array" ->
                element.children().forEachIndexed { index, item ->
                    add("${name}_$index", stringUnit(swiftFormat(androidText(item), firstPosition = 1)))
                }
            else -> throw GradleException("strings.xml has a <${element.tagName}>, which the iOS String Catalog generator doesn't know.")
        }
    }
    return json(sortedMapOf("sourceLanguage" to "en", "strings" to entries, "version" to "1.0")) + "\n"
}

/**
 * A plural's entry. Compose chooses a plural's form by a quantity it takes apart from the placeholders' arguments
 * (`pluralStringResource(res, quantity, args…)`), so the entry's text is one substitution on argument 1, the quantity, and each form's own
 * placeholders are numbered from 2. A form is always formatted, so its literal `%` is always doubled.
 */
private fun plural(element: Element): Map<String, Any> {
    val forms = sortedMapOf<String, Any>()
    element.children().forEach { item ->
        forms[item.getAttribute("quantity")] = stringUnit(swiftFormat(androidText(item), firstPosition = 2, alwaysFormatted = true))
    }
    val quantity = sortedMapOf("argNum" to 1, "formatSpecifier" to "lld", "variations" to mapOf("plural" to forms))
    return stringUnit("%#@quantity@") + mapOf("substitutions" to mapOf("quantity" to quantity))
}

private fun stringUnit(value: String): Map<String, Any> = mapOf("stringUnit" to sortedMapOf("state" to "translated", "value" to value))

/** An element's text with Android's backslash escapes undone (`\'`, `\"`, `\n`); the XML parser has already undone the entities. */
private fun androidText(element: Element): String =
    ESCAPE.replace(element.textContent) { match ->
        when (val escaped = match.groupValues[1]) {
            "n" -> "\n"
            "t" -> "\t"
            else -> escaped
        }
    }

/**
 * [text] as a Swift format string: each placeholder `%N$@` with `N` counted from [firstPosition] (a placeholder without a position takes
 * the next), and every other `%` doubled when the text is formatted, which it is when it has a placeholder or [alwaysFormatted].
 */
private fun swiftFormat(text: String, firstPosition: Int, alwaysFormatted: Boolean = false): String {
    val placeholders = PLACEHOLDER.findAll(text).toList()
    if (placeholders.isEmpty() && !alwaysFormatted) return text
    val shift = firstPosition - 1
    val result = StringBuilder()
    var end = 0
    placeholders.forEachIndexed { index, match ->
        result.append(text.substring(end, match.range.first).replace("%", "%%"))
        val position = match.groupValues[1].ifEmpty { null }?.toInt() ?: (index + 1)
        result.append("%${position + shift}$@")
        end = match.range.last + 1
    }
    return result.append(text.substring(end).replace("%", "%%")).toString()
}

private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

/** [value] as JSON in the layout Xcode writes a String Catalog in: two-space indents and ` : ` after a key. */
private fun json(value: Any, indent: String = ""): String =
    when (value) {
        is Map<*, *> ->
            value.entries.joinToString(",\n", "{\n", "\n$indent}") { (key, item) ->
                "$indent  ${jsonString(key.toString())} : ${json(checkNotNull(item), "$indent  ")}"
            }
        is String -> jsonString(value)
        else -> value.toString()
    }

private fun jsonString(text: String): String =
    text
        .fold(StringBuilder("\"")) { out, char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\t' -> out.append("\\t")
                else -> out.append(char)
            }
        }
        .append('"')
        .toString()

// ---- Icons ----

/** One entry of the Kotlin `AppIcon` enum: its name and everything between its parentheses. */
private val ICON_ENTRY = Regex("""^ {4}(\w+)\(([^()]*)\),?[ \t]*$""", RegexOption.MULTILINE)

private val STRING_LITERAL = Regex(""""([^"\\]*)"""")

/** Names Swift keeps for itself that a Kotlin icon might have in lower case, which need backticks as an enum case. */
private val SWIFT_KEYWORDS =
    setOf(
        "import",
        "default",
        "repeat",
        "switch",
        "case",
        "return",
        "public",
        "private",
        "internal",
        "init",
        "self",
        "class",
        "struct",
        "enum",
        "func",
        "var",
        "let",
        "in",
        "is",
        "as",
        "do",
        "for",
        "if",
        "else",
        "while",
        "true",
        "false",
        "nil",
        "operator",
        "extension",
        "protocol",
        "static",
        "subscript",
        "super",
        "throw",
        "try",
        "catch",
        "where",
        "guard",
        "defer",
        "break",
        "continue",
        "fallthrough",
        "associatedtype",
        "typealias",
        "inout",
        "open",
        "fileprivate",
        "rethrows",
        "throws",
        "Any",
        "Self",
    )

/**
 * The Swift `AppIcon` for the Kotlin enum in [kotlinSource] (`AppIcon.kt`): each icon's SF Symbol, and its filled one where the Kotlin
 * entry names one. The Kotlin source is read as text, since its other fields are Compose types that can't be exported; an entry's first
 * string is its symbol and its second its filled symbol, as the enum's constructor orders them. Fails on an enum it reads no entry from, or
 * an entry with no symbol, rather than writing less.
 */
internal fun iosAppIconSwift(kotlinSource: String): String {
    val body = kotlinSource.substringAfter("enum class AppIcon(", "").substringAfter(") {", "")
    val icons =
        ICON_ENTRY.findAll(body)
            .map { entry ->
                val name = entry.groupValues[1]
                val symbols = STRING_LITERAL.findAll(entry.groupValues[2]).map { it.groupValues[1] }.toList()
                if (symbols.isEmpty() || symbols.size > 2) {
                    throw GradleException("AppIcon.$name: expected its SF Symbol and at most a filled one, found ${symbols.size} strings.")
                }
                Triple(
                    name.replaceFirstChar { it.lowercase() }.let { if (it in SWIFT_KEYWORDS) "`$it`" else it },
                    symbols[0],
                    symbols.getOrNull(1),
                )
            }
            .toList()
    if (icons.isEmpty()) throw GradleException("No entries read from the Kotlin AppIcon enum: its layout changed. Fix iosAppIconSwift.")
    val filled = icons.filter { it.third != null }
    return buildString {
        appendLine("// Generated by ./gradlew generateIosResources from the shared design system's AppIcon.kt. Don't edit it:")
        appendLine("// checkIosResources fails when it differs from what the Kotlin enum now produces.")
        appendLine()
        appendLine("/// The app's chrome icons, each as the SF Symbol the shared UI draws for it on iOS (dev/ios-native-resources).")
        appendLine("enum AppIcon: CaseIterable {")
        icons.forEach { appendLine("    case ${it.first}") }
        appendLine()
        appendLine("    /// The icon's SF Symbol.")
        appendLine("    var symbol: String {")
        appendLine("        switch self {")
        icons.forEach { appendLine("        case .${it.first}: \"${it.second}\"") }
        appendLine("        }")
        appendLine("    }")
        appendLine()
        appendLine("    /// The symbol of the icon's selected or active form, which is the plain one where it has no other.")
        appendLine("    var filledSymbol: String {")
        appendLine("        switch self {")
        filled.forEach { appendLine("        case .${it.first}: \"${it.third}\"") }
        appendLine("        default: symbol")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
    }
}

// ---- Drawables ----

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/** The attributes of a drawable's `<path>` the converter carries over, or drops knowingly. */
private val PATH_ATTRIBUTES = setOf("pathData", "fillColor", "fillType")

/**
 * The SVG of the Android vector drawable [vectorXml], for a template image: its paths in its own viewport. An Android path's data is SVG
 * path data as it stands. The fill colour is dropped, since a template image is drawn in whatever colour the view gives it, and
 * `autoMirrored` too (the iOS app has no right-to-left layout yet). Fails on anything else it would have to drop: a group, a stroke, a
 * gradient, a clip.
 */
internal fun iosDrawableSvg(name: String, vectorXml: String): String {
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val vector = factory.newDocumentBuilder().parse(InputSource(vectorXml.reader())).documentElement
    if (vector.tagName != "vector") throw GradleException("$name: not a vector drawable (<${vector.tagName}>).")
    val width = vector.getAttributeNS(ANDROID_NS, "viewportWidth")
    val height = vector.getAttributeNS(ANDROID_NS, "viewportHeight")
    val paths =
        vector.children().joinToString("") { child ->
            if (child.tagName != "path") throw GradleException("$name: has a <${child.tagName}>, which the SVG converter doesn't handle.")
            val attributes = (0 until child.attributes.length).map { child.attributes.item(it).localName }
            (attributes - PATH_ATTRIBUTES).firstOrNull()?.let {
                throw GradleException("$name: its path has android:$it, which the SVG converter doesn't handle.")
            }
            val rule =
                when (val fillType = child.getAttributeNS(ANDROID_NS, "fillType")) {
                    "",
                    "nonZero" -> ""
                    "evenOdd" -> " fill-rule=\"evenodd\""
                    else -> throw GradleException("$name: unknown android:fillType \"$fillType\".")
                }
            "<path d=\"${child.getAttributeNS(ANDROID_NS, "pathData")}\"$rule/>"
        }
    return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 $width $height\">$paths</svg>\n"
}

/** An asset catalog folder's own file. */
private const val CATALOG_INFO = "{\n  \"info\" : {\n    \"author\" : \"xcode\",\n    \"version\" : 1\n  }\n}\n"

/** The folder that gives the images their namespace, so a drawable named `map` is `Drawable/map` and can't meet another asset's name. */
internal const val DRAWABLE_NAMESPACE = "Drawable"

/**
 * The files of the asset catalog that holds [drawables] (name to vector XML) as template images that keep their vector, by path inside the
 * catalog.
 */
internal fun iosDrawableCatalog(drawables: Map<String, String>): Map<String, String> {
    val files = sortedMapOf("Contents.json" to CATALOG_INFO)
    files["$DRAWABLE_NAMESPACE/Contents.json"] =
        "{\n  \"info\" : {\n    \"author\" : \"xcode\",\n    \"version\" : 1\n  },\n  \"properties\" : {\n    \"provides-namespace\" : true\n  }\n}\n"
    drawables.toSortedMap().forEach { (name, xml) ->
        val folder = "$DRAWABLE_NAMESPACE/$name.imageset"
        files["$folder/$name.svg"] = iosDrawableSvg(name, xml)
        files["$folder/Contents.json"] =
            "{\n  \"images\" : [\n    {\n      \"filename\" : \"$name.svg\",\n      \"idiom\" : \"universal\"\n    }\n  ],\n" +
                "  \"info\" : {\n    \"author\" : \"xcode\",\n    \"version\" : 1\n  },\n" +
                "  \"properties\" : {\n    \"preserves-vector-representation\" : true,\n    \"template-rendering-intent\" : \"template\"\n  }\n}\n"
    }
    return files
}

// ---- Tasks ----

/** What both tasks read and where the generated files live. */
abstract class IosResourcesTask : DefaultTask() {
    /** The shared UI's `strings.xml`. */
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val sharedStrings: RegularFileProperty

    /** The shared design system's `AppIcon.kt`. */
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val appIconSource: RegularFileProperty

    /** The shared UI's vendored vector drawables. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val drawables: ConfigurableFileCollection

    /** `Localizable.xcstrings`. */
    @get:Internal abstract val catalog: RegularFileProperty

    /** `AppIcon.swift`. */
    @get:Internal abstract val appIconSwift: RegularFileProperty

    /** The generated asset catalog's folder. */
    @get:Internal abstract val drawableCatalog: DirectoryProperty

    /** Every generated file with its text, as the sources now produce it. */
    protected fun generated(): Map<File, String> {
        val images =
            iosDrawableCatalog(
                drawables.asFileTree.files.filter { it.extension == "xml" }.associate { it.nameWithoutExtension to it.readText() },
            )
        val catalogDir = drawableCatalog.get().asFile
        return mapOf(
            catalog.get().asFile to iosStringCatalog(sharedStrings.get().asFile.readText()),
            appIconSwift.get().asFile to iosAppIconSwift(appIconSource.get().asFile.readText()),
        ) + images.mapKeys { (path, _) -> catalogDir.resolve(path) }
    }

    /** Files in the generated asset catalog that no drawable produces any more. */
    protected fun stale(generated: Set<File>): List<File> =
        drawableCatalog.get().asFile.walkTopDown().filter { it.isFile && it !in generated }.toList()
}

/** Writes the iOS app's generated resources from the shared UI's sources, and removes images whose drawable is gone. */
abstract class GenerateIosResources : IosResourcesTask() {
    @TaskAction
    fun generate() {
        val files = generated()
        files.forEach { (file, text) ->
            file.parentFile.mkdirs()
            if (!file.exists() || file.readText() != text) file.writeText(text)
        }
        stale(files.keys).forEach { it.delete() }
        drawableCatalog.get().asFile.walkBottomUp().filter { it.isDirectory && it.list().isNullOrEmpty() }.forEach { it.delete() }
    }
}

/**
 * Fails when a generated iOS resource isn't what the shared sources now produce, naming the files and the task that rewrites them
 * (dev/ios-native-resources). Reads text only, so it is part of `check` on every machine.
 */
abstract class CheckIosResources : IosResourcesTask() {
    /** The generated files as they are on disk, so the check reruns when one is edited. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val committed: ConfigurableFileCollection

    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val files = generated()
        val root = catalog.get().asFile.parentFile
        val differing = files.filter { (file, text) -> !file.exists() || file.readText() != text }.keys + stale(files.keys)
        if (differing.isNotEmpty()) {
            throw GradleException(
                "The iOS app's generated resources differ from the shared UI's sources (dev/ios-native-resources). " +
                    "Run ./gradlew $GENERATE_IOS_RESOURCES_TASK_NAME and commit the result:\n" +
                    differing.sorted().joinToString("\n") { "  ${it.relativeToOrSelf(root)}" },
            )
        }
        marker.get().asFile.writeText("ok\n")
    }
}
