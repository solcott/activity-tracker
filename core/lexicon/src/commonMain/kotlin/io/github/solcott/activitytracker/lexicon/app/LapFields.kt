package io.github.solcott.activitytracker.lexicon.app

// A lap's and a leg's fields by detail category ([DetailCategory.LAP_FIELDS]): cut to a tier, and laid over another tier's.

internal fun Lap.only(fields: Set<String>, core: Boolean): Lap =
    Lap(
        start = start,
        elapsed = elapsed,
        moving = moving.takeIf { core },
        distance = distance.takeIf { core },
        ascent = ascent.takeIf { core },
        descent = descent.takeIf { core },
        intensity = intensity.takeIf { core },
        trigger = trigger.takeIf { core },
        avgSpeed = avgSpeed.takeIf { "avgSpeed" in fields },
        maxSpeed = maxSpeed.takeIf { "maxSpeed" in fields },
        avgHeartRate = avgHeartRate.takeIf { "avgHeartRate" in fields },
        maxHeartRate = maxHeartRate.takeIf { "maxHeartRate" in fields },
        avgPower = avgPower.takeIf { "avgPower" in fields },
        avgCadence = avgCadence.takeIf { "avgCadence" in fields },
        calories = calories.takeIf { "calories" in fields },
    )

internal fun Leg.only(fields: Set<String>, core: Boolean): Leg =
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
        avgSpeed = avgSpeed.takeIf { "avgSpeed" in fields },
        maxSpeed = maxSpeed.takeIf { "maxSpeed" in fields },
        avgHeartRate = avgHeartRate.takeIf { "avgHeartRate" in fields },
        maxHeartRate = maxHeartRate.takeIf { "maxHeartRate" in fields },
        avgPower = avgPower.takeIf { "avgPower" in fields },
        avgCadence = avgCadence.takeIf { "avgCadence" in fields },
        calories = calories.takeIf { "calories" in fields },
    )

/** The category fields ([DetailCategory.LAP_FIELDS]) this lap has, by name. */
internal fun lapFieldsSet(lap: Lap): Set<String> =
    listOfNotNull(
            "avgSpeed".takeIf { lap.avgSpeed != null },
            "maxSpeed".takeIf { lap.maxSpeed != null },
            "avgHeartRate".takeIf { lap.avgHeartRate != null },
            "maxHeartRate".takeIf { lap.maxHeartRate != null },
            "avgPower".takeIf { lap.avgPower != null },
            "avgCadence".takeIf { lap.avgCadence != null },
            "calories".takeIf { lap.calories != null },
        )
        .toSet()

/** The category fields ([DetailCategory.LAP_FIELDS]) this leg has, by name. */
internal fun legFieldsSet(leg: Leg): Set<String> =
    listOfNotNull(
            "avgSpeed".takeIf { leg.avgSpeed != null },
            "maxSpeed".takeIf { leg.maxSpeed != null },
            "avgHeartRate".takeIf { leg.avgHeartRate != null },
            "maxHeartRate".takeIf { leg.maxHeartRate != null },
            "avgPower".takeIf { leg.avgPower != null },
            "avgCadence".takeIf { leg.avgCadence != null },
            "calories".takeIf { leg.calories != null },
        )
        .toSet()

internal fun Lap.hasAny(fields: Set<String>): Boolean = lapFieldsSet(this).any { it in fields }

internal fun Leg.hasAny(fields: Set<String>): Boolean = legFieldsSet(this).any { it in fields }

internal fun Lap.overlaid(n: Lap): Lap =
    copy(
        moving = n.moving ?: moving,
        distance = n.distance ?: distance,
        ascent = n.ascent ?: ascent,
        descent = n.descent ?: descent,
        intensity = n.intensity ?: intensity,
        trigger = n.trigger ?: trigger,
        avgSpeed = n.avgSpeed ?: avgSpeed,
        maxSpeed = n.maxSpeed ?: maxSpeed,
        avgHeartRate = n.avgHeartRate ?: avgHeartRate,
        maxHeartRate = n.maxHeartRate ?: maxHeartRate,
        avgPower = n.avgPower ?: avgPower,
        avgCadence = n.avgCadence ?: avgCadence,
        calories = n.calories ?: calories,
    )

internal fun Leg.overlaid(n: Leg): Leg =
    copy(
        subSport = n.subSport ?: subSport,
        transition = n.transition ?: transition,
        moving = n.moving ?: moving,
        distance = n.distance ?: distance,
        ascent = n.ascent ?: ascent,
        descent = n.descent ?: descent,
        avgSpeed = n.avgSpeed ?: avgSpeed,
        maxSpeed = n.maxSpeed ?: maxSpeed,
        avgHeartRate = n.avgHeartRate ?: avgHeartRate,
        maxHeartRate = n.maxHeartRate ?: maxHeartRate,
        avgPower = n.avgPower ?: avgPower,
        avgCadence = n.avgCadence ?: avgCadence,
        calories = n.calories ?: calories,
    )
