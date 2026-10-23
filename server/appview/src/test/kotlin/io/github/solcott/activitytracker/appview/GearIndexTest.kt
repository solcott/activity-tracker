package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.ActivityGear
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/** Indexing gear items from repos and tier spaces, and the gear activities list (`gear/activity-gear`). */
class GearIndexTest : GearTestBase() {
    @Test
    fun aRepoItemIsIndexedPublic() {
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        assertEquals(mapOf(gearUri("3tarmac") to (Origins.REPO to Visibility.PUBLIC)), gearRows())
        remove(Nsid.GEAR_ITEM, Visibility.PUBLIC, "3tarmac")
        assertEquals(emptyMap(), gearRows())
    }

    @Test
    fun aSelfItemIsIndexedOnlyMe() {
        item("3meter", Visibility.SELF, "Power meter", type = "sensor")
        assertEquals(mapOf(gearUri("3meter") to (space(Visibility.SELF).value to Visibility.SELF)), gearRows())
    }

    @Test
    fun anItemMovingBetweenTiersIsOneRowAtTheNarrower() {
        item("3bike", Visibility.PUBLIC, "Stumpjumper")
        // The move writes the new place first, then deletes the old.
        item("3bike", Visibility.FOLLOWERS, "Stumpjumper")
        assertEquals(mapOf(gearUri("3bike") to (space(Visibility.FOLLOWERS).value to Visibility.FOLLOWERS)), gearRows())
        remove(Nsid.GEAR_ITEM, Visibility.PUBLIC, "3bike")
        assertEquals(mapOf(gearUri("3bike") to (space(Visibility.FOLLOWERS).value to Visibility.FOLLOWERS)), gearRows())

        // And back to Public: the Followers copy deleted promotes nothing, the repo copy is the row.
        item("3bike", Visibility.PUBLIC, "Stumpjumper")
        remove(Nsid.GEAR_ITEM, Visibility.FOLLOWERS, "3bike")
        assertEquals(mapOf(gearUri("3bike") to (Origins.REPO to Visibility.PUBLIC)), gearRows())
    }

    @Test
    fun anItemWithAnInvalidNameIsDropped() {
        item("3blank", Visibility.PUBLIC, " ")
        item("3long", Visibility.PUBLIC, "x".repeat(1001))
        assertEquals(emptyMap(), gearRows())
    }

    @Test
    fun aPublicRideListsItsGear() {
        ride(gear = listOf(gearUri("3tarmac"), gearUri("3helmet")))
        assertEquals(
            mapOf((ActivityGear.SOURCE_RECORD to Visibility.PUBLIC) to listOf(gearUri("3tarmac"), gearUri("3helmet"))),
            activityGear(),
        )
        // Re-indexed without gear, it lists none.
        ride()
        assertEquals(emptyMap(), activityGear())
    }

    @Test
    fun gearAtOnlyMeIsListedFromTheSelfDetails() {
        ride()
        gearDetails(Visibility.SELF, listOf(gearUri("3tarmac")))
        assertEquals(mapOf((ActivityGear.SOURCE_DETAILS to Visibility.SELF) to listOf(gearUri("3tarmac"))), activityGear())
        remove(Nsid.FEED_ACTIVITY_DETAILS, Visibility.SELF, ride)
        assertEquals(emptyMap(), activityGear())
    }

    @Test
    fun anotherAthletesItemOrAnotherCollectionIsIgnored() {
        ride(gear = listOf(gearUri("3theirs", did = bob), "at://$alice/${Nsid.FEED_ACTIVITY}/3other", gearUri("3tarmac")))
        assertEquals(mapOf((ActivityGear.SOURCE_RECORD to Visibility.PUBLIC) to listOf(gearUri("3tarmac"))), activityGear())
    }

    @Test
    fun aDroppedSpaceTakesItsGearAndReferences() {
        item("3meter", Visibility.FOLLOWERS, "Power meter", type = "sensor")
        ride()
        gearDetails(Visibility.FOLLOWERS, listOf(gearUri("3meter")))
        runBlocking { indexer.dropSpace(space(Visibility.FOLLOWERS)) }
        assertEquals(emptyMap(), gearRows())
        assertEquals(emptyMap(), activityGear())
    }
}
