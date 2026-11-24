package io.github.solcott.activitytracker.appview

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Clock

// The UTC dates the AppView counts days by: report limits, board periods, rider windows and confirmation months.

/** [epochMs]'s date in UTC. */
internal fun utcDate(epochMs: Long): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC)

/** [this] clock's current date in UTC. */
internal fun Clock.todayUtc(): LocalDate = utcDate(now().toEpochMilliseconds())
