package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.index.validPositionE7
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.ktor.http.HttpStatusCode

// The client errors every view answers with, so each one is the same status and name wherever it's thrown.

/** Refuses the request as `400 InvalidRequest`, [message] saying why. */
internal fun invalidRequest(message: String): Nothing = throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", message)

/** `400 NotFound`: the answer for a subject that doesn't exist and for one the viewer may not see alike, so the two can't be told apart. */
internal fun notFound(message: String): XrpcError = XrpcError(HttpStatusCode.BadRequest.value, "NotFound", message)

/**
 * Refuses a search box, in 1e-7 degrees, that's off the globe, upside down, or wider than [SearchSegments.MAX_SPAN_E7] on either side, as
 * `InvalidRequest`.
 */
internal fun requireSearchBox(minLat: Int, minLng: Int, maxLat: Int, maxLng: Int) {
    if (!validPositionE7(minLat, minLng) || !validPositionE7(maxLat, maxLng)) invalidRequest("The box is outside the globe")
    if (minLat > maxLat || minLng > maxLng) invalidRequest("The box's minimum is above its maximum")
    if (maxLat - minLat > SearchSegments.MAX_SPAN_E7 || maxLng - minLng > SearchSegments.MAX_SPAN_E7)
        invalidRequest("The box is wider than one degree")
}
