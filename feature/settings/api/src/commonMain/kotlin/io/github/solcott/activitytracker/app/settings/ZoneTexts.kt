package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone

/**
 * What a zone's row says that follows a rule, chosen here for the shared and the native UI (dev/shared-display-text).
 *
 * Their signatures are also what gives [Zone] and [PrivacyZone] a Swift type: each appears in the contracts only as an immutable list's
 * element, whose type Swift export erases, and a native view reads those lists' elements as these classes (dev/ios-swift-interop).
 */
object ZoneTexts {
    /** A computed zone's label: "Zone 2", "Zone 5a". */
    fun label(zone: Zone): AppText = AppText.named("zones_label", AppText.written(zone.label))

    /** A privacy zone's name: its label, or "Zone 2" for the second in the list when it has none. [index] is its place, from 0. */
    fun privacyZoneName(zone: PrivacyZone, index: Int): AppText =
        zone.label?.let(AppText::written) ?: AppText.named("privacy_zones_unnamed", AppText.written(formatWhole(index + 1)))
}
