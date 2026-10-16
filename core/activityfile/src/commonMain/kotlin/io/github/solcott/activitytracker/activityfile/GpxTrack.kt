package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.location.LocationSample

/**
 * The points of a GPX track, such as an activity's stored `feed.activityTrack`, in time order. Throws [ActivityFileException] if
 * unreadable.
 */
fun readGpxTrack(gpx: ByteArray): List<LocationSample> = DecodedActivityMapper.map(GpxParser.parse(gpx.decodeToString())).samples
