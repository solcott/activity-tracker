package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.time.Instant

/**
 * Fixed values the builders default to, so a sample renders the same on any machine and on any date. [NOW] is the harness's default current
 * time; every other instant is before it.
 */
object Fixed {
    val NOW: Instant = Instant.parse("2026-06-01T12:00:00Z")
    val STARTED_AT: Instant = Instant.parse("2026-05-31T07:00:00Z")
    val CREATED_AT: Instant = Instant.parse("2026-05-31T09:00:00Z")

    val ALICE = Did("did:plc:alice")
    val BOB = Did("did:plc:bob")
    val CAROL = Did("did:plc:carol")

    /** Three points around Denver, as a precision-5 polyline. */
    const val POLYLINE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    /** The record's URI in [author]'s repo. */
    fun uri(author: Did, collection: String, rkey: String): AtUri = AtUri.of(author, collection, rkey)

    fun cid(seed: String): Cid = Cid("bafyrei$seed")
}
