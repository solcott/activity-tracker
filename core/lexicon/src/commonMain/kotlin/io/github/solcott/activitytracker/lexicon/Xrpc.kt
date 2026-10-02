package io.github.solcott.activitytracker.lexicon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * Typed XRPC method descriptors, shared by the client (to call) and the AppView (to route and decode params). Query params are data classes
 * encoded to/from the query string via [AtJson].
 */
sealed interface XrpcMethod {
    val nsid: String
}

class XrpcQuery<P, O>(
    override val nsid: String,
    val params: KSerializer<P>,
    val output: KSerializer<O>,
) : XrpcMethod

class XrpcProcedure<I, O>(
    override val nsid: String,
    val input: KSerializer<I>,
    val output: KSerializer<O>,
) : XrpcMethod

@Serializable data object NoParams

@Serializable data object NoOutput

val NoParamsSerializer: KSerializer<NoParams> = NoParams.serializer()
val UnitSerializer: KSerializer<Unit> = Unit.serializer()

/** The standard XRPC error body. */
@Serializable data class XrpcErrorBody(val error: String, val message: String? = null)
