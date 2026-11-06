package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.segments.SegmentNames
import kotlin.time.Clock

/** What the feed needs for posts (feed/posts): their votes, and blocking a reported author. */
internal fun namesOf(db: AppDatabase, clients: SessionClients, feed: FeedRepository) =
    SegmentNames(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), Clock.System, feed.localPosts)

internal fun blocksOf(db: AppDatabase, clients: SessionClients, feed: FeedRepository) =
    BlockRepository(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), feed, Clock.System)
