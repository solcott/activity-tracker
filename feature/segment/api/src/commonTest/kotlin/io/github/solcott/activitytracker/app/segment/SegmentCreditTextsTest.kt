package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a segment's data credit says (segments/segment-records, dev/shared-display-text). */
class SegmentCreditTextsTest {
    private fun license(id: String) = AppText.named("segment_credit_license", AppText.written(id))

    @Test
    fun openStreetMapLinksItsCreditThenTheLicence() {
        val credit = SegmentCredit.OpenStreetMap("ODbL-1.0")
        assertTrue(SegmentCreditTexts.links(credit))
        assertEquals(listOf(AppText.named("segment_credit_osm"), license("ODbL-1.0")), SegmentCreditTexts.lines(credit))
    }

    @Test
    fun anAgencyIsNamedWhenTheRecordNamesIt() {
        val credit = SegmentCredit.Agency("PD", "USFS")
        assertFalse(SegmentCreditTexts.links(credit))
        assertEquals(
            listOf(AppText.named("segment_credit_agency_named", AppText.written("USFS")), license("PD")),
            SegmentCreditTexts.lines(credit),
        )
        assertEquals(listOf(AppText.named("segment_credit_agency")), SegmentCreditTexts.lines(SegmentCredit.Agency("")))
    }

    @Test
    fun anotherSourceShowsOnlyItsLicence() {
        assertEquals(listOf(license("CC0-1.0")), SegmentCreditTexts.lines(SegmentCredit.License("CC0-1.0")))
    }
}
