package io.github.solcott.activitytracker.app.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** A choice of text read by a platform's strings (dev/shared-display-text). */
class AppTextTest {
    /** Strings as a platform holds them, with `{n}` for a placeholder so the test owns the format. */
    private val source =
        object : TextSource {
            private val strings =
                mapOf("unit_distance_mi" to "{1} mi", "error_with_reason" to "{1}. {2}", "error_load_feed" to "Feed failed")
            private val plurals = mapOf("like_count" to ("{1} like" to "{1} likes"))

            override fun string(name: String, args: List<String>): String? = strings[name]?.let { fill(it, args) }

            override fun plural(name: String, quantity: Int, args: List<String>): String? =
                plurals[name]?.let { (one, other) -> fill(if (quantity == 1) one else other, args) }

            override fun time(epochSeconds: Long, utcOffsetMinutes: Int?): String =
                "time $epochSeconds" + utcOffsetMinutes?.let { " $it" }.orEmpty()

            private fun fill(template: String, args: List<String>): String =
                args.foldIndexed(template) { index, text, arg -> text.replace("{${index + 1}}", arg) }
        }

    @Test
    fun aTimeIsFormattedByTheSource() {
        assertEquals("time 1700000000", AppText.Time(1_700_000_000).resolve(source))
        assertEquals(
            "Paused. time 1700000000",
            AppText.named("error_with_reason", AppText.written("Paused"), AppText.Time(1_700_000_000)).resolve(source),
        )
    }

    @Test fun writtenTextReadsAsItIs() = assertEquals("alice.test", AppText.written("alice.test").resolve(source))

    @Test
    fun aNamedStringTakesItsArguments() =
        assertEquals("3.00 mi", AppText.named("unit_distance_mi", AppText.written("3.00")).resolve(source))

    @Test
    fun argumentsAreResolvedFirst() =
        assertEquals(
            "Feed failed. Offline",
            AppText.named("error_with_reason", AppText.named("error_load_feed"), AppText.written("Offline")).resolve(source),
        )

    @Test
    fun aPluralReadsInTheFormItsQuantitySelects() {
        assertEquals("1 like", AppText.Plural("like_count", 1, argsOf("1")).resolve(source))
        assertEquals("3 likes", AppText.Plural("like_count", 3, argsOf("3")).resolve(source))
    }

    @Test
    fun aNameWithNoStringReadsAsItsFallback() {
        assertEquals("Quidditch", AppText.Named("sport_quidditch", fallback = AppText.written("Quidditch")).resolve(source))
        assertEquals("Feed failed", AppText.Named("sensor_x", fallback = AppText.named("error_load_feed")).resolve(source))
    }

    @Test
    fun withoutAFallbackItReadsAsTheName() {
        assertEquals("no_such_string", AppText.named("no_such_string").resolve(source))
        assertEquals("no_such_plural", AppText.Plural("no_such_plural", 2).resolve(source))
    }

    @Test
    fun equalChoicesAreEqual() =
        assertEquals(AppText.named("unit_distance_mi", AppText.written("3.00")), AppText.named("unit_distance_mi", AppText.written("3.00")))

    private fun argsOf(vararg texts: String) = kotlinx.collections.immutable.persistentListOf(*texts.map(AppText::written).toTypedArray())
}
