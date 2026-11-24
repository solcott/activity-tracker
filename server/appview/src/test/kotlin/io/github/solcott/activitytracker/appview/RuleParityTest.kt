package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.SpaceAccess
import io.github.solcott.activitytracker.appview.api.Subject
import io.github.solcott.activitytracker.appview.api.admittedComments
import io.github.solcott.activitytracker.appview.api.commentTier
import io.github.solcott.activitytracker.appview.api.commentsVisibleTo
import io.github.solcott.activitytracker.appview.api.pairRuleHolds
import io.github.solcott.activitytracker.appview.api.visibleTo
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityGroupPairs
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.appview.segments.mayPair
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.ManagedSpace.CheckAccess
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Rules kept twice, once in Kotlin and once as SQL (or as two SQL shapes), checked against each other over the same fixtures, so a change
 * to one side that the other misses fails here: `mayPair` against `pairRuleHolds`, `SpaceAccess.canRead` against `visibleTo`'s tier
 * admission, and `commentTier` against `commentsVisibleTo`.
 *
 * Alice is the athlete. Bob follows her; carol and she follow each other; dave follows no one she knows but erin, whose follows need
 * approval and who hasn't approved him; erin follows alice, who blocks her.
 */
class RuleParityTest : CommentTestBase() {
    private val viewers = listOf(bob, carol, dave, erin)

    private fun seed() {
        listOf(alice, bob, carol, dave).forEach { commit(it, Nsid.ACTOR_PROFILE, "self", """{"displayName":"${it.last()}"}""") }
        commit(erin, Nsid.ACTOR_PROFILE, "self", """{"displayName":"e","followPolicy":"approve"}""")
        follow(bob, alice)
        follow(carol, alice)
        follow(alice, carol)
        follow(dave, erin)
        follow(erin, alice)
        runBlocking {
            indexer.spaceWrite(
                SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS),
                Nsid.GRAPH_BLOCK,
                erin,
                "bafyblock",
                AtJson.parseToJsonElement("""{"subject":"$erin","createdAt":"2026-10-01T09:00:00Z"}""").jsonObject,
            )
        }
    }

    @Test
    fun mayPairAgreesWithPairRuleHolds() {
        seed()
        val pairs = listOf(alice to bob, bob to alice, alice to carol, alice to dave, alice to erin, dave to erin, bob to carol)
        val modes = listOf(GroupMode.OFF, GroupMode.CONNECTED, GroupMode.ANYONE, "sometimes")
        val outcomes = mutableSetOf<Pair<String, Boolean>>()
        for ((a, b) in pairs) for (modeA in modes) for (modeB in modes) {
            val (kotlin, sql) =
                transaction(db) {
                    ActivityGroupPairs.deleteAll()
                    listOf(a to modeA, b to modeB).forEach { (did, mode) ->
                        GroupPrefs.upsert {
                            it[GroupPrefs.did] = did
                            it[GroupPrefs.mode] = mode
                            it[updatedAt] = 0
                        }
                    }
                    ActivityGroupPairs.insert {
                        it[activityA] = "at://$a/${Nsid.FEED_ACTIVITY}/3ka"
                        it[activityB] = "at://$b/${Nsid.FEED_ACTIVITY}/3kb"
                        it[authorA] = a
                        it[authorB] = b
                        it[visibilityA] = Visibility.PUBLIC
                        it[visibilityB] = Visibility.PUBLIC
                        it[matchedAt] = 0
                    }
                    mayPair(a, modeA, b, modeB) to ActivityGroupPairs.selectAll().where { pairRuleHolds() }.any()
                }
            assertEquals(kotlin, sql, "$a ($modeA) with $b ($modeB)")
            if (modeA == GroupMode.CONNECTED && modeB == GroupMode.CONNECTED) outcomes += "$a $b" to kotlin
        }
        // Both connected: only the effective follows and no block pair.
        assertEquals(
            setOf("$alice $bob" to true, "$bob $alice" to true, "$alice $carol" to true) +
                setOf("$alice $dave", "$alice $erin", "$dave $erin", "$bob $carol").map { it to false },
            outcomes,
        )
    }

    @Test
    fun canReadAgreesWithVisibleTo() = runBlocking {
        seed()
        val tiers = listOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS, Visibility.SELF)
        tiers.forEach { post("3k$it", it) }
        val access = SpaceAccess(db, Did("did:web:appview.test"))
        for (tier in tiers) for (viewer in viewers) {
            val space = SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, tier)
            val canRead = access.check(Did(alice), CheckAccess.Params(space, repo = Did(viewer))).read
            val admitted =
                transaction(db) {
                    Activities.select(Activities.uri).where { (Activities.uri eq uri("3k$tier")) and visibleTo(Did(viewer)) }.any()
                }
            assertEquals(canRead, admitted, "$viewer reading alice's $tier tier")
            assertEquals(admitsTier(viewer, tier), canRead, "$viewer reading alice's $tier tier, by hand")
        }
    }

    @Test
    fun commentTierAgreesWithCommentsVisibleTo() {
        seed()
        val audiences = listOf(null, Visibility.FOLLOWERS, Visibility.MUTUALS, Visibility.SELF)
        var n = 0
        for (tier in listOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS)) {
            val activity = "3kr$tier"
            post(activity, tier)
            for (rootAudience in audiences) {
                val root = comment(alice, "3kc${n++}", n, activity, rootAudience)
                // Replies written wider and narrower than their root, so the root's cap is exercised both ways.
                for (audience in audiences) comment(alice, "3kc${n++}", n, activity, audience, parent = root)
            }
        }
        for (viewer in viewers.map(::Did) + null) {
            transaction(db) {
                val rows = admittedComments(Subject.ACTIVITY).selectAll().where { Comments.authorDid eq alice }.toList()
                val shown =
                    admittedComments(Subject.ACTIVITY)
                        .select(Comments.uri)
                        .where { (Comments.authorDid eq alice) and visibleTo(viewer) and commentsVisibleTo(viewer, Subject.ACTIVITY) }
                        .map { it[Comments.uri] }
                        .toSet()
                for (row in rows) {
                    val expected = admitsTier(viewer?.value, commentTier(row, Subject.ACTIVITY))
                    assertEquals(expected, row[Comments.uri] in shown, "${viewer?.value} on ${row[Comments.uri]}")
                }
            }
        }
    }

    /** Who alice's fixture admits to [tier], written out by hand. */
    private fun admitsTier(viewer: String?, tier: String): Boolean =
        when (tier) {
            Visibility.PUBLIC -> viewer != erin
            Visibility.FOLLOWERS -> viewer == bob || viewer == carol
            Visibility.MUTUALS -> viewer == carol
            else -> false
        }
}
