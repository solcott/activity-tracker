package io.github.solcott.activitytracker.data.importsave

import io.github.solcott.activitytracker.data.importsave.KeepWay.COPY
import io.github.solcott.activitytracker.data.importsave.KeepWay.GRANT
import io.github.solcott.activitytracker.data.importsave.KeepWay.NONE
import kotlin.test.Test
import kotlin.test.assertEquals

class GrantBudgetTest {
    private val small = KeepCandidate(size = 100_000, grantable = true)
    private val large = KeepCandidate(size = MAX_COPY_BYTES + 1, grantable = true)

    @Test
    fun capFollowsAndroid11() {
        assertEquals(128, persistedGrantCap(29))
        assertEquals(512, persistedGrantCap(30))
    }

    @Test
    fun grantsWithinBudget() {
        assertEquals(listOf(GRANT, GRANT), planKeeping(existingGrants = 0, sdkInt = 34, candidates = listOf(small, large)))
    }

    @Test
    fun keepsSpareAndCountsExistingGrants() {
        // 512 - 64 spare - 446 held = 2 left.
        val ways = planKeeping(existingGrants = 446, sdkInt = 34, candidates = List(4) { small })
        assertEquals(2, ways.count { it == GRANT })
        assertEquals(2, ways.count { it == COPY })
    }

    @Test
    fun largestSourcesTakeTheGrantsAndSmallOnesAreCopied() {
        // 128 - 64 - 63 = 1 grant below Android 11: the large file takes it, wherever it is in the pick.
        assertEquals(listOf(COPY, GRANT, COPY), planKeeping(existingGrants = 63, sdkInt = 29, candidates = listOf(small, large, small)))
    }

    @Test
    fun pastBudgetLargeSourcesAreNotKept() {
        assertEquals(listOf(NONE, COPY), planKeeping(existingGrants = 600, sdkInt = 34, candidates = listOf(large, small)))
    }

    @Test
    fun ungrantableSourcesAreCopiedWhenSmall() {
        val ways = planKeeping(0, 34, listOf(KeepCandidate(10, grantable = false), KeepCandidate(MAX_COPY_BYTES + 1, grantable = false)))
        assertEquals(listOf(COPY, NONE), ways)
    }

    @Test
    fun keptSourceRoundTripsThroughItsStoredForm() {
        val kept = KeptSource(KeptSource.Kind.COPY, byteArrayOf(1, 2, 3))
        assertEquals(kept, KeptSource.of(kept.kind.name, kept.data.copyOf()))
        assertEquals(null, KeptSource.of("unknown", byteArrayOf()))
    }
}
