package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText

/**
 * What a segment's data credit says (segments/segment-records, dev/shared-display-text), for every page that draws its line: the segment
 * page, its enlarged map. Both UIs ask it.
 */
object SegmentCreditTexts {
    /** Whether [credit]'s first line is OpenStreetMap's credit, which links to `PlaceTexts.OSM_COPYRIGHT_URL`. */
    fun links(credit: SegmentCredit): Boolean = credit is SegmentCredit.OpenStreetMap

    /**
     * [credit]'s lines in order: OpenStreetMap's credit or the agency's line for those sources, then the licence when the record names one
     * ("Data licence: ODbL-1.0").
     */
    fun lines(credit: SegmentCredit): List<AppText> {
        val source =
            when (credit) {
                is SegmentCredit.OpenStreetMap -> PlaceTexts.osmCredit
                is SegmentCredit.Agency ->
                    credit.attribution?.let { AppText.named("segment_credit_agency_named", AppText.written(it)) }
                        ?: AppText.named("segment_credit_agency")
                is SegmentCredit.License -> null
            }
        val license =
            when (credit) {
                is SegmentCredit.OpenStreetMap -> credit.license
                is SegmentCredit.Agency -> credit.license
                is SegmentCredit.License -> credit.id
            }
        return listOfNotNull(source, PlaceTexts.license(license))
    }
}
