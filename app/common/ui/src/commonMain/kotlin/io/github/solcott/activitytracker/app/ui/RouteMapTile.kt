package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.ImmutableList

/**
 * A feed card's route, [height] tall: a map image drawn on this device, with the line preview while it's drawn and whenever it can't be,
 * and on web, which can't draw maps yet (feed/feed-route-map). Previews and tests draw the line preview.
 */
@Composable expect fun RouteMapTile(polyline: String, height: Dp, gaps: ImmutableList<Int>?, modifier: Modifier = Modifier)
