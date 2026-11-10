package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** What the gear list says and when it shows its collections, which both UIs ask (ui/settings-layout, "The Gear list is native on iOS"). */
class GearListTextsTest {
    private val bike = GearRow("3kgear1", "Gravel bike", GearType.BIKE, null, Visibility.PUBLIC, null)
    private val totals = GearTotals(activities = 1, movingTime = 0, distance = 3_200_000, elevationGain = 0, elevationLoss = 0, days = 1)

    private fun joined(a: AppText, b: AppText) = AppText.named("gear_total_joined", a, b)

    @Test
    fun theDetailIsTheKindOrTypeAndWhoMaySeeIt() {
        assertEquals(
            joined(GearTexts.type(GearType.BIKE), LabelText.visibility(Visibility.PUBLIC)),
            GearListTexts.detail(bike, showRetired = false),
        )
        assertEquals(
            joined(GearTexts.kind(GearType.BIKE, "gravel"), LabelText.visibility(Visibility.SELF)),
            GearListTexts.detail(bike.copy(kind = "gravel", visibility = Visibility.SELF), showRetired = false),
        )
    }

    @Test
    fun retiredShowsOnlyWhereAsked() {
        val retired = bike.copy(retired = true)
        val detail = joined(GearTexts.type(GearType.BIKE), LabelText.visibility(Visibility.PUBLIC))
        assertEquals(detail, GearListTexts.detail(retired, showRetired = false))
        assertEquals(joined(detail, AppText.named("gear_retired")), GearListTexts.detail(retired, showRetired = true))
        assertEquals(detail, GearListTexts.detail(bike, showRetired = true))
    }

    @Test
    fun theTotalWaitsForTheTotals() {
        assertNull(GearListTexts.total(bike, UnitSystem.Metric))
        assertEquals(
            GearTexts.mainTotal(bike.mainTotal, totals, UnitSystem.Imperial),
            GearListTexts.total(bike.copy(totals = totals), UnitSystem.Imperial),
        )
    }

    @Test
    fun aCollectionCountsItsItems() =
        assertEquals(
            AppText.Plural("gear_collection_count", 4, persistentListOf(AppText.written("4"))),
            GearListTexts.collectionCount(GearCollectionRow("c", "Race kit", 4)),
        )

    @Test
    fun collectionsShowOnceThereIsGearAndExplainThemselvesUntilOneExists() {
        val empty = GearLists()
        assertFalse(empty.showsCollections)
        assertFalse(empty.explainsCollections)
        val gear = GearLists(active = persistentListOf(GearGroup(GearType.BIKE, persistentListOf(bike))))
        assertTrue(gear.showsCollections)
        assertTrue(gear.explainsCollections)
        val collected = gear.copy(collections = persistentListOf(GearCollectionRow("c", "Race kit", 1)))
        assertTrue(collected.showsCollections)
        assertFalse(collected.explainsCollections)
    }
}
