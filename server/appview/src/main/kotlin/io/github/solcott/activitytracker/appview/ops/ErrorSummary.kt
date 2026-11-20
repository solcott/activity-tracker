package io.github.solcott.activitytracker.appview.ops

import com.fasterxml.jackson.core.JacksonException
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import kotlinx.serialization.SerializationException

/**
 * The text the AppView records for operators about an error (dev/worker-telemetry): in worker status and in every stored failure (segment
 * jobs, space syncs, stream fetches, block backfills). Its type and the first line of its message, at most [MAX_LENGTH] characters. An
 * error from decoding a record, a blob or a frame is recorded by its type alone, since decoders quote the input they failed on
 * (kotlinx.serialization's "JSON input: …", Jackson's source location), and that input can be private content.
 */
object ErrorSummary {
    /** The longest recorded error text; the stored columns are this wide. */
    const val MAX_LENGTH = 500

    fun of(e: Throwable): String {
        val type = e::class.simpleName ?: e::class.java.name
        val text =
            when {
                e.causes().any { it.quotesInput() } -> type
                e is XrpcException -> "$type: ${e.error}" + e.message?.firstLine()?.let { ": $it" }.orEmpty()
                else -> e.message?.firstLine()?.takeIf { it.isNotBlank() }?.let { "$type: $it" } ?: type
            }
        return text.take(MAX_LENGTH)
    }

    /** [text] as recorded error text: its first line, at most [MAX_LENGTH] characters, for reasons the AppView words itself. */
    fun text(text: String): String = text.firstLine().take(MAX_LENGTH)

    private fun Throwable.quotesInput(): Boolean = this is SerializationException || this is JacksonException

    private fun Throwable.causes(): Sequence<Throwable> =
        generateSequence(this) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSES)

    private fun String.firstLine(): String = lineSequence().firstOrNull().orEmpty().trim()

    private const val MAX_CAUSES = 16
}
