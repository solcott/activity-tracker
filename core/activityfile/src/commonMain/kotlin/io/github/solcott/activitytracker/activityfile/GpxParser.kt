package io.github.solcott.activitytracker.activityfile

/**
 * Reads a GPX 1.0/1.1 track. Points from every `trk` and `trkseg` are joined in time order; heart rate, cadence, temperature and power come
 * from whichever extension carries them (Garmin TrackPointExtension, Strava, GoldenCheetah). GPX has no summary, so [DecodedActivityMapper]
 * computes distance, moving time and climb from the points. The title is the first track's `name`, else the file's `metadata/name`, and the
 * notes its `desc` (Strava's activity description). Sets no device: `creator` names the exporting software ("Garmin Connect", "StravaGPX"),
 * not the watch.
 */
internal object GpxParser {
    fun parse(xml: String): DecodedActivity {
        var sport: String? = null
        var trackName: String? = null
        var metadataName: String? = null
        var description: String? = null
        var point: SampleBuilder? = null
        val samples = mutableListOf<DecodedSample>()
        walkXml(
            xml,
            onStart = { path, attribute ->
                if (path.last() == "trkpt") point = SampleBuilder(attribute("lat")?.toDoubleOrNull(), attribute("lon")?.toDoubleOrNull())
            },
            onEnd = { path, text ->
                val name = path.last()
                val current = point
                when {
                    name == "trkpt" -> {
                        current?.let { samples += it.build() }
                        point = null
                    }
                    current != null -> current.read(name, text)
                    name == "type" && path.parent == "trk" && sport == null -> sport = text.ifEmpty { null }
                    name == "name" && path.parent == "trk" && trackName == null -> trackName = text.ifEmpty { null }
                    name == "desc" && path.parent == "trk" && description == null -> description = text.ifEmpty { null }
                    name == "name" && path.parent == "metadata" -> metadataName = text.ifEmpty { null }
                }
            },
        )
        if (samples.none { it.timestampMillis != null }) {
            activityFileError("This GPX file has no times, so it's a planned route rather than a recorded activity")
        }
        // Only the sport: GPX has no totals, so the mapper computes them from the samples.
        val summary = sport?.let { DecodedSummary(it, null, null, null, null, null, null, null, null, null, null, null, null, null) }
        return DecodedActivity(
            fileId = null,
            sessions = listOfNotNull(summary),
            laps = emptyList(),
            records = samples,
            title = trackName ?: metadataName,
            notes = description,
        )
    }

    private fun SampleBuilder.read(name: String, text: String) {
        when (name.lowercase()) {
            "ele" -> altitude = text.toDoubleOrNull()
            "time" -> time = parseXmlTime(text)
            "hr",
            "heartrate" -> heartRate = text.toIntValue()
            "cad",
            "cadence" -> cadence = text.toIntValue()
            "atemp",
            "temp" -> temperature = text.toDoubleOrNull()
            "power",
            "watts" -> power = text.toIntValue()
            "speed" -> speed = text.toDoubleOrNull()
        }
    }
}
