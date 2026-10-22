package io.github.solcott.activitytracker.data.segments

import androidx.compose.runtime.Immutable

/**
 * What a page of a segment's leaderboard or Mayors board carries besides its entries: the signed-in athlete's own entry ([viewer], null
 * when they aren't on the board), the board type served ([board], a `SegmentBoard` value, null when the segment has none to list) and the
 * types it lists, most efforts first ([boards]).
 */
@Immutable data class BoardMeta<out E>(val viewer: E?, val board: String? = null, val boards: List<String> = emptyList())
