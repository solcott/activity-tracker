package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.BoxedLineTable
import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.e7ToDegrees
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.select

/** Rows of [table] whose stored bounding box overlaps [box]: their line may pass through it. */
internal fun boxOverlaps(table: BoxedLineTable, box: SearchBox): Op<Boolean> =
    (table.minLatE7 lessEq box.maxLat) and
        (table.maxLatE7 greaterEq box.minLat) and
        (table.minLngE7 lessEq box.maxLng) and
        (table.maxLngE7 greaterEq box.minLng)

/**
 * Segments whose stored box overlaps [box] and whose line passes through one of its 0.01° cells. Both only narrow it down: whether the line
 * itself crosses the box is left to the caller.
 */
internal fun segmentsNear(box: SearchBox): Op<Boolean> {
    val cells = SegmentCells.cellsIn(box.minLat.e7ToDegrees(), box.minLng.e7ToDegrees(), box.maxLat.e7ToDegrees(), box.maxLng.e7ToDegrees())
    return boxOverlaps(Segments, box) and
        exists(
            SegmentCellIndex.select(SegmentCellIndex.cell).where {
                (SegmentCellIndex.segmentUri eq Segments.uri) and (SegmentCellIndex.cell inList cells)
            },
        )
}

/** Rows whose point, [lat] and [lng] in degrees × 10^7, lies in [box], edges included. */
internal fun pointInBox(lat: Column<Int>, lng: Column<Int>, box: SearchBox): Op<Boolean> =
    (lat greaterEq box.minLat) and (lat lessEq box.maxLat) and (lng greaterEq box.minLng) and (lng lessEq box.maxLng)
