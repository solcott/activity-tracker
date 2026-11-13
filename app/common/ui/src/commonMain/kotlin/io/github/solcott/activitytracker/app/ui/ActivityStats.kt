package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.text.CardStatsText
import io.github.solcott.activitytracker.app.text.LabeledText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The distance stat, or null for a sport without a rate whose distance is absent or zero, like a strength workout's 0 m. */
@Composable fun distanceStat(sport: String, meters: Double?): Stat? = CardStatsText.distance(sport, meters, LocalUnitSystem.current)?.stat()

/** These labelled figures as the shared UI's strings read them. */
@Composable fun List<LabeledText>.stats(): ImmutableList<Stat> = map { it.stat() }.toImmutableList()

/** This labelled figure as the shared UI's strings read it. */
@Composable fun LabeledText.stat(): Stat = Stat(label.string(), value.string())
