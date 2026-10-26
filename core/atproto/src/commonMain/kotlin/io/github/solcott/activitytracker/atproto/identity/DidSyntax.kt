package io.github.solcott.activitytracker.atproto.identity

/**
 * Whether [did] is one [IdentityResolver] may look up: a `did:plc` of lowercase base32 characters, or a `did:web` naming a bare host with
 * an optional `%3A` port. Checked before any request, so a DID can't carry a path or query into the URL it is fetched from.
 */
fun isResolvableDid(did: String): Boolean = PLC.matches(did) || WEB.matches(did)

private val PLC = Regex("did:plc:[a-z2-7]+")

private const val LABEL = "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"

private val WEB = Regex("did:web:$LABEL(?:\\.$LABEL)*(?:%3[aA][0-9]{1,5})?")
