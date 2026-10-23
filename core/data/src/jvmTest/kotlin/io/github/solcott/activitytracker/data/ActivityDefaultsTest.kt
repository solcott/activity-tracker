package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The per-category defaults (`feed/detail-visibility`: "Default visibility per category"), as `ActivityDefaults` reads and saves them. */
class ActivityDefaultsTest {
    private val pds = FakeAthletePds()

    private fun storedDefaults(json: String) {
        pds.stored.getOrPut(Visibility.SELF) { mutableMapOf() }["${Nsid.ACTOR_ACTIVITY_DEFAULTS}/self"] =
            AtJson.parseToJsonElement("""{"${'$'}type":"${Nsid.ACTOR_ACTIVITY_DEFAULTS}",$json}""").jsonObject
    }

    @Test
    fun neverSetStartsEverythingButGearPublic() = runTest {
        val choice = pds.session().defaults.load()
        assertEquals(Visibility.PUBLIC, choice.activity)
        assertEquals(DetailCategory.all.associateWith { Visibility.PUBLIC } + (DetailCategory.GEAR to Visibility.SELF), choice.tiers)
    }

    @Test
    fun onlyTheActivityDefaultSetStartsEveryCategoryButGearThere() = runTest {
        storedDefaults(""""visibility":"followers"""")
        assertEquals(
            DetailCategory.all.associateWith { Visibility.FOLLOWERS } + (DetailCategory.GEAR to Visibility.SELF),
            pds.session().defaults.load().tiers,
        )
    }

    @Test
    fun aGearDefaultSetToPublicStartsPublic() = runTest {
        storedDefaults(""""visibility":"public","details":{"gear":"public"}""")
        assertEquals(Visibility.PUBLIC, pds.session().defaults.load().tiers[DetailCategory.GEAR])
    }

    @Test
    fun anOldChartsDefaultIsIgnored() = runTest {
        storedDefaults(""""visibility":"public","chartsVisibility":"self"""")
        assertEquals(VisibilityChoice.PUBLIC, pds.session().defaults.load())
    }

    @Test
    fun aCategoryDefaultIsReadAndCappedAtTheActivity() = runTest {
        storedDefaults(""""visibility":"mutuals","details":{"calories":"public","heartRate":"self"}""")
        val tiers = pds.session().defaults.load().tiers
        assertEquals(Visibility.MUTUALS, tiers[DetailCategory.CALORIES], "capped")
        assertEquals(Visibility.SELF, tiers[DetailCategory.HEART_RATE])
        assertEquals(Visibility.MUTUALS, tiers[DetailCategory.POWER], "unset: the activity's")
    }

    @Test
    fun savingWritesEveryCategoryCappedAndNoChartsDefault() = runTest {
        val s = pds.session()
        val saved = s.defaults.save(VisibilityChoice(Visibility.MUTUALS, mapOf(DetailCategory.CALORIES to Visibility.PUBLIC)))
        assertEquals(Visibility.MUTUALS, saved.tiers[DetailCategory.CALORIES])
        val record = pds.stored.getValue(Visibility.SELF).getValue("${Nsid.ACTOR_ACTIVITY_DEFAULTS}/self")
        assertEquals(Visibility.MUTUALS, record.getValue("details").jsonObject.getValue("calories").jsonPrimitive.content)
        assertFalse("chartsVisibility" in record)
        assertEquals(saved, pds.session().defaults.load(), "another device reads the same")
    }

    @Test
    fun narrowingTheActivityDragsWiderCategoriesAndWideningLeavesThem() {
        val choice =
            VisibilityChoice(
                Visibility.PUBLIC,
                mapOf(DetailCategory.CALORIES to Visibility.PUBLIC, DetailCategory.POWER to Visibility.FOLLOWERS),
            )
        val narrowed = choice.withActivity(Visibility.MUTUALS)
        assertEquals(Visibility.MUTUALS, narrowed.tiers[DetailCategory.CALORIES])
        assertEquals(Visibility.MUTUALS, narrowed.tiers[DetailCategory.POWER])

        val widened = VisibilityChoice(Visibility.FOLLOWERS).withActivity(Visibility.PUBLIC)
        assertEquals(Visibility.FOLLOWERS, widened.tiers[DetailCategory.HEART_RATE])
        assertEquals(Visibility.PUBLIC, widened.activity)
    }
}
