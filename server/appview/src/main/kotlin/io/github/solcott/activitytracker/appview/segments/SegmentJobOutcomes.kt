package io.github.solcott.activitytracker.appview.segments

/** How a segment job attempt ended: done, failed and due again, or given up (kept in `segment_job_failures`). */
enum class JobOutcome(val tag: String) {
    DONE("done"),
    RETRY("retry"),
    DEAD("dead"),
}

/** Told how each segment job attempt ends, by kind; the metrics count them (dev/worker-telemetry). */
fun interface SegmentJobOutcomes {
    fun outcome(kind: String, outcome: JobOutcome)

    companion object {
        /** Counts nothing. */
        val None = SegmentJobOutcomes { _, _ -> }
    }
}
