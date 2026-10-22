package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
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
        override fun groupsOf(properties: Map<String, String?>): Set<String> = buildSet {
            fun allowed(use: String) =
                listOf("managed", "accpt", "accpt_disc").any { suffix ->
                    properties["${use}_$suffix"]?.trim()?.let { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) } == true
                }
            if (allowed("bicycle")) add(SegmentSportGroup.RIDE)
            if (allowed("hiker_pedestrian")) addAll(FOOT_GROUPS)
            if (allowed("xcountry_ski")) add(SegmentSportGroup.SKI)
            if (allowed("nonmotor_watercraft")) add(SegmentSportGroup.PADDLE)
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

    /** The feature's name, trimmed and title-cased when it's all capitals; null when it has none. */
    fun nameFrom(properties: Map<String, String?>): String? = nameOf(properties)?.trim()?.takeIf { it.isNotEmpty() }?.let(::titleCased)

    override fun excluded(tags: Map<String, String>): Boolean = false

    override fun wayGroups(tags: Map<String, String>): Set<String> =
        if (tags[NAME].isNullOrBlank()) emptySet() else tags[GROUPS].orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    override fun marksJunctions(tags: Map<String, String>): Boolean = wayGroups(tags).isNotEmpty()

    override fun directions(tags: Map<String, String>, group: String): Pair<Boolean, Boolean> = true to true

    companion object {
        /** The tags [AgencyReader] gives an agency way. */
        const val NAME = "name"
        const val GROUPS = "agency:groups"

        /** `<kind>:<feature key>`, with `#<part>` for a MultiLineString's parts after the first. */
        const val KEY = "agency:key"

        private const val MILEPOST_DECIMALS = 3
        private val FOOT_GROUPS = setOf(SegmentSportGroup.RUN, SegmentSportGroup.HIKE)

        /** Each `TRLUSE` part, letters only and lower-cased, and the groups it names. */
        private val NPS_USES: Map<String, Set<String>> = buildMap {
            for (use in listOf("hiker", "pedestrian", "hike", "hiking")) put(use, FOOT_GROUPS)
            for (use in listOf("bicycle", "bike")) put(use, setOf(SegmentSportGroup.RIDE))
            for (use in listOf("crosscountryski", "crosscountryskiing", "xcski")) put(use, setOf(SegmentSportGroup.SKI))
            for (use in listOf("canoe", "kayak", "canoekayak", "nonmotorizedwatercraft", "paddling")) {
                put(use, setOf(SegmentSportGroup.PADDLE))
            }
        }

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
