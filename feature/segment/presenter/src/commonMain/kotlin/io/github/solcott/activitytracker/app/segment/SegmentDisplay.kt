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
internal fun creditOf(record: SegmentRecord): SegmentCredit? = creditOf(record.source, record.license, record.attribution)

/** Credit for a line's data from [source] under [license], as [creditOf] a segment's: a trail's or road's too (segments/path-page). */
internal fun creditOf(source: String, license: String, attribution: String?): SegmentCredit? {
    val trimmed = license.trim()
    return when (source) {
        SegmentSource.ATHLETE -> null
        SegmentSource.OSM -> SegmentCredit.OpenStreetMap(trimmed)
        SegmentSource.AGENCY -> SegmentCredit.Agency(trimmed, attribution?.trim()?.takeIf { it.isNotEmpty() })
        else -> trimmed.takeIf { it.isNotEmpty() }?.let(SegmentCredit::License)
    }
}
