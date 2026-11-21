package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.fakeTrailAttributes
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDog
import io.github.solcott.activitytracker.lexicon.app.TrailHorse
import io.github.solcott.activitytracker.lexicon.app.TrailLit
import io.github.solcott.activitytracker.lexicon.app.TrailPisteDifficulty
import io.github.solcott.activitytracker.lexicon.app.TrailPisteGrooming
import io.github.solcott.activitytracker.lexicon.app.TrailSacScale
import io.github.solcott.activitytracker.lexicon.app.TrailSeason
import io.github.solcott.activitytracker.lexicon.app.TrailSmoothness
import io.github.solcott.activitytracker.lexicon.app.TrailSurface
import io.github.solcott.activitytracker.lexicon.app.TrailSurfaces
import io.github.solcott.activitytracker.lexicon.app.TrailVisibility
import io.github.solcott.activitytracker.lexicon.app.TrailWheelchair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList

/**
 * What the Trail details section says (segments/trail-attributes): every value of every attribute has its string, the rows read as the
 * spec's examples, a segment page shows only the scales of its sport group, and the section renders with its website link.
 */
class TrailTextsTest {
    @Test
    fun everyValueHasItsString() {
        val every =
            TrailSurfaces.all.map { TrailAttributes(surfaces = listOf(TrailSurface(it, 50), TrailSurface(TrailSurfaces.DIRT, 50))) } +
                TrailSurfaces.all.map { TrailAttributes(surfaces = listOf(TrailSurface(it, 100))) } +
                TrailSmoothness.all.map { TrailAttributes(smoothness = it) } +
                TrailSacScale.all.map { TrailAttributes(sacScale = it) } +
                (0..TrailAttributes.MAX_MTB_SCALE).map { TrailAttributes(mtbScale = it, mtbScaleUphill = it.coerceAtMost(5)) } +
                (0..TrailAttributes.MAX_MTB_SCALE_IMBA).map { TrailAttributes(mtbScaleImba = it) } +
                TrailPisteDifficulty.all.map { TrailAttributes(pisteDifficulty = it) } +
                TrailPisteGrooming.all.map { TrailAttributes(pisteGrooming = it) } +
                (1..TrailAttributes.MAX_TRAIL_CLASS).map { TrailAttributes(trailClass = it) } +
                TrailVisibility.all.map { TrailAttributes(visibility = it) } +
                TrailLit.all.map { TrailAttributes(lit = it) } +
                TrailDog.all.map { TrailAttributes(dog = it) } +
                TrailHorse.all.map { TrailAttributes(horse = it) } +
                TrailWheelchair.all.map { TrailAttributes(wheelchair = it) } +
                TrailSeason.all.map { TrailAttributes(seasons = listOf(it)) } +
                TrailAttributes(seasons = TrailSeason.all) +
                TrailAttributes(seasonal = true) +
                fakeTrailAttributes().copy(widthCm = 53, gradePercent = 12)
        val texts =
            every.flatMap { trail -> TrailTexts.rows(trail, null) }.flatMap { listOf(it.label, it.value) } +
                TrailTexts.title +
                TrailTexts.groups(fakeTrailAttributes().copy(trailClass = 2, gradePercent = 6), null).map { it.title } +
                listOf(TrailTexts.toggleLabel(true), TrailTexts.toggleLabel(false))
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
        assertTrue(every.all { trail -> TrailTexts.rows(trail, null).isNotEmpty() }, "a value gave no row")
    }

    @Test
    fun aRideSegmentReadsTheSpecsExample() {
        val trail =
            TrailAttributes(
                surfaces = listOf(TrailSurface(TrailSurfaces.PAVED, 70), TrailSurface(TrailSurfaces.GRAVEL, 30)),
                mtbScale = 2,
                sacScale = TrailSacScale.HIKING,
                dog = TrailDog.LEASHED,
            )
        val rows = TrailTexts.rows(trail, SegmentSportGroup.RIDE)
        assertEquals(listOf("trail_detail_surface", "trail_detail_mtb", "trail_detail_dog"), rows.map { (it.label as AppText.Named).name })
        assertEquals(
            AppText.named(
                "segment_browse_details",
                AppText.named("trail_surface_share", AppText.written("70"), AppText.named("trail_surface_paved")),
                AppText.named("trail_surface_share", AppText.written("30"), AppText.named("trail_surface_gravel")),
            ),
            rows[0].value,
        )
        assertEquals(AppText.named("trail_mtb_2"), rows[1].value, "in words, not S2")
    }

    @Test
    fun aWidthReadsAsAKindOfTrail() {
        fun width(cm: Int, sport: String? = SegmentSportGroup.RIDE) =
            TrailTexts.rows(TrailAttributes(widthCm = cm), sport).singleOrNull()?.value
        assertEquals(AppText.named("trail_width_singletrack"), width(100), "the kind alone, no number")
        assertEquals(AppText.named("trail_width_singletrack"), width(TrailTexts.DOUBLETRACK_CM - 1))
        assertEquals(AppText.named("trail_width_doubletrack"), width(TrailTexts.DOUBLETRACK_CM))
        assertEquals(AppText.named("trail_width_road"), width(TrailTexts.ROAD_CM))
        assertEquals(AppText.named("trail_width_singletrack"), width(100, SegmentSportGroup.HIKE))
        assertEquals(AppText.named("trail_width_singletrack"), width(100, sport = null), "the Trail page")
        assertNull(width(400, SegmentSportGroup.ALPINE), "no width on a piste")
        assertNull(width(400, SegmentSportGroup.SKI))
    }

    @Test
    fun eachSportGroupShowsItsScales() {
        val trail =
            TrailAttributes(
                sacScale = TrailSacScale.ALPINE_HIKING,
                mtbScale = 3,
                mtbScaleImba = 2,
                mtbScaleUphill = 1,
                pisteDifficulty = "advanced",
                pisteGrooming = "classic",
                trailClass = 2,
            )
        fun scales(sport: String?) = TrailTexts.rows(trail, sport).map { (it.label as AppText.Named).name }
        assertEquals(listOf("trail_detail_hiking", "trail_detail_class"), scales(SegmentSportGroup.HIKE))
        assertEquals(listOf("trail_detail_hiking", "trail_detail_class"), scales(SegmentSportGroup.RUN))
        assertEquals(
            listOf("trail_detail_mtb", "trail_detail_mtb_uphill", "trail_detail_imba", "trail_detail_class"),
            scales(SegmentSportGroup.RIDE),
        )
        assertEquals(listOf("trail_detail_grooming", "trail_detail_class"), scales(SegmentSportGroup.SKI))
        assertEquals(listOf("trail_detail_piste", "trail_detail_grooming", "trail_detail_class"), scales(SegmentSportGroup.ALPINE))
        assertEquals(7, scales(null).size, "the Trail page shows every scale")
    }

    @Test
    fun seasonsReadDatesThenSeasonsThenSeasonal() {
        assertEquals(
            AppText.named(
                "trail_season_dates",
                AppText.named("zones_history_date_short", AppText.named("month_short_6"), AppText.written("15")),
                AppText.named("zones_history_date_short", AppText.named("month_short_10"), AppText.written("15")),
            ),
            TrailTexts.season(TrailAttributes(openFrom = "06-15", openUntil = "10-15", seasons = listOf("winter"))),
        )
        assertEquals(AppText.named("trail_season_only_winter"), TrailTexts.season(TrailAttributes(seasons = listOf("winter"))))
        assertEquals(AppText.named("trail_season_seasonal"), TrailTexts.season(TrailAttributes(seasonal = true, openFrom = "06-15")))
        assertNull(TrailTexts.season(TrailAttributes()))
        assertNull(TrailTexts.season(TrailAttributes(openFrom = "02-30", openUntil = "04-15")), "a day its month lacks")
    }

    @Test
    fun rowsAreGroupedIntoCards() {
        val trail =
            TrailAttributes(
                mtbScale = 2,
                surfaces = listOf(TrailSurface(TrailSurfaces.DIRT, 100)),
                widthCm = 100,
                dog = TrailDog.LEASHED,
            )
        val groups = TrailTexts.groups(trail, SegmentSportGroup.RIDE)
        assertEquals(
            listOf("trail_group_difficulty", "trail_group_surface", "trail_group_access"),
            groups.map { (it.title as AppText.Named).name },
        )
        assertEquals(listOf("trail_detail_mtb"), groups[0].rows.map { (it.label as AppText.Named).name })
        assertEquals(listOf("trail_detail_surface", "trail_detail_width"), groups[1].rows.map { (it.label as AppText.Named).name })
        assertEquals(listOf("trail_detail_dog"), groups[2].rows.map { (it.label as AppText.Named).name })
        assertEquals(
            TrailTexts.rows(trail, SegmentSportGroup.RIDE).toSet(),
            groups.flatMap { it.rows }.toSet(),
            "the cards hold the rows, no more and no fewer",
        )
    }

    @Test
    fun aCardWithNoRowsIsLeftOut() {
        val groups = TrailTexts.groups(TrailAttributes(dog = TrailDog.NO), SegmentSportGroup.RIDE)
        assertEquals(listOf("trail_group_access"), groups.map { (it.title as AppText.Named).name })
        assertTrue(TrailTexts.groups(null, null).isEmpty())
        assertTrue(TrailTexts.groups(TrailAttributes(mtbScale = 3), SegmentSportGroup.HIKE).isEmpty(), "a scale its sport hides")
    }

    @Test
    fun theFoldedLineTakesUpToThreeValues() {
        val trail =
            TrailAttributes(
                surfaces = listOf(TrailSurface(TrailSurfaces.DIRT, 100)),
                widthCm = 100,
                dog = TrailDog.LEASHED,
                openFrom = "06-15",
                openUntil = "10-15",
            )
        assertEquals(
            AppText.named(
                "segment_browse_details",
                AppText.named("segment_browse_details", AppText.named("trail_surface_only_dirt"), AppText.named("trail_width_singletrack")),
                AppText.named("trail_dog_leashed"),
            ),
            TrailTexts.summary(trail),
            "surface, width and dogs; the season doesn't fit",
        )
        assertEquals(AppText.named("trail_dog_no"), TrailTexts.summary(TrailAttributes(dog = TrailDog.NO, mtbScale = 2)))
        assertNull(TrailTexts.summary(TrailAttributes(mtbScale = 3)), "nothing for the line")
        assertNull(TrailTexts.summary(null))
    }

    @Test
    fun onlyWebLinksAreOpened() {
        assertNull(TrailTexts.website("javascript:alert(1)"))
        assertNull(TrailTexts.website("ftp://example.com"))
        assertNull(TrailTexts.website("https://example.org/a|b"))
        assertEquals("https://kingdomtrails.org", TrailTexts.website("https://kingdomtrails.org"))
        val rows = TrailTexts.rows(TrailAttributes(website = "javascript:alert(1)"), null)
        assertTrue(rows.isEmpty())
        assertTrue(TrailTexts.rows(null, null).isEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theSectionShowsItsRowsInCards() = runComposeUiTest {
        setContent { AppPreview { TrailDetailGroups(TrailTexts.groups(fakeTrailAttributes(), SegmentSportGroup.RIDE).toImmutableList()) } }
        onNodeWithText("Trail details").assertExists()
        onNodeWithText("Difficulty").assertExists()
        onNodeWithText("Surface & width").assertExists()
        onNodeWithText("Access & season").assertExists()
        onNodeWithText("70% paved · 30% gravel · Rough").assertExists()
        onNodeWithText("Dogs on leash").assertExists()
        onNodeWithText("Intermediate: roots, rocks and small steps").assertExists()
        onNodeWithText("Singletrack").assertExists()
        onNodeWithText("Open Jun 15 – Oct 15").assertExists()
        onNodeWithText("https://kingdomtrails.org").assertExists()
        onNodeWithText("Hiking (T1)").assertDoesNotExist()
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
