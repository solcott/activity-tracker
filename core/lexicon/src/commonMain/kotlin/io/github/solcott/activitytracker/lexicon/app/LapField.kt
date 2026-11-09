package io.github.solcott.activitytracker.lexicon.app

// A lap's and a leg's fields by detail category ([DetailCategory.LAP_FIELDS]): cut to a tier, and laid over another tier's.

/**
 * One category field [Lap] and [Leg] share: its name in [DetailCategory.LAP_FIELDS], and how to read and replace it on each.
 * [LAP_CATEGORY_FIELDS] lists every one once, so cutting, listing and overlaying laps and legs can't miss a field or disagree on its name.
 */
internal class LapField(
    val name: String,
    val ofLap: (Lap) -> Int?,
    val lapWith: (Lap, Int?) -> Lap,
    val ofLeg: (Leg) -> Int?,
    val legWith: (Leg, Int?) -> Leg,
)

internal val LAP_CATEGORY_FIELDS: List<LapField> =
    listOf(
        LapField("avgSpeed", { it.avgSpeed }, { l, v -> l.copy(avgSpeed = v) }, { it.avgSpeed }, { l, v -> l.copy(avgSpeed = v) }),
        LapField("maxSpeed", { it.maxSpeed }, { l, v -> l.copy(maxSpeed = v) }, { it.maxSpeed }, { l, v -> l.copy(maxSpeed = v) }),
        LapField(
            "avgHeartRate",
            { it.avgHeartRate },
            { l, v -> l.copy(avgHeartRate = v) },
            { it.avgHeartRate },
            { l, v -> l.copy(avgHeartRate = v) },
        ),
        LapField(
            "maxHeartRate",
            { it.maxHeartRate },
            { l, v -> l.copy(maxHeartRate = v) },
            { it.maxHeartRate },
            { l, v -> l.copy(maxHeartRate = v) },
        ),
        LapField("avgPower", { it.avgPower }, { l, v -> l.copy(avgPower = v) }, { it.avgPower }, { l, v -> l.copy(avgPower = v) }),
        LapField(
            "avgCadence",
            { it.avgCadence },
            { l, v -> l.copy(avgCadence = v) },
            { it.avgCadence },
            { l, v -> l.copy(avgCadence = v) },
        ),
        LapField("calories", { it.calories }, { l, v -> l.copy(calories = v) }, { it.calories }, { l, v -> l.copy(calories = v) }),
    )

internal fun Lap.only(fields: Set<String>, core: Boolean): Lap {
    val structure =
        Lap(
            start = start,
            elapsed = elapsed,
            moving = moving.takeIf { core },
            distance = distance.takeIf { core },
            ascent = ascent.takeIf { core },
            descent = descent.takeIf { core },
            intensity = intensity.takeIf { core },
            trigger = trigger.takeIf { core },
        )
    return LAP_CATEGORY_FIELDS.filter { it.name in fields }.fold(structure) { lap, f -> f.lapWith(lap, f.ofLap(this)) }
}

internal fun Leg.only(fields: Set<String>, core: Boolean): Leg {
    val structure =
        Leg(
            start = start,
            elapsed = elapsed,
            sport = sport,
            subSport = subSport.takeIf { core },
            transition = transition.takeIf { core },
            moving = moving.takeIf { core },
            distance = distance.takeIf { core },
            ascent = ascent.takeIf { core },
            descent = descent.takeIf { core },
        )
    return LAP_CATEGORY_FIELDS.filter { it.name in fields }.fold(structure) { leg, f -> f.legWith(leg, f.ofLeg(this)) }
}

/** The category fields ([DetailCategory.LAP_FIELDS]) this lap has, by name. */
internal fun lapFieldsSet(lap: Lap): Set<String> = LAP_CATEGORY_FIELDS.filter { it.ofLap(lap) != null }.mapTo(mutableSetOf()) { it.name }

/** The category fields ([DetailCategory.LAP_FIELDS]) this leg has, by name. */
internal fun legFieldsSet(leg: Leg): Set<String> = LAP_CATEGORY_FIELDS.filter { it.ofLeg(leg) != null }.mapTo(mutableSetOf()) { it.name }

internal fun Lap.hasAny(fields: Set<String>): Boolean = lapFieldsSet(this).any { it in fields }

internal fun Leg.hasAny(fields: Set<String>): Boolean = legFieldsSet(this).any { it in fields }

internal fun Lap.overlaid(n: Lap): Lap =
    LAP_CATEGORY_FIELDS.fold(
        copy(
            moving = n.moving ?: moving,
            distance = n.distance ?: distance,
            ascent = n.ascent ?: ascent,
            descent = n.descent ?: descent,
            intensity = n.intensity ?: intensity,
            trigger = n.trigger ?: trigger,
        ),
    ) { lap, f ->
        f.lapWith(lap, f.ofLap(n) ?: f.ofLap(this))
    }

internal fun Leg.overlaid(n: Leg): Leg =
    LAP_CATEGORY_FIELDS.fold(
        copy(
            subSport = n.subSport ?: subSport,
            transition = n.transition ?: transition,
            moving = n.moving ?: moving,
            distance = n.distance ?: distance,
            ascent = n.ascent ?: ascent,
            descent = n.descent ?: descent,
        ),
    ) { leg, f ->
        f.legWith(leg, f.ofLeg(n) ?: f.ofLeg(this))
    }
