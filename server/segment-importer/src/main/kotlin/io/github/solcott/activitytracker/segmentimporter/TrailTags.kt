package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup

/**
 * Which OSM features are trails for which sport group (segments/trail-candidates), in one place so tuning edits one table. A way is a trail
 * on its own only with a name; inside a named route relation, any way that isn't [excluded] is a member.
 */
internal object TrailTags {
    private val ROADS =
        setOf(
            "motorway",
            "motorway_link",
            "trunk",
            "trunk_link",
            "primary",
            "primary_link",
            "secondary",
            "secondary_link",
            "tertiary",
            "tertiary_link",
            "unclassified",
            "residential",
            "service",
            "living_street",
            "road",
            "busway",
        )
    private val RIDE_WAYS = setOf("path", "track", "bridleway")
    private val FOOT_WAYS = setOf("path", "footway", "track", "bridleway")
    private val CLOSED_ACCESS = setOf("private", "no")
    private val NOT_A_TRAIL_FOOTWAY = setOf("sidewalk", "crossing")
    private val BIKES_ALLOWED = setOf("yes", "designated")
    private val FOOT_GROUPS = setOf(SegmentSportGroup.RUN, SegmentSportGroup.HIKE)

    /** Route relation `route` values and the groups they're trails for. */
    private val ROUTES =
        mapOf(
            "mtb" to setOf(SegmentSportGroup.RIDE),
            "bicycle" to setOf(SegmentSportGroup.RIDE),
            "hiking" to FOOT_GROUPS,
            "foot" to FOOT_GROUPS,
            "running" to FOOT_GROUPS,
            "ski" to setOf(SegmentSportGroup.SKI),
            "canoe" to setOf(SegmentSportGroup.PADDLE),
        )

    /** Whether a way can never be part of a trail: closed to the public, an area, a sidewalk or crossing, a road, steps, a passage. */
    fun excluded(tags: Map<String, String>): Boolean =
        tags["access"] in CLOSED_ACCESS ||
            tags["area"] == "yes" ||
            tags["footway"] in NOT_A_TRAIL_FOOTWAY ||
            tags["highway"] in ROADS ||
            tags["highway"] == "steps" ||
            tags["tunnel"] == "building_passage"

    /** The groups a way is a trail for on its own: none without a name or when [excluded]. */
    fun wayGroups(tags: Map<String, String>): Set<String> {
        if (tags["name"].isNullOrBlank() || excluded(tags)) return emptySet()
        val highway = tags["highway"]
        return buildSet {
            val bikes =
                highway == "cycleway" ||
                    (highway in RIDE_WAYS && tags["bicycle"] != "no") ||
                    (highway == "footway" && tags["bicycle"] in BIKES_ALLOWED) ||
                    "mtb:scale" in tags
            if (bikes) add(SegmentSportGroup.RIDE)
            if ((highway in FOOT_WAYS && tags["foot"] != "no") || "sac_scale" in tags) addAll(FOOT_GROUPS)
            if (tags["piste:type"] == "nordic") add(SegmentSportGroup.SKI)
        }
    }

    /** The groups a route relation is a trail for: none without a name. */
    fun relationGroups(tags: Map<String, String>): Set<String> =
        if (tags["type"] != "route" || tags["name"].isNullOrBlank()) emptySet() else ROUTES[tags["route"]].orEmpty()

    /** Whether a named way marks junctions: any named highway, or a named way that's a trail itself. */
    fun marksJunctions(tags: Map<String, String>): Boolean =
        !tags["name"].isNullOrBlank() && ("highway" in tags || wayGroups(tags).isNotEmpty())

    /**
     * Which ways a way may be travelled for [group], along its own node order: forward and backward. `oneway=yes|true|1` allows forward
     * only, `-1` backward only; for rides `oneway:bicycle` overrides `oneway`.
     */
    fun directions(tags: Map<String, String>, group: String): Pair<Boolean, Boolean> {
        val oneway = (if (group == SegmentSportGroup.RIDE) tags["oneway:bicycle"] else null) ?: tags["oneway"]
        return when (oneway) {
            "yes",
            "true",
            "1" -> true to false
            "-1" -> false to true
            else -> true to true
        }
    }
}
