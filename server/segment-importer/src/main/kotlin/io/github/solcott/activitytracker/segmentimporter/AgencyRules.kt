package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.TrailHorse
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Which features of a public agency's trail dataset are trails for which sport group (segments/trail-candidates), one entry per dataset
 * kind an operator names with `--agency <kind>=<file>`. [AgencyReader] reads a feature's [properties][featureKey] (keys lower-cased, since
 * the published files use upper case and the agency's map service lower case) into the tags of an [OsmWay] these rules then read: its
 * [NAME], its [GROUPS] and its [KEY]. Agency trails go both ways, and are never excluded once read.
 */
internal enum class AgencyRules(val kind: String, override val attribution: String) : TrailRules {
    /** The Forest Service's National Forest System trails (`TrailNFS_Publish`): one trail is several features, split at mileposts. */
    USFS("usfs", "USDA Forest Service") {
        override fun nameOf(properties: Map<String, String?>): String? = properties["trail_name"]

        override fun featureKey(properties: Map<String, String?>): String? {
            val cn = properties["trail_cn"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val bmp = properties["bmp"]?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            return "$cn@${bmp.setScale(MILEPOST_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}"
        }

        /** A use counts when its managed, accepted or accepted/discouraged field holds a season; discouraged or restricted don't. */
        override fun groupsOf(properties: Map<String, String?>): Set<String> =
            USFS_USES.filterKeys { usfsAllowed(properties, it) }.values.flatten().toSet()

        /** A use closes when it's restricted all year and allowed in no season. */
        override fun closedTo(properties: Map<String, String?>): Set<String> =
            USFS_USES.filterKeys { use -> properties["${use}_restricted"]?.trim() == USFS_ALL_YEAR && !usfsAllowed(properties, use) }
                .values
                .flatten()
                .toSet()

        // A getter: the companion's tables aren't initialized while the entries are.
        override val attributeFields: List<String>
            get() =
                listOf("trail_surface", "trail_class", "typical_tread_width", "typical_trail_grade", "accessibility_status") +
                    (USFS_USES.keys + PACK_SADDLE).flatMap { use -> USFS_SEASONS.map { "${use}_$it" } } +
                    "${PACK_SADDLE}_restricted"

        override fun facts(tags: Map<String, String>, group: String?): WayFacts {
            val fields = tags.attributeFields()
            return WayFacts(
                surface = AgencyFacts.usfsSurface(fields["trail_surface"]),
                trailClass = AgencyFacts.trailClass(fields["trail_class"]),
                widthCm = AgencyFacts.usfsWidth(fields["typical_tread_width"]),
                gradePercent = AgencyFacts.usfsGrade(fields["typical_trail_grade"]),
                wheelchair = AgencyFacts.usfsAccessibility(fields["accessibility_status"]),
                horse =
                    when {
                        usfsSeason(fields, "${PACK_SADDLE}_managed") != null -> TrailHorse.DESIGNATED
                        usfsAllowed(fields, PACK_SADDLE) -> TrailHorse.YES
                        fields["${PACK_SADDLE}_restricted"]?.trim() == USFS_ALL_YEAR -> TrailHorse.NO
                        else -> null
                    },
                open =
                    USFS_USES.entries
                        .firstOrNull { group in it.value }
                        ?.key
                        // The first season field that reads as dates, so an unreadable managed season falls back to the accepted one.
                        ?.let { use -> USFS_SEASONS.firstNotNullOfOrNull { usfsSeason(fields, "${use}_$it")?.let(FactDates::slashRange) } },
            )
        }
    },

    /** The Park Service's public trails (`NPS_Public_Trails`), whose listed uses are free text in practice. */
    NPS("nps", "National Park Service") {
        override fun nameOf(properties: Map<String, String?>): String? = properties["trlname"]

        override fun featureKey(properties: Map<String, String?>): String? = properties["geometryid"]?.trim()?.takeIf { it.isNotEmpty() }

        /** Existing, shown on public maps and not closed to the public, by the uses `TRLUSE` lists. */
        override fun groupsOf(properties: Map<String, String?>): Set<String> {
            fun field(name: String) = properties[name]?.trim().orEmpty()
            val open =
                field("trlstatus").equals("Existing", ignoreCase = true) &&
                    field("publicdisplay").equals("Public Map Display", ignoreCase = true) &&
                    !field("opentopublic").equals("No", ignoreCase = true)
            if (!open) return emptySet()
            return field("trluse")
                .split('|', ',', '/')
                .flatMap { use -> NPS_USES[use.filter(Char::isLetter).lowercase()].orEmpty() }
                .toSet()
        }

        override val attributeFields = listOf("trlsurface", "trlclass", "seasonal", "maintainer")

        override fun facts(tags: Map<String, String>, group: String?): WayFacts {
            val fields = tags.attributeFields()
            return WayFacts(
                surface = AgencyFacts.npsSurface(fields["trlsurface"]),
                trailClass = AgencyFacts.trailClass(fields["trlclass"]),
                seasonal =
                    when (FactValues.meaningful(fields["seasonal"])) {
                        "yes" -> true
                        "no" -> false
                        else -> null
                    },
                operator = fields["maintainer"]?.takeIf { FactValues.meaningful(it) != null }?.let(FactValues::operator),
            )
        }
    },

    /** BLM's public managed trails (`BLM_Natl_GTLF_Public_Managed_Trails`), whose allowed-mode codes name who may use each trail. */
    BLM("blm", "Bureau of Land Management") {
        override fun nameOf(properties: Map<String, String?>): String? = properties["route_prmry_nm"]

        override fun featureKey(properties: Map<String, String?>): String? =
            properties["original_globalid"]?.trim()?.takeIf { it.isNotEmpty() }

        /**
         * The groups `PLAN_ALLOW_MODE_TRNSPRT` names, read literally; for an unknown code, those of `PLAN_MODE_TRNSPRT`. None while access
         * is restricted to administrators, permitted users or everyone.
         */
        override fun groupsOf(properties: Map<String, String?>): Set<String> {
            fun field(name: String) = properties[name]?.trim().orEmpty().uppercase()
            if (field("plan_access_rstrct") in BLM_RESTRICTED) return emptySet()
            return BLM_MODES[field("plan_allow_mode_trnsprt")]
                ?: when (field("plan_mode_trnsprt")) {
                    "NON-MOTORIZED" -> FOOT_GROUPS + SegmentSportGroup.RIDE
                    "NON-MECHANIZED" -> FOOT_GROUPS
                    else -> emptySet()
                }
        }

        /**
         * Every group restricted access shuts out; else each of `ride`, `run` and `hike` a known, non-snow allowed mode leaves out. An
         * unknown mode closes nothing: the planned mode can open a trail but never close one.
         */
        override fun closedTo(properties: Map<String, String?>): Set<String> {
            fun field(name: String) = properties[name]?.trim().orEmpty().uppercase()
            val mode = field("plan_allow_mode_trnsprt")
            val open = BLM_MODES[mode]
            return when {
                field("plan_access_rstrct") in BLM_RESTRICTED -> SegmentSportGroup.all.toSet()
                open == null || mode.startsWith("SNOW_") -> emptySet()
                else -> FOOT_GROUPS + SegmentSportGroup.RIDE - open
            }
        }

        override val attributeFields = listOf("obsrve_srfce_type")

        override fun facts(tags: Map<String, String>, group: String?): WayFacts =
            WayFacts(surface = AgencyFacts.blmSurface(tags.attributeFields()["obsrve_srfce_type"]))
    };

    override val source: String
        get() = SegmentSource.AGENCY

    override val license: String
        get() = LICENSE_AGENCY

    /** The feature's name as published, before [nameFrom] tidies it. */
    protected abstract fun nameOf(properties: Map<String, String?>): String?

    /** The feature's stable id within its dataset, or null when it has none and must be skipped. */
    abstract fun featureKey(properties: Map<String, String?>): String?

    /** The groups the feature is a trail for, before its name is considered. */
    abstract fun groupsOf(properties: Map<String, String?>): Set<String>

    /**
     * The groups the feature says the trail is closed to, which OSM candidates along it may not invite (segments/trail-candidates). A group
     * missing from [groupsOf] isn't closed: none of the datasets says "not listed" to mean "not allowed".
     */
    open fun closedTo(properties: Map<String, String?>): Set<String> = emptySet()

    /** The feature's name, trimmed and title-cased when it's all capitals; null when it has none. */
    fun nameFrom(properties: Map<String, String?>): String? = nameOf(properties)?.trim()?.takeIf { it.isNotEmpty() }?.let(::titleCased)

    override fun excluded(tags: Map<String, String>): Boolean = false

    override fun wayGroups(tags: Map<String, String>): Set<String> =
        if (tags[NAME].isNullOrBlank()) emptySet() else tags[GROUPS].orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    override fun marksJunctions(tags: Map<String, String>): Boolean = wayGroups(tags).isNotEmpty()

    override fun directions(tags: Map<String, String>, group: String): Directions = Directions.BOTH

    /** The feature properties [AgencyReader] keeps in a way's tags as `agency:<field>`, for [facts] (segments/trail-attributes). */
    abstract val attributeFields: List<String>

    companion object {
        /** The tags [AgencyReader] gives an agency way. */
        const val NAME = "name"
        const val GROUPS = "agency:groups"

        /** `<kind>:<feature key>`, with `#<part>` for a MultiLineString's parts after the first. */
        const val KEY = "agency:key"

        /** What prefixes a kept attribute field in an agency way's tags. */
        const val FIELD = "agency:"

        /** The kept attribute fields among an agency way's tags, by field name. */
        private fun Map<String, String>.attributeFields(): Map<String, String> =
            entries.filter { it.key.startsWith(FIELD) }.associate { it.key.removePrefix(FIELD) to it.value }

        private const val MILEPOST_DECIMALS = 3

        /** The Forest Service's pack and saddle use, as its fields' prefix. */
        private const val PACK_SADDLE = "pack_saddle"

        /** The suffixes of a Forest Service use's fields that hold a season it is allowed in. */
        private val USFS_SEASONS = listOf("managed", "accpt", "accpt_disc")

        /** [field]'s season, when it holds one. */
        private fun usfsSeason(properties: Map<String, String?>, field: String): String? =
            properties[field]?.trim()?.takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }

        private val FOOT_GROUPS = setOf(SegmentSportGroup.RUN, SegmentSportGroup.HIKE)

        /** Each Forest Service use, as its fields' prefix, and the groups it names. */
        private val USFS_USES: Map<String, Set<String>> =
            mapOf(
                "bicycle" to setOf(SegmentSportGroup.RIDE),
                "hiker_pedestrian" to FOOT_GROUPS,
                "xcountry_ski" to setOf(SegmentSportGroup.SKI),
                "nonmotor_watercraft" to setOf(SegmentSportGroup.PADDLE),
            )

        /** A Forest Service season covering the whole year. */
        private const val USFS_ALL_YEAR = "01/01-12/31"

        /** Whether [use]'s managed, accepted or accepted/discouraged field holds a season. */
        private fun usfsAllowed(properties: Map<String, String?>, use: String): Boolean = USFS_SEASONS.any { suffix ->
            usfsSeason(properties, "${use}_$suffix") != null
        }

        /** Each `TRLUSE` part, letters only and lower-cased, and the groups it names. */
        private val NPS_USES: Map<String, Set<String>> = buildMap {
            for (use in listOf("hiker", "pedestrian", "hike", "hiking")) put(use, FOOT_GROUPS)
            for (use in listOf("bicycle", "bike")) put(use, setOf(SegmentSportGroup.RIDE))
            for (use in listOf("crosscountryski", "crosscountryskiing", "xcski")) put(use, setOf(SegmentSportGroup.SKI))
            for (use in listOf("canoe", "kayak", "canoekayak", "nonmotorizedwatercraft", "paddling")) {
                put(use, setOf(SegmentSportGroup.PADDLE))
            }
        }

        /**
         * Each `PLAN_ALLOW_MODE_TRNSPRT` code and the groups its definition names: bicycles for rides, hiking for runs and hikes, nordic
         * skiing for skis. Codes for motor vehicles or horses alone give none; a code not listed falls back to the planned mode.
         */
        private val BLM_MODES: Map<String, Set<String>> = buildMap {
            val shared = FOOT_GROUPS + SegmentSportGroup.RIDE
            for (code in
                listOf("NON_MOTO_SHARED", "BIKE_HIK_ONLY", "ALL_MOTO_VEH", "MTC_ATV_SHARED", "MTC_ATV_UTV_SHARED", "TECH_VEH_SHARED")) {
                put(code, shared)
            }
            put("BIKE_ONLY", setOf(SegmentSportGroup.RIDE))
            for (code in listOf("HIK_ONLY", "EQU_HIK_ONLY")) put(code, FOOT_GROUPS)
            for (code in listOf("SNOW_NON_MOTO_SHARED", "SNOW_NON_MECH_SHARED", "SNOW_MOTO_SHARED")) put(code, setOf(SegmentSportGroup.SKI))
            for (code in
                listOf(
                    "EQU_ONLY",
                    "STRT_LGL_VEH",
                    "MTC_ONLY",
                    "MTC_ATV_ONLY",
                    "MTC_ATV_UTV_ONLY",
                    "TECH_MTC_ONLY",
                    "TECH_HI_CLEAR_VEH_ONLY",
                    "SNOW_MOTO_ONLY",
                    "MTC_SHARED",
                )) {
                put(code, emptySet())
            }
        }

        /** `PLAN_ACCESS_RSTRCT` values, upper-cased, that close a trail to the public ("All" is "no one allowed to access"). */
        private val BLM_RESTRICTED = setOf("ALL", "ADMIN ONLY", "AUTHORIZED/PERMITTED USER ONLY")

        /** The rules for an operator's `--agency` kind, or null for a kind the importer doesn't know. */
        fun ofKind(kind: String): AgencyRules? = entries.firstOrNull { it.kind == kind }

        /**
         * [name] in title case when it has letters and none of them lower case ("BEAVER POND TRAIL" to "Beaver Pond Trail"); any other name
         * as written. A letter starts a word after a space, hyphen, slash or parenthesis, not after an apostrophe ("Widow's").
         */
        fun titleCased(name: String): String {
            if (name.none(Char::isLetter) || name.any(Char::isLowerCase)) return name
            val lower = name.lowercase()
            return buildString {
                for ((i, c) in lower.withIndex()) append(if (i == 0 || lower[i - 1] in WORD_STARTS) c.uppercaseChar() else c)
            }
        }

        private const val WORD_STARTS = " -/("
    }
}
