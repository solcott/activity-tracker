package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.ResolvingPdsLookup
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.net.CachingIdentityResolver
import io.github.solcott.activitytracker.appview.net.GuardedHttp
import io.github.solcott.activitytracker.appview.net.PlcPacing
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.websocket.WebSockets

/**
 * The AppView's outbound clients and identity resolvers (identity/outbound-requests). Configured endpoints (PLC, the handle resolver,
 * Jetstream, labelers, Ozone) are trusted ([http]); every host learned from a DID document or a handle goes through [guarded], which
 * refuses private addresses. Both name the AppView in `User-Agent`. DID documents are cached, and every `did:plc` fetch shares
 * [plcPacing]'s pause after a 429. [configuredIdentity] is only for the operator's own labeler and Ozone DIDs, whose endpoints may be on
 * the local network.
 */
class Outbound(
    val http: HttpClient,
    val guarded: HttpClient,
    val plcPacing: PlcPacing,
    val identity: CachingIdentityResolver,
    val configuredIdentity: CachingIdentityResolver,
) {
    companion object {
        fun of(config: AppViewConfig): Outbound {
            val agent = "activitytracker-appview (+${config.publicUrl})"
            val http =
                HttpClient(CIO) {
                    expectSuccess = false
                    install(WebSockets)
                    install(UserAgent) { this.agent = agent }
                }
            val guarded = GuardedHttp.create(config.allowPrivateAddresses, userAgent = agent)
            val pacing = PlcPacing()
            return Outbound(
                http,
                guarded,
                pacing,
                CachingIdentityResolver(http, config.plcUrl, config.handleResolverUrl, untrusted = guarded, pacing = pacing),
                CachingIdentityResolver(http, config.plcUrl, config.handleResolverUrl, pacing = pacing),
            )
        }
    }
}

/** Drops [did] from every cache of identities, after an `#identity` event names it. */
internal suspend fun forgetIdentity(
    did: Did,
    identity: CachingIdentityResolver,
    verifier: ServiceAuthVerifier,
    pds: ResolvingPdsLookup,
    credentials: SpaceCredentials?,
) {
    identity.invalidate(did)
    verifier.forget(did)
    pds.invalidate(did)
    credentials?.forget(did)
}
