package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.activitytracker.lexicon.AtJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** Every builder's defaults make a valid lexicon value: it survives a round trip through the wire format. */
class FakesTest {
    private inline fun <reified T> roundTrips(value: T) {
        val serializer: KSerializer<T> = serializer()
        assertEquals(value, AtJson.decodeFromString(serializer, AtJson.encodeToString(serializer, value)))
    }

    @Test fun profileBasic() = roundTrips(fakeProfileBasic())

    @Test fun profile() = roundTrips(fakeProfile())

    @Test fun activity() = roundTrips(fakeActivity())

    @Test fun segment() = roundTrips(fakeSegment())

    @Test fun comment() = roundTrips(fakeComment())

    @Test fun gearItem() = roundTrips(fakeGearItem())

    @Test fun effort() = roundTrips(fakeEffort())

    @Test fun leaderboardEntry() = roundTrips(fakeLeaderboardEntry())

    @Test fun mayorEntry() = roundTrips(fakeMayorEntry())
}
