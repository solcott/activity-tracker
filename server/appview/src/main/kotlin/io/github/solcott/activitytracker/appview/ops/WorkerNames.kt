package io.github.solcott.activitytracker.appview.ops

/** The AppView's background workers by the stable names the registry, the metrics and the admin pages key on (dev/worker-telemetry). */
object WorkerNames {
    const val JETSTREAM = "jetstream"
    const val SPACE_SYNCER = "space-syncer"
    const val SEGMENT_MATCHER = "segment-matcher"
    const val BSKY_BLOCK_BACKFILL = "bsky-block-backfill"
    const val HANDLE_SWEEP = "handle-sweep"
    const val SEGMENT_GROUPS_BACKFILL = "segment-groups-backfill"
    const val LABEL_SWEEP = "label-sweep"
    const val MODERATION_ACCESS_LOG = "moderation-access-log"

    /** One per configured labeler, named by its DID (a configured service, never an athlete's). */
    fun labelSubscriber(did: String) = "label-subscriber:$did"
}
