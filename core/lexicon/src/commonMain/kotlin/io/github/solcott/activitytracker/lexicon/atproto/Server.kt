package io.github.solcott.activitytracker.lexicon.atproto

import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
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
