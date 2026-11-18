package io.github.solcott.activitytracker.app

import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.hasAnswer

/**
 * The viewer's own entry, which [viewerOf] reads from the page meta, when no loaded entry has the same [athleteOf]; null before the first
 * answer. A board pins it apart from its list (segments/segment-leaderboards, segments/segment-mayors, a dive site's Regulars).
 */
fun <E, M, K> PagedState<E, M>.viewerApart(viewerOf: (M) -> E?, athleteOf: (E) -> K): E? =
    meta?.let(viewerOf)?.takeIf { own -> hasAnswer && items.none { athleteOf(it) == athleteOf(own) } }
