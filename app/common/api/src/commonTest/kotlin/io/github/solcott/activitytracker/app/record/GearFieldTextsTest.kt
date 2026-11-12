package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.app.gear.GearTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf

/** What the activity forms' gear picker says (gear/activity-gear, dev/shared-display-text). */
class GearFieldTextsTest {
    private val bike =
        GearOption(AtUri("at://did:plc:a/app.activitytracker.gear.item/bike"), "Canyon", "bike", "road", Visibility.FOLLOWERS)
    private val shoes =
        GearOption(AtUri("at://did:plc:a/app.activitytracker.gear.item/shoes"), "Pegasus", "shoes", visibility = Visibility.PUBLIC)

    @Test
    fun theSummaryNamesTheChosenItemsInOrderOrNone() {
        assertEquals(AppText.named("gear_field_none"), GearFieldTexts.summary(GearField(persistentListOf(bike, shoes))))
        assertEquals(
            AppText.written("Pegasus, Canyon"),
            GearFieldTexts.summary(GearField(persistentListOf(bike, shoes), selected = persistentListOf(shoes.uri, bike.uri))),
        )
    }

    @Test
    fun aLineNamesTheKindOrTypeAndWhoSeesIt() {
        assertEquals(
            AppText.named("page_gear_item_value", GearTexts.kind("bike", "road"), LabelText.visibility(Visibility.FOLLOWERS)),
            GearFieldTexts.line(bike),
        )
        assertEquals(
            AppText.named(
                "page_gear_item_value",
                AppText.named("page_gear_item_value", GearTexts.type("shoes"), LabelText.visibility(Visibility.PUBLIC)),
                AppText.named("gear_field_retired"),
            ),
            GearFieldTexts.line(shoes.copy(retired = true)),
        )
    }

    @Test
    fun theLeftOutCountShowsOnlyWhenItemsWereLeftOut() {
        assertNull(GearFieldTexts.notAdded(GearField()))
        assertEquals(
            AppText.Plural("gear_field_not_added", 2, persistentListOf(AppText.written("2"))),
            GearFieldTexts.notAdded(GearField(notAdded = 2)),
        )
    }
}
