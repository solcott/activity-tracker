package io.github.solcott.activitytracker.lexicon.atproto

import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import kotlinx.serialization.Serializable

object GetServiceAuth {
    @Serializable
    data class Params(
        /** DID of the service the token is for (the AppView's `did:web`). */
        val aud: String,
        /** Unix seconds; PDSes cap this at ~1 hour. */
        val exp: Long? = null,
        /** Lexicon method the token is bound to. */
        val lxm: String? = null,
    )

    @Serializable data class Output(val token: String)

    val method = XrpcQuery("com.atproto.server.getServiceAuth", Params.serializer(), Output.serializer())
}

/**
 * `com.atproto.server.createSession`: signs in with an identifier (handle or DID) and an app password. Server tools only (the segment
 * importer's service account); clients sign in with OAuth.
 */
object CreateSession {
    @Serializable data class Input(val identifier: String, val password: String)

    @Serializable data class Output(val accessJwt: String, val refreshJwt: String, val did: Did, val handle: Handle? = null)

    val method = XrpcProcedure("com.atproto.server.createSession", Input.serializer(), Output.serializer())
}

/** `com.atproto.server.refreshSession`: a new [CreateSession.Output], authorized by its refresh JWT rather than an input. */
object RefreshSession {
    const val NSID = "com.atproto.server.refreshSession"
}

object ResolveHandle {
    @Serializable data class Params(val handle: String)

    @Serializable data class Output(val did: Did)

    val method = XrpcQuery("com.atproto.identity.resolveHandle", Params.serializer(), Output.serializer())
}

object DescribeRepo {
    @Serializable data class Params(val repo: String)

    @Serializable data class Output(val handle: Handle, val did: Did, val collections: List<String> = emptyList())

    val method = XrpcQuery("com.atproto.repo.describeRepo", Params.serializer(), Output.serializer())
}
