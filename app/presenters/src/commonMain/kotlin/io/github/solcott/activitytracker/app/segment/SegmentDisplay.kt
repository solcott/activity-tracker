package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.location.SegmentGeometry

/** A segment's precision-6 [line] at precision 5, for the maps; null when it doesn't decode. */
internal fun mapLine(line: String): String? = SegmentGeometry.decodePolyline(line)?.let(SegmentGeometry::toPrecision5)

/**
 * Credit for [record]'s data by its source: none for an athlete's, and only a non-blank licence for a source we don't know. Its attribution
 * names the agency for an agency's only, so no one else can put their own text in a credit.
 */
internal fun creditOf(record: SegmentRecord): SegmentCredit? {
    val license = record.license.trim()
    return when (record.source) {
        SegmentSource.ATHLETE -> null
        SegmentSource.OSM -> SegmentCredit.OpenStreetMap(license)
        SegmentSource.AGENCY -> SegmentCredit.Agency(license, record.attribution?.trim()?.takeIf { it.isNotEmpty() })
        else -> license.takeIf { it.isNotEmpty() }?.let(SegmentCredit::License)
    }
}
