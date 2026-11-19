package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AdminPage
import io.github.solcott.activitytracker.appview.admin.HealthPage
import io.github.solcott.activitytracker.appview.admin.HealthSources
import io.github.solcott.activitytracker.appview.admin.JobQueuesPage
import io.github.solcott.activitytracker.appview.admin.SegmentsPage
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.segments.SegmentQueue
import org.jetbrains.exposed.v1.jdbc.Database

/** The admin site's operator pages `main` mounts, in navigation order (admin/admin-site). */
internal fun adminPages(config: AppViewConfig, db: Database, indexer: Indexer, health: HealthSources): List<AdminPage> =
    listOf(
        HealthPage(health),
        JobQueuesPage(SegmentQueue(db, indexer.segmentJobs)),
        SegmentsPage.of(config, db, indexer.segmentPins, indexer.segmentJobs),
    )
