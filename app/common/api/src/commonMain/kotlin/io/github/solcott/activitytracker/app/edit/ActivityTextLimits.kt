package io.github.solcott.activitytracker.app.edit

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord

/**
 * How long an activity's title and description may be, on every form that writes them (edit, import, manual entry, Record), for the shared
 * and the native form alike. Text is counted as it saves, trimmed, in characters ([ActivityRecord.textLength]), so what fits here fits the
 * lexicon. A field over its limit shows its counter in the error colour and holds Save back, and nothing refuses typing, so text already
 * too long (an imported file's notes) can still be shortened.
 */
object ActivityTextLimits {
    const val TITLE = ActivityRecord.MAX_TITLE_GRAPHEMES
    const val DESCRIPTION = ActivityRecord.MAX_DESCRIPTION_GRAPHEMES

    /** A counter shows from this share of a limit, so a field far from it stays quiet. */
    private const val COUNTER_FROM = 0.9

    /** How long [text] is as it would be saved. */
    fun length(text: String): Int = ActivityRecord.textLength(text.trim())

    fun titleFits(text: String): Boolean = length(text) <= TITLE

    fun descriptionFits(text: String): Boolean = length(text) <= DESCRIPTION

    /** Both fit, so the form may save. */
    fun fits(title: String, description: String): Boolean = titleFits(title) && descriptionFits(description)

    /** "95/100" under the title once it nears or passes its limit; null before that. */
    fun titleCounter(text: String): AppText? = counter(text, TITLE)

    /** "950/1000" under the description once it nears or passes its limit; null before that. */
    fun descriptionCounter(text: String): AppText? = counter(text, DESCRIPTION)

    private fun counter(text: String, max: Int): AppText? {
        val length = length(text)
        return if (length < max * COUNTER_FROM) {
            null
        } else {
            AppText.named("details_length", AppText.written(formatWhole(length.toLong())), AppText.written(formatWhole(max.toLong())))
        }
    }
}
