package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus

/**
 * What a place's page says about where it came from and what state it's in, for a segment's page and a dive site's alike
 * (dev/shared-display-text): who created it, its data credit and licence, and its status with what that means.
 */
object PlaceTexts {
    /** "By @handle". */
    fun createdBy(handle: String): AppText = AppText.named("segment_created_by", LabelText.handle(handle))

    /** OpenStreetMap's credit, for a place imported from it; it links to [OSM_COPYRIGHT_URL]. */
    val osmCredit: AppText = AppText.named("segment_credit_osm")

    /** OSM's attribution guideline asks for a link to this page. */
    const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

    /** The data's licence ("Data licence: ODbL-1.0"); null when the record names none. */
    fun license(license: String): AppText? =
        license.takeIf { it.isNotBlank() }?.let { AppText.named("segment_credit_license", AppText.written(it)) }

    /** A [status] as a label ("Proposed"); null for an active place, or one with no status. */
    fun status(status: String?): AppText? =
        when (SegmentStatus.of(status)) {
            SegmentStatus.PROPOSED -> AppText.named("segment_status_proposed")
            SegmentStatus.DUPLICATE -> AppText.named("segment_status_duplicate")
            SegmentStatus.ARCHIVED -> AppText.named("segment_status_archived")
            else -> null
        }

    /**
     * What [status] means for this kind of place: the string named [proposedHelp], [duplicateHelp] or [archivedHelp] (a segment's and a
     * site's say it in their own words); null for an active place.
     */
    fun statusHelp(status: String?, proposedHelp: String, duplicateHelp: String, archivedHelp: String): AppText? =
        when (SegmentStatus.of(status)) {
            SegmentStatus.PROPOSED -> AppText.named(proposedHelp)
            SegmentStatus.DUPLICATE -> AppText.named(duplicateHelp)
            SegmentStatus.ARCHIVED -> AppText.named(archivedHelp)
            else -> null
        }

    /** Open the original is offered for a duplicate whose original is named ([hasOriginal]). */
    fun offersOriginal(status: String?, hasOriginal: Boolean): Boolean = SegmentStatus.of(status) == SegmentStatus.DUPLICATE && hasOriginal

    /** "Open the original". */
    val openOriginal: AppText = AppText.named("segment_open_original")
}
