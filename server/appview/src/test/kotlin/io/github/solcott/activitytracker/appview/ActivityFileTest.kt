package io.github.solcott.activitytracker.appview

import com.garmin.fit.BufferEncoder
import com.garmin.fit.DateTime
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.Manufacturer
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport as FitSport
import io.github.solcott.activitytracker.activityfile.DecodedActivityMapper
import io.github.solcott.activitytracker.activityfile.GarminJavaFitDecoder
import io.github.solcott.activitytracker.lexicon.app.Sport
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shared FIT parser runs on the AppView's classpath (JVM 25, no Metro graph), ready for when the AppView reads activity files. */
class ActivityFileTest {
    @Test
    fun decodesAFitActivity() {
        val start = DateTime(Instant.parse("2026-09-20T07:00:00Z"))
        val bytes =
            BufferEncoder(Fit.ProtocolVersion.V2_0).run {
                write(
                    FileIdMesg().apply {
                        type = File.ACTIVITY
                        manufacturer = Manufacturer.GARMIN
                        timeCreated = start
                    }
                )
                write(
                    SessionMesg().apply {
                        timestamp = start
                        startTime = start
                        sport = FitSport.CYCLING
                        totalElapsedTime = 3600f
                        totalDistance = 30_000f
                    }
                )
                close()
            }

        val activity = DecodedActivityMapper.map(GarminJavaFitDecoder().decode(bytes))

        assertEquals(Sport.RIDE, activity.sport)
        assertEquals(30_000.0, activity.distanceMeters)
        assertEquals("Garmin", activity.device)
    }
}
