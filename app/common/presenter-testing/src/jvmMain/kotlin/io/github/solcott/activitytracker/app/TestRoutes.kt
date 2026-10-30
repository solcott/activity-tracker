package io.github.solcott.activitytracker.app

/** The route [ActivityTestBase]'s activity is served with, shared by the detail page's and the route map's tests. */
object TestRoutes {
    /** A trimmed polyline of three points, as the AppView serves it. */
    const val SERVED_POLYLINE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    /** [SERVED_POLYLINE] on the activity's record, broken after its second point. */
    const val SERVED_ROUTE = """"polyline":"$SERVED_POLYLINE","polylineGaps":[1],"""
}
