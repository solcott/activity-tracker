package io.github.solcott.activitytracker.activityfile

/**
 * Reads the first activity of a Garmin TCX file. Each `Lap` becomes a summary (its `TotalTimeSeconds` is timer time, so moving time) and
 * each `Trackpoint` a sample, including the `TPX` speed and power extensions (and the lap's `LX` power and cadence). TCX has no ascent
 * total, so climb is computed from the samples. The device is `Creator/Name` ("Forerunner 965").
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
            fileId = state.device?.let { DecodedSource(type = null, manufacturer = null, product = it, timeCreatedMillis = null) },
            sessions = emptyList(),
            laps = state.laps.map { it.build(state.sport) },
            records = state.samples,
        )
    }

    private class State {
        var sawActivity = false
        var sawCourse = false
        var inActivity = false
        var sport: String? = null
        var device: String? = null
        var lap: LapBuilder? = null
        var point: SampleBuilder? = null
        val laps = mutableListOf<LapBuilder>()
        val samples = mutableListOf<DecodedSample>()

        fun start(path: List<String>, attribute: (String) -> String?) {
            when (path.last()) {
                "Course" -> sawCourse = true
                // Only the first activity; a multi-activity file is rare and its others would need their own records.
                "Activity" ->
                    if (!sawActivity) {
                        sawActivity = true
                        inActivity = true
                        sport = attribute("Sport")
                    }
                "Lap" -> if (inActivity) lap = LapBuilder(parseXmlTime(attribute("StartTime")))
                "Trackpoint" -> if (inActivity) point = SampleBuilder()
            }
        }

        fun end(path: List<String>, text: String) {
            if (!inActivity) return
            val name = path.last()
            val currentPoint = point
            val currentLap = lap
            when {
                name == "Activity" -> inActivity = false
                name == "Trackpoint" -> {
                    currentPoint?.let { samples += it.build() }
                    point = null
                }
                currentPoint != null -> currentPoint.read(name, path.parent, text)
                name == "Lap" -> {
                    currentLap?.let { laps += it }
                    lap = null
                }
                currentLap != null -> currentLap.read(name, path.parent, text)
                name == "Name" && path.parent == "Creator" -> device = text.ifEmpty { null }
            }
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

        fun read(name: String, parent: String?, text: String) {
            when {
                name == "TotalTimeSeconds" -> timerSeconds = text.toDoubleOrNull()
                name == "DistanceMeters" && parent == "Lap" -> distance = text.toDoubleOrNull()
                name == "MaximumSpeed" -> maxSpeed = text.toDoubleOrNull()
                name == "Value" && parent == "AverageHeartRateBpm" -> avgHeartRate = text.toIntValue()
                name == "Value" && parent == "MaximumHeartRateBpm" -> maxHeartRate = text.toIntValue()
                name == "Cadence" && parent == "Lap" -> avgCadence = text.toIntValue()
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
                // TCX laps carry timer time only; the mapper takes elapsed time from the samples.
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
            )
    }
}
