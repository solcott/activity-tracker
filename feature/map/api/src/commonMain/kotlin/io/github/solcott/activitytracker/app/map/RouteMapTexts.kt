package io.github.solcott.activitytracker.app.map

import io.github.solcott.activitytracker.app.RouteMap
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText

/** What the enlarged route map shows (feed/route-map-viewer, dev/shared-display-text); the Compose page and the native one ask it. */
object RouteMapTexts {
    /** The bar's title: the activity's title, else its type's name; empty until it loads. */
    fun title(content: RouteMapContent?): AppText =
        content?.let { it.title?.let(AppText::written) ?: LabelText.activityType(it.activityType) } ?: AppText.written("")

    /** The route a native map draws, as the detail page's map draws it; null with no route. */
    fun routeMap(content: RouteMapContent): RouteMap? = content.route?.let { RouteMap.of(it.polyline, it.gaps, it.ends) }
}
