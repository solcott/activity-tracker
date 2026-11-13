package io.github.solcott.activitytracker.app.text

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Text for a UI to show, as a choice rather than a string: which shared string, with what in its placeholders. Code that every client runs
 * makes the choice, where its rule is written once (a quantity with its unit, a label looked up by a token, a failure with its reason), and
 * each UI only resolves it to the string its platform holds: Compose resources in the shared UI, the String Catalog in the native iOS one
 * (dev/shared-display-text).
 */
@Immutable
sealed interface AppText {
    /**
     * The shared string named [name] (a `<string>` of `strings.xml`) with [args] in its placeholders, in order. Where no string has that
     * name, as for a token from an open set that the app doesn't name, it reads as [fallback], and without one as the name itself.
     */
    @Immutable
    data class Named(val name: String, val args: ImmutableList<AppText> = persistentListOf(), val fallback: AppText? = null) : AppText

    /** The shared plural named [name] in the form [quantity] selects, with [args] in its placeholders. */
    @Immutable data class Plural(val name: String, val quantity: Int, val args: ImmutableList<AppText> = persistentListOf()) : AppText

    /** [text] as it is: a formatted number, a handle, something an athlete wrote. */
    @Immutable data class Written(val text: String) : AppText

    /**
     * The moment [epochSeconds] as a time of day on the device's 12- or 24-hour clock ("7:05 AM", "19:05"): in the UTC offset
     * [utcOffsetMinutes] when given (an activity's own clock, feed/activity-start-time), else in the device's time zone. Each UI formats
     * it, since the clock is the device's setting.
     */
    @Immutable data class Time(val epochSeconds: Long, val utcOffsetMinutes: Int? = null) : AppText

    companion object {
        /** The shared string named [name] with [args] in its placeholders. */
        fun named(name: String, vararg args: AppText): AppText = Named(name, args.toList().toImmutableList())

        /** [text] as it is. */
        fun written(text: String): AppText = Written(text)

        /** [texts] as a list, each next one joined on by `text_list_pair` ("Ride, Run, Swim"); null for none. */
        fun joined(texts: List<AppText>): AppText? = texts.reduceOrNull { list, next -> named("text_list_pair", list, next) }

        /** [texts] as sentences of one paragraph, each next one joined on by `text_sentence_pair` (a space); null for none. */
        fun sentences(texts: List<AppText>): AppText? = texts.reduceOrNull { first, next -> named("text_sentence_pair", first, next) }

        /** [texts] as paragraphs, each next one joined on by `text_paragraph_pair` (a blank line); null for none. */
        fun paragraphs(texts: List<AppText>): AppText? = texts.reduceOrNull { first, next -> named("text_paragraph_pair", first, next) }
    }
}

/**
 * A platform's strings, for [resolve]: what turns a name and finished arguments into text. The shared UI has its own resolver, which must
 * run in composition; this is for a UI that reads its strings outside one, as the native iOS UI does.
 */
interface TextSource {
    /** The string named [name] with [args] in its placeholders, or null when no string has that name. */
    fun string(name: String, args: List<String>): String?

    /** The plural named [name] in the form [quantity] selects, with [args] in its placeholders, or null when no plural has that name. */
    fun plural(name: String, quantity: Int, args: List<String>): String?

    /**
     * The moment [epochSeconds] as a time of day on the device's 12- or 24-hour clock, in the UTC offset [utcOffsetMinutes] when given,
     * else in the device's time zone.
     */
    fun time(epochSeconds: Long, utcOffsetMinutes: Int?): String
}

/** This text as [source]'s strings read it. A name [source] doesn't have reads as its fallback, or as the name. */
fun AppText.resolve(source: TextSource): String =
    when (this) {
        is AppText.Written -> text
        is AppText.Named -> source.string(name, args.map { it.resolve(source) }) ?: fallback?.resolve(source) ?: name
        is AppText.Plural -> source.plural(name, quantity, args.map { it.resolve(source) }) ?: name
        is AppText.Time -> source.time(epochSeconds, utcOffsetMinutes)
    }
