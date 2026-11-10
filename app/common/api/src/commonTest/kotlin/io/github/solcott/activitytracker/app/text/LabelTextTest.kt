package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Gender
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals

/** Labels looked up by a token, and what each shows for a token the app has no string for (dev/shared-display-text). */
class LabelTextTest {
    /** Strings for the tokens the app names here; anything else has none. */
    private val source =
        object : TextSource {
            private val strings =
                mapOf(
                    "sport_run" to "Run",
                    "sport_ride" to "Ride",
                    "sub_sport_ride_mountain" to "Mountain Bike Ride",
                    "dive_alert_ndlReached" to "No-deco limit reached",
                    "sensor_power" to "Power meter",
                    "sensor_other" to "Sensor",
                    "handle_format" to "@{1}",
                    "gender_female" to "Female",
                    "detail_body" to "Body",
                    "zones_visibility_heading" to "Zones",
                )

            override fun string(name: String, args: List<String>): String? =
                strings[name]?.let { args.foldIndexed(it) { index, text, arg -> text.replace("{${index + 1}}", arg) } }

            override fun plural(name: String, quantity: Int, args: List<String>): String? = null
        }

    private fun AppText.read() = resolve(source)

    @Test
    fun aSport() {
        assertEquals("Run", LabelText.sport(Sport.RUN).read())
        assertEquals("Quidditch", LabelText.sport("quidditch").read())
    }

    @Test
    fun anActivityType() {
        assertEquals("Mountain Bike Ride", LabelText.activityType(ActivityType(Sport.RIDE, SubSport.MOUNTAIN)).read())
        assertEquals("Ride", LabelText.activityType(ActivityType(Sport.RIDE, "unicycle")).read())
        assertEquals("Ride", LabelText.activityType(ActivityType(Sport.RIDE)).read())
        assertEquals("Quidditch", LabelText.activityType(ActivityType("quidditch", "seeker")).read())
    }

    @Test
    fun aDiveAlert() {
        assertEquals("No-deco limit reached", LabelText.diveAlert("ndlReached").read())
        assertEquals("somethingNew", LabelText.diveAlert("somethingNew").read())
    }

    @Test
    fun aSensor() {
        assertEquals("Power meter", LabelText.sensorType("power").read())
        assertEquals("Sensor", LabelText.sensorType("tricorder").read())
    }

    @Test fun aHandle() = assertEquals("@alice.test", LabelText.handle("alice.test").read())

    @Test
    fun aGender() {
        assertEquals("Female", LabelText.gender(Gender.FEMALE).read())
        assertEquals("agender", LabelText.gender("agender").read())
    }

    @Test
    fun aDetailGroup() {
        assertEquals("Body", LabelText.detailGroup(DetailGroup.Body).read())
        assertEquals("Zones", LabelText.visibilityScope(DetailGroup.Training).read())
    }
}
