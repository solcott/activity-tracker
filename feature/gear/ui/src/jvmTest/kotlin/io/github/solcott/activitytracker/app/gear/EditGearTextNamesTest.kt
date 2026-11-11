package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.GearType
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString

/**
 * Every string the gear and collection forms' shared text names exists (dev/shared-display-text), as `SharedTextNamesTest` holds for
 * `:app-common-api`.
 */
class EditGearTextNamesTest {
    @Test
    fun everyFormTextNamesAString() {
        val day = LocalDate(2026, 7, 1)
        val texts =
            listOf(true, false).flatMap { listOf(EditGearTexts.title(it), EditGearTexts.collectionTitle(it)) } +
                EditGearTexts.kind(GearType.BIKE, null) +
                EditGearTexts.sports(emptyList()) +
                EditGearTexts.defaultFor(emptyList()) +
                UnitSystem.entries.map(EditGearTexts::startingDistance) +
                listOf(null, GearPhoto.Picked(PickedPhoto(ByteString(1), "image/webp", 1, 1))).map(EditGearTexts::photoAction) +
                EditGearTexts.mountedOn(GearMountedOn("at://x", "Gravel bike", day)) +
                EditGearTexts.retiredOn(day)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
