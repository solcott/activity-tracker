package io.github.solcott.activitytracker.data.divesites

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Clock

/**
 * Publishes the signed-in athlete's dive sites (dive-sites/site-creation): a public `feed.diveSite` in their repo, queued through the
 * outbox like any record. Callers check the position first ([DiveSiteRepository.check]); the AppView derives its status once it indexes it.
 */
@SingleIn(SessionScope::class)
@Inject
class DiveSitePublisher(
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val sites: DiveSiteRepository,
    private val clock: Clock,
) {
    /**
     * Queues a site named [name] centred on ([lat], [lng]) in 1e-7 degrees with [radius] meters, placed from [dive], under [LICENSE],
     * naming [derivedFrom] only when given (the caller passes it only for a dive whose Map category is public), and sends it if it can.
     * Returns its URI.
     */
    @Suppress("LongParameterList") // The record's fields.
    suspend fun create(dive: AtUri, name: String, lat: Int, lng: Int, radius: Int, derivedFrom: StrongRef? = null): AtUri {
        val rkey = Tid.next(clock)
        val record =
            DiveSiteRecord(
                name = name.trim(),
                lat = lat,
                lng = lng,
                radius = radius.coerceIn(DiveSiteRecord.MIN_RADIUS, DiveSiteRecord.MAX_RADIUS),
                source = SegmentSource.ATHLETE,
                license = LICENSE,
                createdAt = clock.now(),
                derivedFrom = derivedFrom,
            )
        writer.put(Nsid.FEED_DIVE_SITE, rkey, DiveSiteRecord.serializer(), record)
        sites.markCreated(dive)
        outbox.drain()
        return AtUri.of(clients.did, Nsid.FEED_DIVE_SITE, rkey)
    }

    companion object {
        /** The licence of sites athletes place: public domain, so anyone may reuse the position. */
        const val LICENSE = "CC0-1.0"
    }
}
