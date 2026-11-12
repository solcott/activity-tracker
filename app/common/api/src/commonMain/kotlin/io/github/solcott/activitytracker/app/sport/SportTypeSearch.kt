package io.github.solcott.activitytracker.app.sport

import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.TextSource
import io.github.solcott.activitytracker.app.text.resolve
import io.github.solcott.activitytracker.lexicon.app.ActivityType

/** A type picker's search, the rule both UIs ask (dev/shared-display-text). */
object SportTypeSearch {
    /** Whether a picker with [query] is searching: anything but blanks typed. */
    fun searching(query: String): Boolean = query.isNotBlank()

    /**
     * Whether [type] matches [query]: [query], trimmed, is part of the type's label ([typeLabel]), its sport's label ([sportLabel]), or
     * either token, ignoring case. A blank query matches everything. Tokens match too, so "sup" finds Stand Up Paddle; "mtb" finds nothing.
     */
    fun matches(type: ActivityType, query: String, typeLabel: String?, sportLabel: String?): Boolean {
        val needle = query.trim()
        return needle.isEmpty() ||
            listOfNotNull(typeLabel, sportLabel, type.sport, type.subSport).any { it.contains(needle, ignoreCase = true) }
    }

    /** [matches] with the labels as [source]'s strings read them, for a UI that resolves text outside composition. */
    fun matches(type: ActivityType, query: String, source: TextSource): Boolean =
        matches(
            type,
            query,
            LabelText.activityType(type).resolve(source),
            LabelText.activityType(ActivityType(type.sport)).resolve(source),
        )
}
