package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.GearMount
import io.github.solcott.activitytracker.lexicon.app.GearMounts
import io.github.solcott.activitytracker.lexicon.app.MountGraph
import io.github.solcott.activitytracker.lexicon.app.MountProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** `gear.item#mount` rules: one parent at a time, no loops, at most three levels. */
class GearMountsTest {
    private fun item(rkey: String, did: String = "did:plc:alice") = AtUri("at://$did/${Nsid.GEAR_ITEM}/$rkey")

    private fun day(date: String) = Instant.parse("${date}T00:00:00Z")

    private val tarmac = item("tarmac")
    private val stumpjumper = item("stumpy")
    private val wheelset = item("wheels")
    private val tires = item("tires")
    private val chain = item("chain")

    private fun graph(vararg mounts: Pair<AtUri, AtUri>) =
        MountGraph.of(mounts.groupBy({ it.first }, { GearMount(it.second, day("2026-01-01")) }))

    @Test
    fun aChainOnABikeIsValid() {
        val mounts = listOf(GearMount(tarmac, day("2026-03-01")))
        assertNull(GearMounts.problem(chain, mounts, graph()))
        assertEquals(mounts.single(), GearMounts.open(mounts))
        assertEquals(mounts.single(), GearMounts.at(mounts, day("2026-04-01")))
        assertNull(GearMounts.at(mounts, day("2026-02-01")))
    }

    @Test
    fun aPowerMeterMovedBetweenBikesKeepsBothMountsInOrder() {
        val mounts = listOf(GearMount(stumpjumper, day("2026-06-01")), GearMount(tarmac, day("2026-03-01"), day("2026-06-01")))
        assertNull(GearMounts.problem(item("pm"), mounts, graph()))
        assertEquals(listOf(tarmac, stumpjumper), GearMounts.kept(item("pm"), mounts, graph()).map { it.parent })
    }

    @Test
    fun theRootAtAnInstantFollowsTheMountsThen() {
        val mounts =
            mapOf(
                item("pm") to listOf(GearMount(tarmac, day("2026-03-01"), day("2026-06-01")), GearMount(stumpjumper, day("2026-06-01"))),
                tires to listOf(GearMount(wheelset, day("2026-01-01"))),
                wheelset to listOf(GearMount(tarmac, day("2026-02-01"))),
            )
        val mountsOf = { uri: AtUri -> mounts[uri] }
        assertEquals(tarmac, GearMounts.rootAt(item("pm"), day("2026-04-15"), mountsOf))
        assertEquals(tarmac, GearMounts.rootAt(item("pm"), day("2026-05-15"), mountsOf), "moved only in June")
        assertEquals(stumpjumper, GearMounts.rootAt(item("pm"), day("2026-07-01"), mountsOf))
        assertEquals(item("pm"), GearMounts.rootAt(item("pm"), day("2026-01-01"), mountsOf), "not mounted yet")
        assertEquals(tarmac, GearMounts.rootAt(tires, day("2026-03-01"), mountsOf), "through the wheelset")
        assertEquals(wheelset, GearMounts.rootAt(tires, day("2026-01-15"), mountsOf), "before the wheelset was on the bike")
    }

    @Test
    fun anOverlappingMountIsRefusedAndOnlyItIsDropped() {
        val mounts = listOf(GearMount(tarmac, day("2026-03-01")), GearMount(stumpjumper, day("2026-05-01")))
        assertEquals(MountProblem.OVERLAP, GearMounts.problem(chain, mounts, graph()))
        assertEquals(listOf(tarmac), GearMounts.kept(chain, mounts, graph()).map { it.parent })
    }

    @Test
    fun tiresOnAWheelsetOnABikeAreValid() {
        val bikeGraph = graph(wheelset to tarmac)
        assertNull(GearMounts.problem(tires, listOf(GearMount(wheelset, day("2026-03-01"))), bikeGraph))
        // and in the other order: the wheelset onto the bike after the tires onto the wheelset
        assertNull(GearMounts.problem(wheelset, listOf(GearMount(tarmac, day("2026-03-01"))), graph(tires to wheelset)))
    }

    @Test
    fun aLoopIsRefused() {
        val g = graph(tires to wheelset, wheelset to tarmac)
        assertEquals(MountProblem.LOOP, GearMounts.problem(tarmac, listOf(GearMount(wheelset, day("2026-03-01"))), g))
        assertEquals(MountProblem.LOOP, GearMounts.problem(tarmac, listOf(GearMount(tires, day("2026-03-01"))), g))
    }

    @Test
    fun aFourthLevelIsRefused() {
        val valve = item("valve")
        val g = graph(tires to wheelset, wheelset to tarmac)
        // valve on tires is the third level: fine
        assertNull(GearMounts.problem(valve, listOf(GearMount(tires, day("2026-03-01"))), g))
        val cap = item("cap")
        val deeper = graph(tires to wheelset, wheelset to tarmac, valve to tires)
        assertEquals(MountProblem.TOO_DEEP, GearMounts.problem(cap, listOf(GearMount(valve, day("2026-03-01"))), deeper))
        // or the whole three-level stack onto a fourth item below the bike
        val stack = graph(tires to wheelset, valve to tires)
        assertEquals(
            MountProblem.TOO_DEEP,
            GearMounts.problem(
                wheelset,
                listOf(GearMount(item("hub"), day("2026-03-01"))),
                graph(tires to wheelset, valve to tires, item("hub") to tarmac),
            ),
        )
        assertNull(GearMounts.problem(wheelset, listOf(GearMount(tarmac, day("2026-03-01"))), stack))
    }

    @Test
    fun anotherAthletesItemItselfOrAnEmptyWindowIsRefused() {
        assertEquals(
            MountProblem.FOREIGN_PARENT,
            GearMounts.problem(chain, listOf(GearMount(item("bike", "did:plc:bob"), day("2026-03-01"))), graph()),
        )
        assertEquals(MountProblem.FOREIGN_PARENT, GearMounts.problem(chain, listOf(GearMount(chain, day("2026-03-01"))), graph()))
        assertEquals(
            MountProblem.EMPTY,
            GearMounts.problem(chain, listOf(GearMount(tarmac, day("2026-03-01"), day("2026-03-01"))), graph()),
        )
    }
}
