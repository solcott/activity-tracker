package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * The route an activity's map draws for the viewer: the owner's full track once it's read, else the polyline they were served. [gaps] are
 * where its line breaks; [ends] where its start and end are marked when not at the line's own ends, set only on the owner's track.
 */
@Immutable data class ShownRoute(val polyline: String, val gaps: ImmutableList<Int>?, val ends: RouteEnds?)
