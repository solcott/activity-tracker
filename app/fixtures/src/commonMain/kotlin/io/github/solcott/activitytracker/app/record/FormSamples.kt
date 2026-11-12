package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.profile.EditProfileSamples
import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.io.encoding.Base64
import kotlinx.collections.immutable.persistentListOf
import kotlinx.io.bytestring.ByteString

/** What the activity forms' samples share: the athlete's gear, and an activity's visibility with its categories. */
object FormSamples {
    private val tarmac = AtUri("at://${Fixed.ALICE.value}/${Nsid.GEAR_ITEM}/3ktarmac")
    private val pegasus = AtUri("at://${Fixed.ALICE.value}/${Nsid.GEAR_ITEM}/3kpegasus")
    private val allez = AtUri("at://${Fixed.ALICE.value}/${Nsid.GEAR_ITEM}/3kallez")

    /** Three items, one retired, the Tarmac chosen, and a collection to add. */
    val gear: GearField
        get() =
            GearField(
                options =
                    persistentListOf(
                        GearOption(tarmac, "Tarmac", GearType.BIKE, "road", Visibility.PUBLIC),
                        GearOption(pegasus, "Pegasus 41", GearType.SHOES, visibility = Visibility.FOLLOWERS),
                        GearOption(allez, "Allez", GearType.BIKE, "road", Visibility.SELF, retired = true),
                    ),
                selected = persistentListOf(tarmac),
                collections = persistentListOf(GearCollectionOption("race", "Race day", 3)),
            )

    /** A Followers activity whose heart rate is Only me. */
    val visibility: VisibilityFields
        get() =
            VisibilityFields(
                activity = Visibility.FOLLOWERS,
                categories =
                    persistentListOf(
                        CategoryVisibility(DetailCategory.START_TIME, Visibility.FOLLOWERS),
                        CategoryVisibility(DetailCategory.MAP, Visibility.FOLLOWERS),
                        CategoryVisibility(DetailCategory.HEART_RATE, Visibility.SELF),
                    ),
            )

    /** A photo picked on the form: a tiny PNG, as Kotlin's compression would hand it over. */
    val pickedPhoto: PickedPhoto
        get() = PickedPhoto(ByteString(Base64.decode(EditProfileSamples.PICKED_PNG)), "image/png", 8, 8)
}
