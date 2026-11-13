import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

/** The iOS app's generated strings, icons and images, and the check that holds them to their sources (dev/ios-native-resources). */
class IosResourcesTest {
    private fun strings(vararg lines: String) = "<resources>\n${lines.joinToString("\n")}\n</resources>"

    /** The English text the catalog holds for [name], read back out of its JSON. */
    private fun String.textOf(name: String): String {
        val entry = substringAfter("    \"$name\" : {")
        return entry.substringAfter("\"value\" : \"").substringBefore("\"\n").replace("\\\"", "\"").replace("\\n", "\n")
    }

    @Test
    fun `a plain string is an entry under its own name, kept by hand`() {
        val catalog = iosStringCatalog(strings("""<string name="login_sign_in">Sign in</string>"""))
        assertEquals(
            """
            {
              "sourceLanguage" : "en",
              "strings" : {
                "login_sign_in" : {
                  "extractionState" : "manual",
                  "localizations" : {
                    "en" : {
                      "stringUnit" : {
                        "state" : "translated",
                        "value" : "Sign in"
                      }
                    }
                  }
                }
              },
              "version" : "1.0"
            }

            """
                .trimIndent(),
            catalog,
        )
    }

    @Test
    fun `every placeholder is an object, numbers included, in its position`() {
        val catalog =
            iosStringCatalog(
                strings(
                    """<string name="error_with_reason">%1${'$'}s. %2${'$'}s</string>""",
                    """<string name="photos_count">%1${'$'}d of %2${'$'}d photos</string>""",
                    """<string name="unpositioned">%s and %d</string>""",
                ),
            )
        assertEquals("%1$@. %2$@", catalog.textOf("error_with_reason"))
        assertEquals("%1$@ of %2$@ photos", catalog.textOf("photos_count"))
        assertEquals("%1$@ and %2$@", catalog.textOf("unpositioned"))
    }

    @Test
    fun `a percent sign is doubled only where the text is formatted`() {
        val catalog =
            iosStringCatalog(
                strings(
                    """<string name="zone_model_percentMaxHeartRate">% of max heart rate (5)</string>""",
                    """<string name="sensor_battery_percent">Battery %1${'$'}s%</string>""",
                    """<string name="stat_cns_value">%1${'$'}s% → %2${'$'}s%</string>""",
                ),
            )
        assertEquals("% of max heart rate (5)", catalog.textOf("zone_model_percentMaxHeartRate"))
        assertEquals("Battery %1$@%%", catalog.textOf("sensor_battery_percent"))
        assertEquals("%1$@%% → %2$@%%", catalog.textOf("stat_cns_value"))
    }

    @Test
    fun `escapes and entities are undone`() {
        val catalog =
            iosStringCatalog(
                strings(
                    """<string name="apostrophe">Couldn\'t load your feed</string>""",
                    """<string name="ampersand">Privacy &amp; sharing</string>""",
                    """<string name="quoted">Say \"hi\"\nagain</string>""",
                ),
            )
        assertEquals("Couldn't load your feed", catalog.textOf("apostrophe"))
        assertEquals("Privacy & sharing", catalog.textOf("ampersand"))
        assertEquals("Say \"hi\"\nagain", catalog.textOf("quoted"))
    }

    @Test
    fun `a plural is selected by a quantity apart from its arguments`() {
        val catalog =
            iosStringCatalog(
                strings(
                    """<plurals name="card_with_others">""",
                    """    <item quantity="one">with %1${'$'}s and %2${'$'}d other</item>""",
                    """    <item quantity="other">with %1${'$'}s and %2${'$'}d others</item>""",
                    """</plurals>""",
                ),
            )
        assertContains(catalog, "\"value\" : \"%#@quantity@\"")
        assertContains(catalog, "\"argNum\" : 1,\n              \"formatSpecifier\" : \"lld\"")
        assertContains(
            catalog,
            "\"one\" : {\n                    \"stringUnit\" : {\n                      \"state\" : \"translated\",\n                      \"value\" : \"with %2$@ and %3$@ other\"",
        )
        assertContains(catalog, "\"value\" : \"with %2$@ and %3$@ others\"")
    }

    @Test
    fun `a plural form's percent sign is always doubled`() {
        val catalog =
            iosStringCatalog(
                strings("""<plurals name="p"><item quantity="one">100% once</item><item quantity="other">100% often</item></plurals>"""),
            )
        assertContains(catalog, "\"value\" : \"100%% once\"")
    }

    @Test
    fun `each item of a string array is an entry by its index`() {
        val catalog =
            iosStringCatalog(strings("""<string-array name="segment_rank_ordinals"><item>1st</item><item>2nd</item></string-array>"""))
        assertEquals("1st", catalog.textOf("segment_rank_ordinals_0"))
        assertEquals("2nd", catalog.textOf("segment_rank_ordinals_1"))
    }

    @Test
    fun `entries are sorted by name, whatever their order in the source`() {
        val catalog = iosStringCatalog(strings("""<string name="b">B</string>""", """<string name="a">A</string>"""))
        assertTrue(catalog.indexOf("\"a\" : {") < catalog.indexOf("\"b\" : {"))
    }

    @Test
    fun `two names that would be one entry fail`() {
        val error =
            assertFailsWith<GradleException> {
                iosStringCatalog(
                    strings("""<string name="ranks_0">x</string>""", """<string-array name="ranks"><item>1st</item></string-array>"""),
                )
            }
        assertContains(error.message.orEmpty(), "ranks_0")
    }

    private val appIcon =
        """
        enum class AppIcon(
            val material: ImageVector,
            val sfSymbol: String,
            internal val filledMaterial: ImageVector = material,
            val filledSfSymbol: String = sfSymbol,
        ) {
            Back(Icons.AutoMirrored.Filled.ArrowBack, "chevron.backward"),
            Import(Icons.Filled.AddCircle, "tray.and.arrow.down"),
            Like(
                Icons.Outlined.ThumbUp,
                "hand.thumbsup",
                Icons.Filled.ThumbUp,
                "hand.thumbsup.fill",
            ),
        }
        """
            .trimIndent()

    @Test
    fun `each icon is a case with its symbol, and its filled one where it has one`() {
        val swift = iosAppIconSwift(appIcon)
        assertContains(swift, "    case back\n    case `import`\n    case like\n")
        assertContains(
            swift,
            "        case .back: \"chevron.backward\"\n        case .`import`: \"tray.and.arrow.down\"\n        case .like: \"hand.thumbsup\"\n",
        )
        assertContains(swift, "        case .like: \"hand.thumbsup.fill\"\n        default: symbol\n")
    }

    @Test
    fun `an entry without a symbol fails naming it`() {
        val error = assertFailsWith<GradleException> { iosAppIconSwift(appIcon.replace(", \"chevron.backward\"", "")) }
        assertContains(error.message.orEmpty(), "AppIcon.Back")
    }

    @Test
    fun `an enum no entry is read from fails`() {
        assertFailsWith<GradleException> { iosAppIconSwift("enum class AppIcon(val sfSymbol: String) {\n  Back(\"chevron.backward\"),\n}") }
    }

    private val run =
        """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="24dp"
            android:height="24dp"
            android:viewportWidth="960"
            android:viewportHeight="960">
            android:autoMirrored="true">
          <path
              android:fillColor="#FF000000"
              android:pathData="M520,920L520,680Z"/>
        </vector>
        """
            .trimIndent()

    @Test
    fun `a drawable's path is an SVG path in its viewport`() =
        assertEquals(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 960 960\"><path d=\"M520,920L520,680Z\"/></svg>\n",
            iosDrawableSvg("directions_run", run),
        )

    @Test
    fun `an even-odd fill is kept`() =
        assertContains(
            iosDrawableSvg("ski_lift", run.replace("android:fillColor", "android:fillType=\"evenOdd\"\n      android:fillColor")),
            "fill-rule=\"evenodd\"",
        )

    @Test
    fun `what the converter would drop fails naming it`() {
        val stroked =
            assertFailsWith<GradleException> {
                iosDrawableSvg("odd", run.replace("android:fillColor", "android:strokeWidth=\"2\"\n      android:fillColor"))
            }
        assertContains(stroked.message.orEmpty(), "strokeWidth")
        val grouped = assertFailsWith<GradleException> { iosDrawableSvg("odd", run.replace("<path", "<group/><path")) }
        assertContains(grouped.message.orEmpty(), "<group>")
    }

    @Test
    fun `each drawable is a template image that keeps its vector, in a namespace`() {
        val files = iosDrawableCatalog(mapOf("directions_run" to run))
        assertEquals(
            setOf(
                "Contents.json",
                "Drawable/Contents.json",
                "Drawable/directions_run.imageset/Contents.json",
                "Drawable/directions_run.imageset/directions_run.svg",
            ),
            files.keys,
        )
        assertContains(files.getValue("Drawable/Contents.json"), "\"provides-namespace\" : true")
        assertContains(files.getValue("Drawable/directions_run.imageset/Contents.json"), "\"template-rendering-intent\" : \"template\"")
        assertContains(files.getValue("Drawable/directions_run.imageset/Contents.json"), "\"preserves-vector-representation\" : true")
    }

    // ---- The tasks ----

    private val projectDir: File by lazy {
        createTempDirectory("ios-resources").toFile().apply {
            resolve("settings.gradle.kts").writeText("rootProject.name = \"ios-resources-test\"\n")
            resolve("build.gradle.kts").writeText("plugins {\n    base\n    id(\"activitytracker.swift.export\")\n}\n")
            stringsFile
                .apply { parentFile.mkdirs() }
                .writeText(strings("""<string name="login_intro">Sign in with your account.</string>"""))
            resolve("ui/designsystem/src/commonMain/kotlin/io/github/solcott/activitytracker/app/ui/adaptive")
                .apply { mkdirs() }
                .resolve("AppIcon.kt")
                .writeText(appIcon)
            resolve("ui/resources/src/commonMain/composeResources/drawable").apply { mkdirs() }.resolve("directions_run.xml").writeText(run)
            // checkIosScreenRegistry, also in check, reads the registry; with no contract modules here it has nothing to find.
            resolve("iosApp/iosApp/Shell/ScreenRegistry.swift").apply { parentFile.mkdirs() }.writeText("struct ScreenRegistry {}\n")
        }
    }

    private val File.stringsFile
        get() = resolve("ui/resources/src/commonMain/composeResources/values/strings.xml")

    private val catalogFile
        get() = projectDir.resolve("iosApp/iosApp/Localizable.xcstrings")

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    private fun runner(vararg tasks: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*tasks, "--configuration-cache")

    @Test
    fun `generating writes every file, and the check then passes as part of check`() {
        runner(GENERATE_IOS_RESOURCES_TASK_NAME).build()
        assertContains(catalogFile.readText(), "Sign in with your account.")
        assertTrue(projectDir.resolve("iosApp/iosApp/Design/Generated/AppIcon.swift").isFile)
        assertTrue(
            projectDir.resolve("iosApp/iosApp/Generated/Drawables.xcassets/Drawable/directions_run.imageset/directions_run.svg").isFile,
        )

        val result = runner("check").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":$CHECK_IOS_RESOURCES_TASK_NAME")?.outcome)
    }

    @Test
    fun `a reworded shared string fails the check, naming the file and the task`() {
        runner(GENERATE_IOS_RESOURCES_TASK_NAME).build()
        projectDir.stringsFile.writeText(strings("""<string name="login_intro">Sign in.</string>"""))

        val result = runner(CHECK_IOS_RESOURCES_TASK_NAME).buildAndFail()
        assertContains(result.output, "Localizable.xcstrings")
        assertContains(result.output, "./gradlew $GENERATE_IOS_RESOURCES_TASK_NAME")
    }

    @Test
    fun `a generated file edited by hand fails the check`() {
        runner(GENERATE_IOS_RESOURCES_TASK_NAME).build()
        runner(CHECK_IOS_RESOURCES_TASK_NAME).build()
        catalogFile.writeText(catalogFile.readText().replace("Sign in with your account.", "Sign in!"))

        val result = runner(CHECK_IOS_RESOURCES_TASK_NAME).buildAndFail()
        assertContains(result.output, "Localizable.xcstrings")
    }

    @Test
    fun `an image whose drawable is gone fails the check and is removed by generating`() {
        runner(GENERATE_IOS_RESOURCES_TASK_NAME).build()
        val drawables = projectDir.resolve("ui/resources/src/commonMain/composeResources/drawable")
        drawables.resolve("pool.xml").writeText(run)
        runner(GENERATE_IOS_RESOURCES_TASK_NAME).build()
        drawables.resolve("pool.xml").delete()

        val result = runner(CHECK_IOS_RESOURCES_TASK_NAME).buildAndFail()
        assertContains(result.output, "pool.imageset")

        runner(GENERATE_IOS_RESOURCES_TASK_NAME).build()
        assertFalse(projectDir.resolve("iosApp/iosApp/Generated/Drawables.xcassets/Drawable/pool.imageset").exists())
        runner(CHECK_IOS_RESOURCES_TASK_NAME).build()
    }
}
