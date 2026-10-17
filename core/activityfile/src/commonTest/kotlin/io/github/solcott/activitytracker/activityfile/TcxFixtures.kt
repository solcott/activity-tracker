package io.github.solcott.activitytracker.activityfile

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** TCX files as Garmin Connect exports them. */
object TcxFixtures {
    val START: Instant = Instant.parse("2026-09-20T07:00:00Z")

    const val INTERVAL_LAPS = 8

    /**
     * Eight 60 s laps of a run, alternating 400 m Active laps (25 kcal, ended by distance) and 100 m Resting laps (5 kcal, ended by hand),
     * with a track point every 30 s.
     */
    val intervalRun: String = buildString {
        append(HEADER)
        append("""<Activities><Activity Sport="Running"><Id>$START</Id>""")
        var meters = 0
        repeat(INTERVAL_LAPS) { i ->
            val rest = i % 2 == 1
            val lapMeters = if (rest) 100 else 400
            append("""<Lap StartTime="${START + (i * 60).seconds}"><TotalTimeSeconds>60.0</TotalTimeSeconds>""")
            append("<DistanceMeters>$lapMeters.0</DistanceMeters><Calories>${if (rest) 5 else 25}</Calories>")
            append("<AverageHeartRateBpm><Value>${if (rest) 140 else 170}</Value></AverageHeartRateBpm>")
            append("<Intensity>${if (rest) "Resting" else "Active"}</Intensity>")
            append("<TriggerMethod>${if (rest) "Manual" else "Distance"}</TriggerMethod><Track>")
            for (half in 0..1) {
                append(point(i * 60 + half * 30, meters + lapMeters * half / 2, if (rest) 140 else 170))
            }
            meters += lapMeters
            append("</Track></Lap>")
        }
        append("</Activity></Activities></TrainingCenterDatabase>")
    }

    /** A one-lap ride of 10 minutes and 5 km, ended by hand. */
    val oneLapRide: String = buildString {
        append(HEADER)
        append("""<Activities><Activity Sport="Biking"><Id>$START</Id>""")
        append("""<Lap StartTime="$START"><TotalTimeSeconds>600.0</TotalTimeSeconds><DistanceMeters>5000.0</DistanceMeters>""")
        append("<Calories>120</Calories><Intensity>Active</Intensity><TriggerMethod>Manual</TriggerMethod><Track>")
        append(point(0, 0, 130))
        append(point(300, 2500, 140))
        append(point(600, 5000, 150))
        append("</Track></Lap></Activity></Activities></TrainingCenterDatabase>")
    }

    /** Each activity of [duathlon]: its sport, start and laps' seconds and meters; a transition is null meters. */
    class Leg(val sport: String, val start: Int, val laps: List<Pair<Int, Int>>)

    val DUATHLON_LEGS =
        listOf(
            Leg("Running", 0, listOf(900 to 3000)),
            Leg("Transition", 900, listOf(60 to 0)),
            Leg("Biking", 960, listOf(1800 to 15_000)),
            Leg("Transition", 2760, listOf(45 to 0)),
            Leg("Running", 2805, listOf(300 to 1000, 300 to 1000)),
        )

    /** A run, ride, run duathlon as a `MultiSportSession`, with a `Transition` before the ride and the second run, a point a minute. */
    val duathlon: String = buildString {
        append(HEADER)
        append("<Activities><MultiSportSession><Id>$START</Id>")
        var meters = 0
        DUATHLON_LEGS.forEachIndexed { index, leg ->
            if (index == 0) append("<FirstSport>")
            if (leg.sport == "Transition") {
                val (seconds, _) = leg.laps.single()
                append(
                    """<NextSport><Transition StartTime="${START + leg.start.seconds}"><TotalTimeSeconds>$seconds.0</TotalTimeSeconds>"""
                )
                append("<DistanceMeters>0.0</DistanceMeters><Calories>3</Calories><Intensity>Active</Intensity>")
                append("<TriggerMethod>Manual</TriggerMethod></Transition>")
                return@forEachIndexed
            }
            append("""<Activity Sport="${leg.sport}"><Id>${START + leg.start.seconds}</Id>""")
            var t = leg.start
            for ((seconds, lapMeters) in leg.laps) {
                append("""<Lap StartTime="${START + t.seconds}"><TotalTimeSeconds>$seconds.0</TotalTimeSeconds>""")
                append("<DistanceMeters>$lapMeters.0</DistanceMeters><Calories>50</Calories>")
                append("<AverageHeartRateBpm><Value>150</Value></AverageHeartRateBpm>")
                append("<Intensity>Active</Intensity><TriggerMethod>Manual</TriggerMethod><Track>")
                for (s in 0 until seconds step 60) append(point(t + s, meters + lapMeters * s / seconds, 150))
                append("</Track></Lap>")
                t += seconds
                meters += lapMeters
            }
            append("</Activity>")
            append(if (index == 0) "</FirstSport>" else "</NextSport>")
        }
        append("</MultiSportSession></Activities></TrainingCenterDatabase>")
    }

    /** Every track point [duathlon] holds. */
    val DUATHLON_POINTS = DUATHLON_LEGS.filter { it.sport != "Transition" }.sumOf { leg -> leg.laps.sumOf { (s, _) -> (s + 59) / 60 } }

    private const val HEADER =
        """<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">"""

    private fun point(seconds: Int, meters: Int, heartRate: Int) =
        "<Trackpoint><Time>${START + seconds.seconds}</Time><DistanceMeters>$meters.0</DistanceMeters>" +
            "<HeartRateBpm><Value>$heartRate</Value></HeartRateBpm></Trackpoint>"
}
