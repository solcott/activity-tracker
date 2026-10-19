package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ChartAttribution
import io.github.solcott.activitytracker.data.activity.Credit
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ChannelSource
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.MemberView
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** `feed/combined-sources`: which device each chart of a combined activity came from. */
class ChartAttributionTest {
    private fun uri(rkey: String) = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/$rkey")

    private fun at(time: String) = Instant.parse("2026-10-14T${time}:00Z")

    private fun member(rkey: String, from: String, to: String) =
        MemberView(uri(rkey), at(from), (at(to) - at(from)).inWholeSeconds, device = rkey)

    private fun combined(vararg ranking: String) =
        CombinedActivity(
            members = ranking.map { uri(it) },
            sources = listOf(ChannelSource(StreamChannel.HEART_RATE, ranking.map { uri(it) })),
        )

    @Test
    fun aSingleSourceGivesOneDevice() {
        val members = listOf(member("edge", "09:00", "11:00"), member("watch", "09:00", "11:00"))
        val credits =
            ChartAttribution.of(
                StreamChannel.POWER,
                CombinedActivity(members.map { it.uri }, listOf(ChannelSource(StreamChannel.POWER, listOf(uri("edge"))))),
                members,
            )
        assertEquals(listOf(Credit(uri("edge"), at("09:00"), at("11:00"))), credits)
    }

    @Test
    fun aWatchDyingThenAPhoneGivesTwo() {
        val members = listOf(member("watch", "09:00", "10:40"), member("phone", "09:00", "11:30"))
        val credits = ChartAttribution.of(StreamChannel.HEART_RATE, combined("watch", "phone"), members)
        assertEquals(listOf(Credit(uri("watch"), at("09:00"), at("10:40")), Credit(uri("phone"), at("10:40"), at("11:30"))), credits)
        assertEquals(
            listOf(uri("watch"), uri("phone")),
            ChartAttribution.membersOf(StreamChannel.HEART_RATE, combined("watch", "phone"), members),
        )
    }

    @Test
    fun aLowerRankedMemberNeverShowsWhileAHigherOneCoversTheTime() {
        val members = listOf(member("edge", "08:55", "11:05"), member("watch", "09:00", "11:00"))
        assertEquals(listOf(uri("edge")), ChartAttribution.membersOf(StreamChannel.HEART_RATE, combined("edge", "watch"), members))
    }

    @Test
    fun aChannelLeftOutOrUnrankedHasNoCredit() {
        val members = listOf(member("edge", "09:00", "11:00"), member("watch", "09:00", "11:00"))
        val excluded = CombinedActivity(members.map { it.uri }, listOf(ChannelSource(StreamChannel.POWER, emptyList())))
        assertEquals(emptyList(), ChartAttribution.of(StreamChannel.POWER, excluded, members))
        assertEquals(emptyList(), ChartAttribution.of(StreamChannel.CADENCE, excluded, members))
    }
}
