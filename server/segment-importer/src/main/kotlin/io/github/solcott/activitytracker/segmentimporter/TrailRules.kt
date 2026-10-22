package io.github.solcott.activitytracker.segmentimporter

/**
 * What a source's ways are trails for and how they connect (segments/trail-candidates), read from a way's tags: [TrailTags] for OSM ways,
 * [AgencyRules] for an agency dataset's. Each [OsmWay] names its own, so trail building and cutting treat every source alike.
 */
internal interface TrailRules {
    /** The `feed.segment` source of this source's candidates. */
    val source: String

    /** The SPDX identifier of this source's data. */
    val license: String

    /** Who to credit for this source's data, if anyone. */
    val attribution: String?

    /** Whether a way can never be part of a trail, even inside a route relation. */
    fun excluded(tags: Map<String, String>): Boolean

    /** The sport groups a way is a trail for on its own: none without a name or when [excluded]. */
    fun wayGroups(tags: Map<String, String>): Set<String>

    /** Whether a named way marks junctions on the trails it meets. */
    fun marksJunctions(tags: Map<String, String>): Boolean

    /** Which ways a way may be travelled for [group], along its own node order: forward and backward. */
    fun directions(tags: Map<String, String>, group: String): Pair<Boolean, Boolean>
}
