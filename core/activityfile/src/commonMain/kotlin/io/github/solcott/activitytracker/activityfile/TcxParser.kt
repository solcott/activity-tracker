package io.github.solcott.activitytracker.activityfile

/**
 * Reads a Garmin TCX file: its first activity, or every activity of its first `MultiSportSession`. Each `Lap` becomes a summary (its
 * `TotalTimeSeconds` is timer time, so moving time) with its calories, intensity and trigger, and each `Trackpoint` a sample, including the
 * `TPX` speed and power extensions (and the lap's `LX` power and cadence). TCX has no ascent total, so climb is computed from the samples.
 * The device is `Creator/Name` ("Forerunner 965"), with its serial number `Creator/UnitId` and firmware `Creator/Version`, and the notes
 * the first activity's `Notes`.
 *
 * A multisport session gives a session per activity (its sport, and its laps' sums) and per `NextSport` `Transition` (FIT's transition
 * sport), each lasting until the next begins, so the mapper builds legs from them as from a FIT file's sessions. A plain file has none.
 */
internal object TcxParser {
    fun parse(xml: String): DecodedActivity {
        val state = State()
        walkXml(xml, onStart = state::start, onEnd = state::end)
        if (!state.sawActivity) {
            activityFileError(
                if (state.sawCourse) "This TCX file is a course rather than a recorded activity" else "No activity in this TCX file"
            )
        }
        return DecodedActivity(
            fileId = state.source(),
            sessions = state.sessions(),
            laps = state.laps(),
            records = state.samples,
            notes = state.notes,
        )
    }

    /** An activity of the file, or a multisport session's transition (with [transition] its only lap). */
    private class Leg(val sport: String?, val transition: LapBuilder? = null) {
        val laps = mutableListOf<LapBuilder>()
    }

    private class State {
        var sawActivity = false
        var sawCourse = false
        var inActivity = false
        /** Inside the first `MultiSportSession`, whose every activity is read. */
        var inMultisport = false
        var sawMultisport = false
        var device: String? = null
        var unitId: String? = null
        var versionMajor: String? = null
        var versionMinor: String? = null
        var notes: String? = null
        var lap: LapBuilder? = null
        var point: SampleBuilder? = null
        val legs = mutableListOf<Leg>()
        val samples = mutableListOf<DecodedSample>()

        fun start(path: List<String>, attribute: (String) -> String?) {
            when (path.last()) {
                "Course" -> sawCourse = true
                "MultiSportSession" ->
                    if (!sawActivity && !sawMultisport) {
                        sawMultisport = true
                        inMultisport = true
                    }
                // Only the first activity outside a multisport session; a multi-activity file is rare.
                "Activity" ->
                    if (inMultisport || !sawActivity) {
                        sawActivity = true
                        inActivity = true
                        legs += Leg(attribute("Sport"))
                    }
                "Lap" -> if (inActivity) lap = LapBuilder(parseXmlTime(attribute("StartTime")))
                "Transition" -> if (inMultisport) lap = LapBuilder(parseXmlTime(attribute("StartTime")))
                "Trackpoint" -> if (inActivity) point = SampleBuilder()
            }
        }

        fun end(path: List<String>, text: String) {
            val name = path.last()
            if (name == "MultiSportSession") inMultisport = false
            val currentLap = lap
            if (name == "Transition" && inMultisport) {
                currentLap?.let { legs += Leg(TRANSITION, transition = it) }
                lap = null
                return
            }
            if (!inActivity) {
                if (inMultisport && currentLap != null) currentLap.read(name, path.parent, text)
                return
            }
            val currentPoint = point
            when {
                name == "Activity" -> inActivity = false
                name == "Trackpoint" -> {
                    currentPoint?.let { samples += it.build() }
                    point = null
                }
                currentPoint != null -> currentPoint.read(name, path.parent, text)
                name == "Lap" -> {
                    currentLap?.let { legs.last().laps += it }
                    lap = null
                }
                currentLap != null -> currentLap.read(name, path.parent, text)
                else -> readActivity(name, path, text)
            }
        }

        /** The activity's own fields outside its laps: the device's name, serial and firmware, and the first activity's notes. */
        private fun readActivity(name: String, path: List<String>, text: String) {
            val parent = path.parent
            val inCreator = parent == "Version" && path.getOrNull(path.size - 3) == "Creator"
            when {
                name == "Name" && parent == "Creator" -> device = text.ifEmpty { null }
                name == "UnitId" && parent == "Creator" -> unitId = text.takeUnless { it.isEmpty() || it == "0" }
                name == "VersionMajor" && inCreator -> versionMajor = text.ifEmpty { null }
                name == "VersionMinor" && inCreator -> versionMinor = text.ifEmpty { null }
                name == "Notes" && parent == "Activity" && notes == null -> notes = text.ifEmpty { null }
            }
        }

        /**
         * The `Creator` device, or null when the file names none. Minor versions are hundredths, as Garmin writes them: 19 and 2 is 19.02.
         */
        fun source(): DecodedSource? {
            val name = device ?: return null
            val minor = versionMinor?.toIntOrNull()?.toString()?.padStart(2, '0')?.trimEnd('0')
            val version = versionMajor?.let { major -> if (minor.isNullOrEmpty()) major else "$major.$minor" }
            return DecodedSource(
                type = null,
                manufacturer = null,
                product = name,
                timeCreatedMillis = null,
                serialNumber = unitId,
                softwareVersion = version,
            )
        }

        /** Every activity's laps in time order, each lasting until the next lap or transition starts. */
        fun laps(): List<DecodedSummary> {
            val laps = legs.flatMap { leg -> leg.laps.map { it.build(leg.sport) } }
            return laps.withElapsed(until = laps + legs.mapNotNull { leg -> leg.transition?.build(TRANSITION) })
        }

        /** A session per leg when the file is a multisport session, each lasting until the next starts. */
        fun sessions(): List<DecodedSummary> {
            if (!sawMultisport) return emptyList()
            val sessions = legs.mapNotNull { leg -> leg.transition?.build(TRANSITION) ?: leg.summary() }
            return sessions.withElapsed(until = sessions)
        }

        /**
         * These summaries in time order, each with an elapsed time up to the first of [until] that starts after it; the last, to its last
         * sample or the end of its timer time, whichever is later. TCX gives timer time only.
         */
        private fun List<DecodedSummary>.withElapsed(until: List<DecodedSummary>): List<DecodedSummary> {
            val starts = until.mapNotNull { it.startTimeMillis }.sorted()
            val lastSample = samples.mapNotNull { it.timestampMillis }.maxOrNull()
            return sortedBy { it.startTimeMillis }
                .map { s ->
                    val start = s.startTimeMillis ?: return@map s
                    val end =
                        starts.firstOrNull { it > start }
                            ?: listOfNotNull(lastSample, s.totalTimerSeconds?.let { start + (it * MILLIS_PER_SECOND).toLong() }).maxOrNull()
                    s.copy(totalElapsedSeconds = end?.let { (it - start) / MILLIS_PER_SECOND })
                }
        }

        private fun Leg.summary(): DecodedSummary? {
            val built = laps.map { it.build(sport) }
            val first = built.firstOrNull() ?: return null
            return first.copy(
                totalTimerSeconds = built.total { it.totalTimerSeconds },
                totalDistanceMeters = built.total { it.totalDistanceMeters },
                avgSpeedMps = null,
                maxSpeedMps = built.mapNotNull { it.maxSpeedMps }.maxOrNull(),
                avgHeartRate = built.weightedAverage { it.avgHeartRate?.toDouble() }?.toInt(),
                maxHeartRate = built.mapNotNull { it.maxHeartRate }.maxOrNull(),
                avgPowerWatts = built.weightedAverage { it.avgPowerWatts?.toDouble() }?.toInt(),
                avgCadence = built.weightedAverage { it.avgCadence?.toDouble() }?.toInt(),
                maxPowerWatts = built.mapNotNull { it.maxPowerWatts }.maxOrNull(),
                maxCadence = built.mapNotNull { it.maxCadence }.maxOrNull(),
                totalCalories = built.mapNotNull { it.totalCalories }.takeIf { it.isNotEmpty() }?.sum(),
                intensity = null,
                lapTrigger = null,
            )
        }

        private fun SampleBuilder.read(name: String, parent: String?, text: String) {
            when (name) {
                "Time" -> time = parseXmlTime(text)
                "LatitudeDegrees" -> latitude = text.toDoubleOrNull()
                "LongitudeDegrees" -> longitude = text.toDoubleOrNull()
                "AltitudeMeters" -> altitude = text.toDoubleOrNull()
                "DistanceMeters" -> distance = text.toDoubleOrNull()
                "Value" -> if (parent == "HeartRateBpm") heartRate = text.toIntValue()
                "Cadence",
                "RunCadence" -> cadence = text.toIntValue()
                "Speed" -> speed = text.toDoubleOrNull()
                "Watts" -> power = text.toIntValue()
            }
        }
    }

    private class LapBuilder(val startTime: Long?) {
        var timerSeconds: Double? = null
        var distance: Double? = null
        var maxSpeed: Double? = null
        var avgSpeed: Double? = null
        var avgHeartRate: Int? = null
        var maxHeartRate: Int? = null
        var avgPower: Int? = null
        var avgCadence: Int? = null
        var maxPower: Int? = null
        var maxCadence: Int? = null
        var calories: Int? = null
        var intensity: String? = null
        var trigger: String? = null

        /** [parent] is the lap's own element (`Lap`, or `Transition` in a multisport session) for its direct children. */
        fun read(name: String, parent: String?, text: String) {
            val own = parent == "Lap" || parent == "Transition"
            when {
                name == "TotalTimeSeconds" -> timerSeconds = text.toDoubleOrNull()
                name == "DistanceMeters" && own -> distance = text.toDoubleOrNull()
                name == "MaximumSpeed" -> maxSpeed = text.toDoubleOrNull()
                name == "Value" && parent == "AverageHeartRateBpm" -> avgHeartRate = text.toIntValue()
                name == "Value" && parent == "MaximumHeartRateBpm" -> maxHeartRate = text.toIntValue()
                name == "Cadence" && own -> avgCadence = text.toIntValue()
                name == "Calories" && own -> calories = text.toIntValue()
                name == "Intensity" && own -> intensity = text.ifEmpty { null }
                name == "TriggerMethod" && own -> trigger = text.ifEmpty { null }
                name == "AvgSpeed" -> avgSpeed = text.toDoubleOrNull()
                name == "AvgWatts" -> avgPower = text.toIntValue()
                name == "MaxWatts" -> maxPower = text.toIntValue()
                name == "MaxBikeCadence" -> maxCadence = text.toIntValue()
            }
        }

        fun build(sport: String?) =
            DecodedSummary(
                sport = sport,
                subSport = null,
                startTimeMillis = startTime,
                // TCX laps carry timer time only: the parser sets elapsed time from where the next one starts.
                totalElapsedSeconds = null,
                totalTimerSeconds = timerSeconds,
                totalDistanceMeters = distance,
                totalAscentMeters = null,
                avgSpeedMps = avgSpeed,
                maxSpeedMps = maxSpeed,
                avgHeartRate = avgHeartRate,
                maxHeartRate = maxHeartRate,
                avgPowerWatts = avgPower,
                // Garmin Connect's export writes the lap's max cadence here, so the samples give a truer average.
                avgCadence = avgCadence?.takeUnless { it == maxCadence },
                avgTemperatureCelsius = null,
                maxPowerWatts = maxPower,
                maxCadence = maxCadence,
                // The schema requires Calories; writers with none to give write 0.
                totalCalories = calories?.takeIf { it > 0 },
                intensity = intensity,
                lapTrigger = trigger,
            )
    }

    /** FIT's transition sport, which the mapper reads as the time between multisport legs. */
    private const val TRANSITION = "transition"

    private const val MILLIS_PER_SECOND = 1000.0
}
