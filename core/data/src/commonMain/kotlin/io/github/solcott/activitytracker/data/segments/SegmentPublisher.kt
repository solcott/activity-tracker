package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Clock

/**
 * Publishes the signed-in athlete's segments (segments/segment-creation): a public `feed.segment` in their repo, queued through the outbox
 * like any record. Callers check the line first ([SegmentRepository.check]); the AppView derives its status once it indexes it.
 */
@SingleIn(SessionScope::class)
@Inject
class SegmentPublisher(
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val segments: SegmentRepository,
    private val clock: Clock,
) {
    /**
     * Queues a segment named [name] of [sport] along [line] (precision 6), traced by the athlete from [activity], under [LICENSE], naming
     * [derivedFrom] only when given (the caller passes it only for an activity whose route is public), and sends it if it can. Returns its
     * URI.
     */
    suspend fun create(activity: AtUri, name: String, sport: String, line: String, derivedFrom: StrongRef? = null): AtUri {
        val rkey = Tid.next(clock)
        val record =
            SegmentRecord(
                name = name.trim(),
                sport = sport,
                line = line,
                source = SegmentSource.ATHLETE,
                license = LICENSE,
                createdAt = clock.now(),
                derivedFrom = derivedFrom,
            )
        writer.put(Nsid.FEED_SEGMENT, rkey, SegmentRecord.serializer(), record)
        segments.markCreated(activity)
        outbox.drain()
        return AtUri.of(clients.did, Nsid.FEED_SEGMENT, rkey)
    }

    companion object {
        /** The licence of segments athletes trace: public domain, so anyone may reuse the line. */
        const val LICENSE = "CC0-1.0"
    }
}
