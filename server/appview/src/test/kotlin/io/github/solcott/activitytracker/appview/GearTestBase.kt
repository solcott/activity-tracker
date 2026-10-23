package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.ActivityGear
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.GearItems
import io.github.solcott.activitytracker.appview.db.GearMountRows
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Alice's gear and the rides that list it, indexed into a fresh H2 database (`gear/activity-gear`). */
open class GearTestBase {
    protected val service = "did:web:localhost%3A8081"
    protected val alice = "did:plc:alice"
    protected val bob = "did:plc:bob"
    protected val carol = "did:plc:carol"
    protected val dave = "did:plc:dave"
    protected val keys = listOf(alice, bob, carol, dave).associateWith { TestAccountKey() }

    protected val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    protected val indexer = Indexer(db, HandleLookup { did -> did.value.substringAfterLast(':') + ".test" })
    protected val config = AppViewConfig.fromEnv(emptyMap())
    protected val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    protected val ride = "3kride"
    protected val rideUri = "at://$alice/${Nsid.FEED_ACTIVITY}/$ride"

    protected fun gearUri(rkey: String, did: String = alice) = "at://$did/${Nsid.GEAR_ITEM}/$rkey"

    protected fun space(tier: String) = SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, tier)

    protected fun commit(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-14T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey",
            )
        )
    }

    /** Alice's [collection] record [json] at [tier]: in her repo when public, in her tier space otherwise. */
    protected fun put(collection: String, tier: String, rkey: String, json: String) {
        if (tier == Visibility.PUBLIC) commit(alice, collection, rkey, json)
        else runBlocking { indexer.spaceWrite(space(tier), collection, rkey, "bafy$tier$rkey", AtJson.parseToJsonElement(json).jsonObject) }
    }

    protected fun remove(collection: String, tier: String, rkey: String) {
        if (tier == Visibility.PUBLIC) commit(alice, collection, rkey, null)
        else runBlocking { indexer.spaceDelete(space(tier), collection, rkey) }
    }

    protected fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-10-01T09:00:00Z"}""",
        )

    /** Alice's gear item [rkey] at [tier]. */
    protected fun item(rkey: String, tier: String, name: String, type: String = "bike", extra: String = "") =
        put(Nsid.GEAR_ITEM, tier, rkey, """{"name":"$name","type":"$type",$extra"createdAt":"2026-10-01T09:00:00Z"}""")

    /** [mounts] as an item's `mounts` field with its trailing comma, for [item]'s `extra`: each a parent rkey (or URI), from, and to. */
    protected fun mounts(vararg mounts: Triple<String, String, String?>) =
        """"mounts":[""" +
            mounts.joinToString(",") { (parent, from, to) ->
                val uri = if (parent.startsWith("at://")) parent else gearUri(parent)
                """{"parent":"$uri","from":"$from"""" + to?.let { ""","to":"$it"""" }.orEmpty() + "}"
            } +
            "],"

    /** A mount on [parent] from [from] until [to], for [mounts]. */
    protected fun on(parent: String, from: String, to: String? = null) = Triple(parent, from, to)

    protected fun uris(gear: List<String>) = gear.joinToString(",") { "\"$it\"" }

    /** Alice's ride at [tier], listing [gear] on its record when given. */
    protected fun ride(tier: String = Visibility.PUBLIC, gear: List<String>? = null) =
        put(
            Nsid.FEED_ACTIVITY,
            tier,
            ride,
            """{"sport":"ride","startedAt":"2026-10-05T07:00:00Z","elapsedTime":3600,""" +
                gear?.let { """"gear":[${uris(it)}],""" }.orEmpty() +
                """"createdAt":"2026-10-05T08:00:00Z"}""",
        )

    /**
     * Alice's activity [rkey] at [tier]: a [sport] starting at [start] for [elapsed] seconds, with [stats] (`"distance":40000,` and so on,
     * each with its trailing comma), listing [gear] on its record when given.
     */
    @Suppress("LongParameterList") // One per field the usage tests vary.
    protected fun activity(
        rkey: String,
        tier: String = Visibility.PUBLIC,
        sport: String = "ride",
        start: String = "2026-10-05T07:00:00Z",
        elapsed: Long = 3600,
        stats: String = "",
        gear: List<String>? = null,
    ) =
        put(
            Nsid.FEED_ACTIVITY,
            tier,
            rkey,
            """{"sport":"$sport","startedAt":"$start","elapsedTime":$elapsed,$stats""" +
                gear?.let { """"gear":[${uris(it)}],""" }.orEmpty() +
                """"createdAt":"$start"}""",
        )

    protected fun activityUri(rkey: String) = "at://$alice/${Nsid.FEED_ACTIVITY}/$rkey"

    /** Activity [rkey]'s details record at [tier], listing [gear]. */
    protected fun details(rkey: String, tier: String, gear: List<String>) =
        put(
            Nsid.FEED_ACTIVITY_DETAILS,
            tier,
            rkey,
            """{"activity":"${activityUri(rkey)}","gear":[${uris(gear)}],"createdAt":"2026-10-05T08:00:00Z"}""",
        )

    /** The ride's details record at [tier], listing [gear]. */
    protected fun gearDetails(tier: String, gear: List<String>) =
        put(Nsid.FEED_ACTIVITY_DETAILS, tier, ride, """{"activity":"$rideUri","gear":[${uris(gear)}],"createdAt":"2026-10-05T08:00:00Z"}""")

    /** The indexed gear rows: uri to (origin, visibility). */
    protected fun gearRows(): Map<String, Pair<String, String>> = runBlocking {
        db.tx { GearItems.selectAll().associate { it[GearItems.uri] to (it[GearItems.origin] to it[GearItems.visibility]) } }
    }

    /** The indexed mounts: component URI to (parent URI, from ms, to ms) in date order. */
    protected fun gearMounts(): Map<String, List<Triple<String, Long, Long?>>> = runBlocking {
        db.tx {
            GearMountRows.selectAll()
                .sortedBy { it[GearMountRows.mountedFrom] }
                .groupBy({ it[GearMountRows.componentUri] }) {
                    Triple(it[GearMountRows.parentUri], it[GearMountRows.mountedFrom], it[GearMountRows.mountedTo])
                }
        }
    }

    /** The ride's indexed gear references: (source, visibility) to the URIs in order. */
    protected fun activityGear(): Map<Pair<String, String>, List<String>> = runBlocking {
        db.tx {
            ActivityGear.selectAll()
                .where { ActivityGear.activityUri eq rideUri }
                .sortedBy { it[ActivityGear.position] }
                .groupBy({ it[ActivityGear.listedIn] to it[ActivityGear.visibility] }) { it[ActivityGear.gearUri] }
        }
    }
}
