package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable

/** By gear number, not teeth: devices report teeth from a gearing setup that is often a default rather than the bike's. */
@Immutable data class GearTimePreview(val gear: Int, val seconds: Long)

@Immutable data class DepthPreviewPoint(val seconds: Long, val depthMeters: Double)
