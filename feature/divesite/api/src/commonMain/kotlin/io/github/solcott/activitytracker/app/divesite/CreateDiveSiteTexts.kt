package io.github.solcott.activitytracker.app.divesite

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem

/** What the dive site form says (dive-sites/site-creation, dev/shared-display-text), chosen here for the shared and the native form. */
object CreateDiveSiteTexts {
    /** "Radius: 100 m", in meters or feet. */
    fun radius(meters: Int, units: UnitSystem): AppText =
        AppText.named("create_dive_site_radius", StatText.elevation(meters.toDouble(), units))

    /** Why no site can be made from the dive. */
    fun unavailable(reason: CreateDiveSiteUnavailable): AppText =
        AppText.named(
            when (reason) {
                CreateDiveSiteUnavailable.NoPosition -> "create_dive_site_no_position"
                CreateDiveSiteUnavailable.InsideZone -> "create_dive_site_inside_zone"
                CreateDiveSiteUnavailable.HiddenStart -> "create_dive_site_hidden_start"
                CreateDiveSiteUnavailable.AtSite -> "create_dive_site_at_site"
            },
        )

    /** What publishing makes public, then, when the site [namesDive], that it names the dive as its source: one paragraph. */
    fun publicNote(namesDive: Boolean): AppText {
        val public = AppText.named("create_dive_site_public")
        return AppText.sentences(listOfNotNull(public, AppText.named("create_dive_site_names_dive").takeIf { namesDive })) ?: public
    }
}
