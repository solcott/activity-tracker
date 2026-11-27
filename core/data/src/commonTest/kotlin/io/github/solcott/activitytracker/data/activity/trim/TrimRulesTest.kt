package io.github.solcott.activitytracker.data.activity.trim

import io.github.solcott.activitytracker.data.activity.trim.TrimRules.Refusal
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.Leg
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrimRulesTest {
    private val ride = TrimFixtures.record

    private fun refusal(
        record: io.github.solcott.activitytracker.lexicon.app.ActivityRecord = ride,
        owner: Boolean = true,
        member: Boolean = false,
        streams: ActivityStreamData? = null,
    ) = TrimRules.refusal(record, owner, member, streams)

    @Test fun ownersRideCanBeTrimmed() = assertNull(refusal())

    @Test fun onlyTheOwner() = assertEquals(Refusal.NotOwner, refusal(owner = false))

    @Test
    fun notACombinedActivityOrItsMembers() {
        val combined = ride.copy(combined = CombinedActivity(members = listOf(AtUri("at://did:plc:a/x/1"), AtUri("at://did:plc:a/x/2"))))
        assertEquals(Refusal.Combined, refusal(combined))
        assertEquals(Refusal.CombinedMember, refusal(member = true))
    }

    @Test
    fun notMultisportDivesOrSets() {
        assertEquals(Refusal.Multisport, refusal(ride.copy(sport = Sport.MULTISPORT)))
        assertEquals(Refusal.Multisport, refusal(streams = ActivityStreamData(time = listOf(0), legs = listOf(Leg(0, 10, Sport.RUN)))))
        assertEquals(Refusal.Dive, refusal(ride.copy(dive = ActivityDive())))
        assertEquals(Refusal.Sets, refusal(streams = ActivityStreamData(time = listOf(0), sets = listOf(WorkoutSet(0, 30)))))
    }
}
