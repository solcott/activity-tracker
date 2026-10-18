package io.github.solcott.activitytracker.lexicon.app

// Splitting an activity's streams by tier, and merging the tiers a viewer is given back into one ([DetailCategory]).

/**
 * Only the channels and lap and leg fields of [categories], plus the core ones when [core]; null when that leaves no channel. Laps and legs
 * are kept whole in number (each with its start and elapsed) whenever any of them holds a field here, so tiers merge by index.
 */
fun ActivityStreamData.forTier(categories: Set<String>, core: Boolean): ActivityStreamData? {
    fun keeps(channel: String): Boolean = DetailCategory.ofChannel(channel)?.let { it in categories } ?: core
    val lapFields = categories.flatMap { DetailCategory.LAP_FIELDS[it].orEmpty() }.toSet()
    val ints =
        ActivityStreamData.INT_CHANNELS.fold(ActivityStreamData(time = time)) { d, ch ->
            if (keeps(ch.token)) ch.with(d, ch.get(this)) else d
        }
    val laps = laps?.map { it.only(lapFields, core) }?.takeIf { core || it.any { lap -> lap.hasAny(lapFields) } }
    val legs = legs?.map { it.only(lapFields, core) }?.takeIf { core || it.any { leg -> leg.hasAny(lapFields) } }
    val out =
        ints.copy(
            gears = gears.takeIf { keeps(StreamChannel.GEARS) },
            gasSwitches = gasSwitches.takeIf { keeps(StreamChannel.GAS_SWITCHES) },
            diveAlerts = diveAlerts.takeIf { keeps(StreamChannel.DIVE_ALERTS) },
            tankPressures = tankPressures.takeIf { keeps(StreamChannel.TANK_PRESSURES) },
            lengths = lengths.takeIf { keeps(StreamChannel.LENGTHS) },
            sets = sets.takeIf { keeps(StreamChannel.SETS) },
            laps = laps,
            legs = legs,
        )
    return out.takeIf { it.channels.isNotEmpty() }
}

/**
 * This with [narrower]'s channels laid over it, [narrower]'s winning where both have one, and laps and legs merged field by field by index.
 * When the two have different [ActivityStreamData.time]s, [narrower]'s per-sample channels are matched to this one's by time.
 */
fun ActivityStreamData.overlaid(narrower: ActivityStreamData): ActivityStreamData {
    if (time.isEmpty()) return narrower
    val align: (List<Int?>) -> List<Int?> = if (narrower.time == time) { samples -> samples } else alignTo(narrower.time, time)
    val ints = ActivityStreamData.INT_CHANNELS.fold(this) { d, ch -> ch.get(narrower)?.let { ch.with(d, align(it)) } ?: d }
    return ints.copy(
        gears = narrower.gears ?: gears,
        gasSwitches = narrower.gasSwitches ?: gasSwitches,
        diveAlerts = narrower.diveAlerts ?: diveAlerts,
        tankPressures = narrower.tankPressures?.map { TankPressure(align(it.pressure)) } ?: tankPressures,
        lengths = narrower.lengths ?: lengths,
        sets = narrower.sets ?: sets,
        laps = mergeByIndex(laps, narrower.laps) { a, b -> a.overlaid(b) },
        legs = mergeByIndex(legs, narrower.legs) { a, b -> a.overlaid(b) },
    )
}

/** [parts] (widest first) merged into one, each narrower one laid over the wider ([overlaid]); null when there are none. */
fun mergeStreams(parts: List<ActivityStreamData>): ActivityStreamData? = parts.reduceOrNull { acc, d -> acc.overlaid(d) }

internal fun <T> mergeByIndex(wide: List<T>?, narrow: List<T>?, merge: (T, T) -> T): List<T>? {
    if (wide == null || narrow == null) return narrow ?: wide
    return List(maxOf(wide.size, narrow.size)) { i ->
        val a = wide.getOrNull(i)
        val b = narrow.getOrNull(i)
        when {
            a != null && b != null -> merge(a, b)
            a != null -> a
            else -> requireNotNull(b)
        }
    }
}

/** A function moving samples lined up with [from] onto [to]: each sample of [to] takes the last of [from] at or before its time. */
internal fun alignTo(from: List<Int>, to: List<Int>): (List<Int?>) -> List<Int?> = { samples ->
    var j = 0
    to.map { t ->
        while (j + 1 < from.size && from[j + 1] <= t) j++
        if (from.isNotEmpty() && from[j] <= t) samples.getOrNull(j) else null
    }
}

/** The categories whose channels or lap and leg fields this holds. */
fun ActivityStreamData.heldCategories(): Set<String> {
    val lapFields = laps.orEmpty().flatMap { lapFieldsSet(it) } + legs.orEmpty().flatMap { legFieldsSet(it) }
    return channels.mapNotNull { DetailCategory.ofChannel(it) }.toSet() +
        lapFields.mapNotNull { name -> DetailCategory.LAP_FIELDS.entries.firstOrNull { name in it.value }?.key }
}

/** Whether this holds any core data: a core channel, lengths, sets, or a lap's or leg's structure beyond its start and elapsed. */
fun ActivityStreamData.holdsCore(): Boolean =
    channels.any { it in DetailCategory.CORE_CHANNELS && it != StreamChannel.LAPS && it != StreamChannel.LEGS } ||
        laps.orEmpty().any { listOfNotNull(it.moving, it.distance, it.ascent, it.descent, it.intensity, it.trigger).isNotEmpty() } ||
        legs.orEmpty().any { listOfNotNull(it.moving, it.distance, it.ascent, it.descent, it.subSport, it.transition).isNotEmpty() }
