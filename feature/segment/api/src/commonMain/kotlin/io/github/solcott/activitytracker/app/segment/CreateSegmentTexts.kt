package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.SegmentView

/**
 * What the create-segment form says (segments/segment-creation, dev/shared-display-text), chosen here for the shared and the native form.
 */
object CreateSegmentTexts {
    /** The slider's title. */
    val range: AppText = AppText.named("segment_create_range")

    /** The slider's two ends, as assistive technology names them. */
    val startThumb: AppText = AppText.named("segment_create_start")
    val endThumb: AppText = AppText.named("segment_create_end")

    /** Where an end of the stretch is: "480 m along the route". */
    fun along(meters: Double, units: UnitSystem): AppText = AppText.named("segment_create_along", StatText.distance(meters, units))

    /**
     * What the chosen stretch is: its length and sport group ("960 m · Ride"), or why it can't be a segment (too short for its sport group,
     * too long, or across a privacy zone, a hidden end or a break). [selectionFails] says which.
     */
    fun selection(state: CreateSegmentState, units: UnitSystem): AppText {
        val sport = state.sport?.let(SegmentTexts::sportGroup) ?: AppText.written("")
        return when (val selection = state.selection) {
            is SelectionCheck.Ok -> AppText.named("segment_create_length", StatText.distance(selection.meters, units), sport)
            is SelectionCheck.TooShort ->
                AppText.named("segment_create_too_short", sport, StatText.distance(selection.minMeters.toDouble(), units))
            is SelectionCheck.TooLong -> AppText.named("segment_create_too_long", StatText.distance(selection.maxMeters.toDouble(), units))
            SelectionCheck.CrossesGap -> AppText.named("segment_create_crosses_gap")
        }
    }

    /** Whether [selection] says why the stretch can't be a segment, which the forms show as an error. */
    fun selectionFails(state: CreateSegmentState): Boolean = state.selection !is SelectionCheck.Ok

    /** Why no segment can be made from the activity. */
    fun unavailable(reason: CreateSegmentUnavailable): AppText =
        AppText.named(
            when (reason) {
                CreateSegmentUnavailable.NoRoute -> "segment_create_no_route"
                CreateSegmentUnavailable.NoSportGroup -> "segment_create_no_sport"
                CreateSegmentUnavailable.AllHidden -> "segment_create_all_hidden"
            },
        )

    /** What publishing makes public, then, when the segment [namesActivity], that it links to the activity: sentences read in order. */
    fun publicNote(namesActivity: Boolean): List<AppText> =
        listOfNotNull(
            AppText.named("segment_create_public"),
            AppText.named("segment_create_links_activity").takeIf { namesActivity },
        )

    /** An existing segment the stretch duplicates: its name and length ("Ridge climb · 1.0 km"). */
    fun duplicate(segment: SegmentView, units: UnitSystem): AppText =
        AppText.named("text_dot_pair", AppText.written(segment.currentName), StatText.distance(segment.distanceMeters.toDouble(), units))
}
