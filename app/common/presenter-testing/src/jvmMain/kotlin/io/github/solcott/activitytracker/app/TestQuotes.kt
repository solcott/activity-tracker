package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.QuotePosts
import kotlin.time.Clock

/** Quote posts (feed/quote-posts) over the session's outbox, for every presenter that hosts the quote sheet. */
fun quotesOf(db: AppDatabase, clients: SessionClients, feed: FeedRepository) =
    QuotePosts(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), Clock.System, feed)
