package io.github.solcott.activitytracker.location

import kotlin.math.abs

/**
 * Whether a segment's line clearly climbs or descends (segments/segment-direction): by its net change, gain less loss, at least [MIN_M] and
 * at least [MIN_GRADE] of its length. [word] is the English word the segment importer adds to a two-way trail's name.
 */
enum class SegmentClimb(val word: String) {
    Uphill("uphill"),
    Downhill("downhill");

    /** The same line run the other way. */
    fun reversed(): SegmentClimb = if (this == Uphill) Downhill else Uphill

    companion object {
        /** The least net climb or descent, in meters, that counts: five times [ElevationGain]'s hysteresis, so noise never does. */
        const val MIN_M = 15L

        /** The least net grade that counts: 3%, clearly felt on a bike or on foot, so rolling lines whose ups and downs cancel don't. */
        const val MIN_GRADE = 0.03

        /** The climb named by [word] (a `feed.segment#climb` value), if it is one of [entries]' words. */
        fun ofWord(word: String?): SegmentClimb? = entries.firstOrNull { it.word == word }

        /** Which way a line of [lengthM] meters that gains [gainM] and loses [lossM] meters clearly goes; null when neither or unknown. */
        fun of(gainM: Long?, lossM: Long?, lengthM: Long): SegmentClimb? {
            if (gainM == null || lossM == null || lengthM <= 0) return null
            val net = gainM - lossM
            val clear = abs(net) >= MIN_M && abs(net) >= MIN_GRADE * lengthM
            return when {
                !clear -> null
                net > 0 -> Uphill
                else -> Downhill
            }
        }
    }
}

/**
 * Which way a segment runs, as words riders use (segments/segment-direction): uphill or downhill when its line clearly climbs or descends,
 * otherwise its compass heading, and always its turn for a loop. [word] is the English word the segment importer adds to a two-way trail's
 * name; apps show their own labels.
 */
enum class SegmentDirection(val word: String) {
    Uphill(SegmentClimb.Uphill.word),
    Downhill(SegmentClimb.Downhill.word),
    Northbound(SegmentHeading.Northbound.word),
    Eastbound(SegmentHeading.Eastbound.word),
    Southbound(SegmentHeading.Southbound.word),
    Westbound(SegmentHeading.Westbound.word),
    Clockwise(SegmentHeading.Clockwise.word),
    Counterclockwise(SegmentHeading.Counterclockwise.word);

    companion object {
        /** The direction named by [word], if it is one of [entries]' words. */
        fun ofWord(word: String): SegmentDirection? = entries.firstOrNull { it.word == word }

        /** A line heading [heading] that [climb]s: its turn for a loop, else its climb when it has one, else its heading. */
        fun of(heading: SegmentHeading, climb: SegmentClimb?): SegmentDirection =
            when {
                heading == SegmentHeading.Clockwise || heading == SegmentHeading.Counterclockwise || climb == null -> of(heading)
                climb == SegmentClimb.Uphill -> Uphill
                else -> Downhill
            }

        /** [points]' direction, judged by their heading and by a gain of [gainM] and a loss of [lossM] meters over [lengthM] meters. */
        fun of(points: List<GeoPoint>, gainM: Long?, lossM: Long?, lengthM: Long): SegmentDirection =
            of(SegmentHeading.of(points), SegmentClimb.of(gainM, lossM, lengthM))

        private fun of(heading: SegmentHeading): SegmentDirection = entries.first { it.word == heading.word }
    }
}
