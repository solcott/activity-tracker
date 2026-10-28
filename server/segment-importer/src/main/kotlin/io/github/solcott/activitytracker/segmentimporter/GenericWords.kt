package io.github.solcott.activitytracker.segmentimporter

/**
 * The generic words of one [language] that names drop from their end (segments/trail-candidates), such as "Trail" in "Rattler Trail". A
 * language with no list ([known] false) keeps every name as it is.
 */
internal class GenericWords private constructor(val language: String, private val words: List<String>, private val articles: Set<String>) {
    val known: Boolean
        get() = words.isNotEmpty()

    /**
     * [name], one OSM, route or agency name, without a trailing generic word: from each side of a `<Parent> - <Section>` name, and from
     * before a trailing parenthesised qualifier.
     */
    fun shorten(name: String): String {
        val split = name.indexOf(SECTION)
        if (split <= 0) return shortenPart(name)
        return shortenPart(name.substring(0, split)) + SECTION + shortenPart(name.substring(split + SECTION.length))
    }

    private fun shortenPart(part: String): String {
        val qualifier = QUALIFIER.find(part)?.takeIf { it.range.first > 0 } ?: return dropWord(part)
        return dropWord(part.substring(0, qualifier.range.first)) + part.substring(qualifier.range.first)
    }

    /** [core] without its last word when that's a generic word, unless nothing, or only an article, would be left. */
    private fun dropWord(core: String): String {
        val trimmed = core.trimEnd()
        // What's left without the first generic word that ends it as a whole word.
        val kept = words.firstNotNullOfOrNull { word ->
            trimmed
                .takeIf { it.endsWith(word, ignoreCase = true) }
                ?.dropLast(word.length)
                ?.takeIf { it.lastOrNull()?.isWhitespace() == true }
                ?.trimEnd()
        }
        return if (kept.isNullOrEmpty() || kept.lowercase() in articles) core else kept
    }

    companion object {
        private const val SECTION = " - "

        /** A trailing qualifier such as " (Lower)". */
        private val QUALIFIER = Regex("""\s+\([^()]*\)$""")

        /** Each language's generic words, longest first so "Foot Path" goes before "Path", and its articles. */
        private val LISTS = mapOf("en" to (listOf("Foot Path", "Footpath", "Section", "Trail", "Path") to setOf("the", "a", "an")))

        val ENGLISH: GenericWords = forLanguage("en")

        /** The words for the ISO 639-1 [code], or none for a language with no list. */
        fun forLanguage(code: String): GenericWords {
            val (words, articles) = LISTS[code] ?: (emptyList<String>() to emptySet())
            return GenericWords(code, words, articles)
        }
    }
}
