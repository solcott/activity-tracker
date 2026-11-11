package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GearComponent
import io.github.solcott.activitytracker.lexicon.app.GearMountView
import io.github.solcott.activitytracker.lexicon.app.GearSportTotals
import io.github.solcott.activitytracker.lexicon.app.GearStarting
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.GearYearTotals
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.pagedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** What the gear item page says and decides, which both UIs ask (dev/shared-display-text, "The gear page reads the same in both UIs"). */
class GearPageTextsTest {
    private val metric = UnitSystem.Metric
    private val totals = GearTotals(activities = 2, movingTime = 7_200, distance = 64_800, elevationGain = 820, elevationLoss = 0, days = 2)
    private val bike = GearView(AtUri("at://did:plc:alice/app.activitytracker.gear.item/b"), "Gravel bike", GearType.BIKE, kind = "gravel")
    private val chain =
        GearView(AtUri("at://did:plc:alice/app.activitytracker.gear.item/c"), "Chain #3", GearType.COMPONENT, kind = "chain")
    private val page = GearPage(bike, GearUsage(totals, emptyList(), emptyList()), retired = false, isOwner = true)

    private fun joined(a: AppText, b: AppText) = AppText.named("gear_total_joined", a, b)

    private fun labels(figures: List<GearFigure>) = figures.map { it.label }

    private fun state(page: GearPage?, error: UiError? = null) =
        GearState(content = ContentState(page), activities = pagedStateOf(), error = error)

    @Test
    fun theKindLineIsTheKindOrTheType() {
        assertEquals(GearTexts.kind(GearType.BIKE, "gravel"), GearPageTexts.kindLine(bike))
        assertEquals(GearTexts.type(GearType.BIKE), GearPageTexts.kindLine(bike.copy(kind = null)))
        assertEquals(
            GearTexts.type(GearType.COMPONENT),
            GearPageTexts.candidateLine(GearMountCandidate("r", "Chain #5", GearType.COMPONENT)),
        )
    }

    @Test
    fun theMakerIsTheBrandAndModel() {
        assertNull(GearPageTexts.maker(bike))
        assertEquals(AppText.written("Rivendell Appaloosa"), GearPageTexts.maker(bike.copy(brand = "Rivendell", model = "Appaloosa")))
        assertEquals(AppText.written("Appaloosa"), GearPageTexts.maker(bike.copy(model = "Appaloosa")))
    }

    @Test
    fun theNotesAreWhoMaySeeItAndRetired() {
        assertNull(GearPageTexts.notes(page))
        assertEquals(LabelText.visibility(Visibility.PUBLIC), GearPageTexts.notes(page.copy(visibility = Visibility.PUBLIC)))
        assertEquals(
            joined(LabelText.visibility(Visibility.SELF), AppText.named("gear_retired")),
            GearPageTexts.notes(page.copy(visibility = Visibility.SELF, retired = true)),
        )
    }

    @Test
    fun aBikeShowsDistanceAndGainButNoVertical() {
        val figures = GearPageTexts.figures(page, metric)
        assertEquals(
            listOf("gear_page_activity_count", "gear_page_moving_time", "gear_page_distance", "gear_page_elevation_gain", "gear_page_days")
                .map(AppText::named),
            labels(figures),
        )
        assertEquals(GearTexts.distance(64_800, metric), figures[2].value)
    }

    @Test
    fun skisShowVerticalAndNoDistance() {
        val skis =
            page.copy(
                gear = bike.copy(type = GearType.SKI, kind = null),
                usage = GearUsage(totals.copy(distance = 0, elevationGain = 0, elevationLoss = 41_000), emptyList(), emptyList()),
            )
        val figures = GearPageTexts.figures(skis, metric)
        assertEquals(
            listOf("gear_page_activity_count", "gear_page_moving_time", "gear_page_elevation_loss", "gear_page_days").map(AppText::named),
            labels(figures),
        )
        assertEquals(StatText.elevation(41_000.0, metric), figures[2].value)
    }

    @Test
    fun aDiveItemShowsItsDivesAndBottomTime() {
        val dive =
            page.copy(
                gear = bike.copy(type = GearType.DIVE_GEAR, kind = null),
                usage = GearUsage(totals.copy(distance = 0, elevationGain = 0, dives = 3, bottomTime = 5_400), emptyList(), emptyList()),
            )
        val figures = GearPageTexts.figures(dive, metric)
        assertEquals(AppText.named("gear_page_dives"), figures[figures.size - 2].label)
        assertEquals(AppText.written("3"), figures[figures.size - 2].value)
        assertEquals(AppText.written(StatText.duration(5_400)), figures.last().value)
    }

    @Test
    fun startingUsageJoinsWhatItHas() {
        assertNull(GearPageTexts.starting(null, metric))
        assertNull(GearPageTexts.starting(GearStarting(distance = 0), metric))
        assertEquals(
            AppText.named("gear_page_starting", GearTexts.distance(2_000_000, metric)),
            GearPageTexts.starting(GearStarting(distance = 2_000_000), metric),
        )
        assertEquals(
            AppText.named("gear_page_starting", joined(GearTexts.distance(2_000_000, metric), GearTexts.hours(360_000))),
            GearPageTexts.starting(GearStarting(distance = 2_000_000, time = 360_000), metric),
        )
    }

    @Test
    fun aBreakdownShowsOnlyWithMoreThanOneRow() {
        val one = totals.copy(activities = 1, distance = 32_400)
        val single = page.copy(usage = GearUsage(totals, listOf(GearSportTotals("ride", one, "gravel")), listOf(GearYearTotals(2026, one))))
        assertTrue(GearPageTexts.bySport(single, metric).isEmpty())
        assertTrue(GearPageTexts.byYear(single, metric).isEmpty())
        val two =
            page.copy(
                usage =
                    GearUsage(
                        totals,
                        listOf(GearSportTotals("ride", one, "gravel"), GearSportTotals("ride", one, "road")),
                        listOf(GearYearTotals(2026, one), GearYearTotals(2025, one)),
                    ),
            )
        val count = AppText.Plural("gear_total_activities", 1, persistentListOf(AppText.written("1")))
        assertEquals(
            GearFigure(
                LabelText.activityType(io.github.solcott.activitytracker.lexicon.app.ActivityType("ride", "gravel")),
                joined(count, GearTexts.distance(32_400, metric)),
            ),
            GearPageTexts.bySport(two, metric).first(),
        )
        assertEquals(listOf(AppText.written("2026"), AppText.written("2025")), labels(GearPageTexts.byYear(two, metric)))
    }

    @Test
    fun aComponentReadsItsKindTotalAndDates() {
        val from = Instant.parse("2025-03-01T00:00:00Z")
        val to = Instant.parse("2026-07-01T00:00:00Z")
        val past = GearComponent(chain, from, totals, to = to)
        assertEquals(
            joined(GearTexts.kind(GearType.COMPONENT, "chain"), GearTexts.distance(64_800, metric)),
            GearPageTexts.componentLine(past, metric),
        )
        assertEquals(
            AppText.named("gear_component_between", AppText.written("2025-03-01"), AppText.written("2026-07-01")),
            GearPageTexts.componentDates(past, TimeZone.UTC),
        )
        // The day the viewer's zone puts it on: midnight UTC is still the evening before in New York.
        assertEquals(
            AppText.named("gear_component_since", AppText.written("2025-02-28")),
            GearPageTexts.componentDates(past.copy(to = null), TimeZone.of("America/New_York")),
        )
        assertEquals(
            AppText.named("gear_component_since", AppText.written("2025-03-01")),
            GearPageTexts.mountDates(GearMountView(bike, from), TimeZone.UTC),
        )
        assertEquals(AppText.named("gear_component_actions", AppText.written("Chain #3")), GearPageTexts.componentActions(past))
    }

    @Test
    fun thePickerIsTitledByWhatItReplaces() {
        assertEquals(AppText.named("gear_mount_pick_title"), GearPageTexts.pickerTitle(GearMountPicker(persistentListOf())))
        val replacing = GearComponent(chain, Instant.parse("2026-07-01T00:00:00Z"), totals)
        assertEquals(
            AppText.named("gear_mount_replace_title", AppText.written("Chain #3")),
            GearPageTexts.pickerTitle(GearMountPicker(persistentListOf(), replacing)),
        )
    }

    @Test
    fun theOwnersPageOffersEditCollectionsAndComponents() {
        val owner = state(page).copy(collections = persistentListOf(GearCollectionChoice("c", "Race kit", member = true)))
        assertEquals("Gravel bike", owner.title)
        assertTrue(owner.showsEdit)
        assertTrue(owner.showsCollections)
        assertTrue(owner.showsComponents)
        assertTrue(owner.managesComponents)
        assertFalse(owner.authorClickable)
        assertFalse(state(page).showsCollections)
        val follower = state(page.copy(isOwner = false)).copy(collections = owner.collections)
        assertFalse(follower.showsEdit)
        assertFalse(follower.showsCollections)
        assertFalse(follower.showsComponents)
        assertFalse(follower.managesComponents)
        assertTrue(follower.authorClickable)
        assertTrue(
            state(page.copy(isOwner = false, previous = persistentListOf(GearComponent(chain, Instant.DISTANT_PAST, totals))))
                .showsComponents,
        )
        val loading = state(null)
        assertEquals("", loading.title)
        assertFalse(loading.showsEdit)
        assertFalse(loading.showsComponents)
    }

    @Test
    fun mountingWaitsForASave() {
        assertTrue(state(page).mountEnabled)
        assertFalse(state(page).copy(mountSaving = true).mountEnabled)
    }

    @Test
    fun aMountErrorShowsByComponentsAndAnyOtherOverTheActivities() {
        val loop = UiError(UiError.Kind.MountLoop)
        assertEquals(loop, state(page, loop).componentsError)
        assertNull(state(page, loop).activitiesError)
        val like = UiError(UiError.Kind.SaveGearCollection)
        assertNull(state(page, like).componentsError)
        assertEquals(like, state(page, like).activitiesError)
    }

    @Test
    fun anUnmountStartsTheDayAfterTheMountAndShowsTodayOrThatDay() {
        val zone = TimeZone.UTC
        assertNull(state(page).unmountFirstDay(zone))
        val mounted = state(page).copy(unmounting = GearComponent(chain, Instant.parse("2026-07-01T09:00:00Z"), totals))
        assertEquals(LocalDate(2026, 7, 2), mounted.unmountFirstDay(zone))
        assertEquals(LocalDate(2026, 11, 11), mounted.unmountInitialDay(Instant.parse("2026-11-11T12:00:00Z"), zone))
        // Mounted today: the first day it can end is tomorrow, which shows.
        assertEquals(LocalDate(2026, 7, 2), mounted.unmountInitialDay(Instant.parse("2026-07-01T12:00:00Z"), zone))
    }
}
