package io.github.solcott.activitytracker.app.conformance

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.slack.circuit.runtime.CircuitUiState
import io.github.solcott.activitytracker.app.detail.ActivityDetailSamples
import io.github.solcott.activitytracker.app.detail.ActivityDetailUi
import io.github.solcott.activitytracker.app.detail.CombinePickerSamples
import io.github.solcott.activitytracker.app.detail.CombinePickerUi
import io.github.solcott.activitytracker.app.edit.EditActivitySamples
import io.github.solcott.activitytracker.app.edit.EditActivityUi
import io.github.solcott.activitytracker.app.feed.FeedSamples
import io.github.solcott.activitytracker.app.feed.FeedState
import io.github.solcott.activitytracker.app.feed.FeedUi
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.gear.EditGearCollectionSamples
import io.github.solcott.activitytracker.app.gear.EditGearCollectionUi
import io.github.solcott.activitytracker.app.gear.EditGearSamples
import io.github.solcott.activitytracker.app.gear.EditGearUi
import io.github.solcott.activitytracker.app.gear.GearCollectionSamples
import io.github.solcott.activitytracker.app.gear.GearCollectionUi
import io.github.solcott.activitytracker.app.gear.GearListSamples
import io.github.solcott.activitytracker.app.gear.GearListUi
import io.github.solcott.activitytracker.app.gear.GearSamples
import io.github.solcott.activitytracker.app.gear.GearUi
import io.github.solcott.activitytracker.app.gear.IgnoredSensorsSamples
import io.github.solcott.activitytracker.app.gear.IgnoredSensorsUi
import io.github.solcott.activitytracker.app.map.RouteMapSamples
import io.github.solcott.activitytracker.app.map.RouteMapUi
import io.github.solcott.activitytracker.app.media.MediaGallerySamples
import io.github.solcott.activitytracker.app.media.MediaGalleryUi
import io.github.solcott.activitytracker.app.profile.BlockListSamples
import io.github.solcott.activitytracker.app.profile.BlockListUi
import io.github.solcott.activitytracker.app.profile.EditProfileSamples
import io.github.solcott.activitytracker.app.profile.EditProfileUi
import io.github.solcott.activitytracker.app.profile.FollowListSamples
import io.github.solcott.activitytracker.app.profile.FollowListUi
import io.github.solcott.activitytracker.app.profile.FollowRequestsSamples
import io.github.solcott.activitytracker.app.profile.FollowRequestsUi
import io.github.solcott.activitytracker.app.profile.ProfileSamples
import io.github.solcott.activitytracker.app.profile.ProfileUi
import io.github.solcott.activitytracker.app.search.AthleteSearchSamples
import io.github.solcott.activitytracker.app.search.AthleteSearchUi
import io.github.solcott.activitytracker.app.segment.CreateSegmentSamples
import io.github.solcott.activitytracker.app.segment.CreateSegmentUi
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardSamples
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardUi
import io.github.solcott.activitytracker.app.segment.SegmentMapSamples
import io.github.solcott.activitytracker.app.segment.SegmentMapUi
import io.github.solcott.activitytracker.app.segment.SegmentMayorsSamples
import io.github.solcott.activitytracker.app.segment.SegmentMayorsUi
import io.github.solcott.activitytracker.app.segment.SegmentSamples
import io.github.solcott.activitytracker.app.segment.SegmentUi
import io.github.solcott.activitytracker.app.segment.StarredSegmentsSamples
import io.github.solcott.activitytracker.app.segment.StarredSegmentsUi
import io.github.solcott.activitytracker.app.settings.PrivacyZonesSamples
import io.github.solcott.activitytracker.app.settings.PrivacyZonesUi
import io.github.solcott.activitytracker.app.settings.SportZonesSamples
import io.github.solcott.activitytracker.app.settings.SportZonesUi
import io.github.solcott.activitytracker.app.settings.ThresholdHistorySamples
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryUi
import io.github.solcott.activitytracker.app.settings.TrainingZonesSamples
import io.github.solcott.activitytracker.app.settings.TrainingZonesUi
import io.github.solcott.activitytracker.app.settings.ZonesSamples
import io.github.solcott.activitytracker.app.settings.ZonesUi
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import java.io.File
import java.util.jar.JarFile
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every screen that loads content, rendered from its samples in the harness and checked against ui/content-loading through the shared
 * anchors ([TestTags]): progress before the first answer, empty shown as empty, retry on a first-load failure, content kept while
 * refreshing and after a failed refresh, and a paged list's footer. A screen whose `State` holds a `ContentState` or `PagedState` and isn't
 * in [screens] fails [everyContentLoadingScreenIsListed].
 */
@OptIn(ExperimentalTestApi::class)
class ContentLoadingConformanceTest {
    /** One screen: its samples, its UI, and text its loaded samples show. */
    private class Entry<S : Any>(
        val state: KClass<S>,
        val samples: () -> Map<String, S>,
        val shows: String?,
        val ui: @Composable (S) -> Unit,
    )

    private inline fun <reified S : Any> entry(
        noinline samples: () -> Map<String, S>,
        shows: String?,
        noinline ui: @Composable (S) -> Unit,
    ) = Entry(S::class, samples, shows, ui)

    private val screens: List<Entry<*>> =
        listOf(
            entry({ FeedSamples.all }, "Morning Ride") { FeedUi(it) },
            entry({ SegmentSamples.all }, "Ridge Trail") { SegmentUi(it) },
            entry({ SegmentMapSamples.all }, null) { SegmentMapUi(it) },
            entry({ SegmentLeaderboardSamples.all }, "Carol") { SegmentLeaderboardUi(it) },
            entry({ SegmentMayorsSamples.all }, "Bob") { SegmentMayorsUi(it) },
            entry({ StarredSegmentsSamples.all }, "Flagstaff Climb") { StarredSegmentsUi(it) },
            entry({ CreateSegmentSamples.all }, null) { CreateSegmentUi(it) },
            entry({ ActivityDetailSamples.all }, "Morning Ride") { ActivityDetailUi(it) },
            entry({ CombinePickerSamples.all }, "Ride (watch)") { CombinePickerUi(it) },
            entry({ RouteMapSamples.all }, null) { RouteMapUi(it) },
            entry({ MediaGallerySamples.all }, null) { MediaGalleryUi(it) },
            entry({ ProfileSamples.all }, "Trail runner and weekend rider.") { ProfileUi(it) },
            entry({ FollowRequestsSamples.all }, "Carol") { FollowRequestsUi(it) },
            entry({ FollowListSamples.all }, "Carol") { FollowListUi(it) },
            entry({ BlockListSamples.all }, "Carol") { BlockListUi(it) },
            entry({ AthleteSearchSamples.all }, "Carol") { AthleteSearchUi(it) },
            entry({ EditProfileSamples.all }, null) { EditProfileUi(it) },
            entry({ TrainingZonesSamples.all }, null) { TrainingZonesUi(it) },
            entry({ ThresholdHistorySamples.all }, null) { ThresholdHistoryUi(it) },
            entry({ ZonesSamples.all }, null) { ZonesUi(it) },
            entry({ SportZonesSamples.all }, null) { SportZonesUi(it) },
            entry({ PrivacyZonesSamples.all }, null) { PrivacyZonesUi(it) },
            entry({ EditActivitySamples.all }, null) { EditActivityUi(it) },
            entry({ GearListSamples.all }, "Gravel bike") { GearListUi(it) },
            entry({ GearSamples.all }, "Rivendell Appaloosa") { GearUi(it) },
            entry({ EditGearSamples.all }, null) { EditGearUi(it) },
            entry({ GearCollectionSamples.all }, "Cold water setup") { GearCollectionUi(it) },
            entry({ EditGearCollectionSamples.all }, null) { EditGearCollectionUi(it) },
            entry({ IgnoredSensorsSamples.all }, "Serial …2071") { IgnoredSensorsUi(it) },
        )

    @Test
    fun everyScreenMeetsContentLoading() {
        val failures = screens.flatMap { screen -> screen.check() }
        assertTrue(failures.isEmpty(), failures.joinToString("\n", prefix = "${failures.size} content-loading failures:\n"))
    }

    @Test
    fun everyContentLoadingScreenIsListed() {
        val listed = screens.map { it.state.java }.toSet()
        val missing = contentLoadingStates().filterNot { it in listed }.map { it.simpleName }.sorted()
        assertEquals(emptyList(), missing, "Content-loading screens without samples in ContentLoadingConformanceTest: $missing")
    }

    private fun <S : Any> Entry<S>.check(): List<String> =
        samples().mapNotNull { (kind, state) ->
            runCatching { render(state) { assertKind(kind, shows) } }
                .exceptionOrNull()
                ?.let { "${this.state.simpleName} $kind: ${it.message?.lines()?.first()}" }
        }

    private fun <S : Any> Entry<S>.render(state: S, checks: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { AppPreview { ui(state) } }
        checks()
    }

    private fun ComposeUiTest.assertKind(kind: String, shows: String?) {
        fun tag(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)
        fun content() = shows?.let {
            onNode(hasText(it, substring = true) or hasContentDescription(it, substring = true), useUnmergedTree = true).assertExists()
        }
        when (kind) {
            SampleKind.LOADING -> tag(TestTags.LOADING).assertExists()
            SampleKind.FAILED -> {
                tag(TestTags.LOAD_ERROR).assertExists()
                tag(TestTags.RETRY).assertExists()
            }
            SampleKind.EMPTY -> {
                tag(TestTags.LOADING).assertDoesNotExist()
                tag(TestTags.LOAD_ERROR).assertDoesNotExist()
            }
            SampleKind.LOADED -> {
                tag(TestTags.LOADING).assertDoesNotExist()
                tag(TestTags.LOAD_ERROR).assertDoesNotExist()
                content()
            }
            SampleKind.REFRESHING -> {
                tag(TestTags.LOADING).assertDoesNotExist()
                tag(TestTags.LOAD_ERROR).assertDoesNotExist()
                content()
            }
            SampleKind.REFRESH_FAILED -> {
                tag(TestTags.REFRESH_ERROR).assertExists()
                tag(TestTags.LOAD_ERROR).assertDoesNotExist()
                content()
            }
            SampleKind.LOADING_MORE -> tag(TestTags.PAGE_LOADING).assertExists()
            SampleKind.PAGE_FAILED -> {
                tag(TestTags.PAGE_ERROR).assertExists()
                tag(TestTags.PAGE_RETRY).assertExists()
            }
            SampleKind.END -> {
                tag(TestTags.LIST_END).assertExists()
                tag(TestTags.PAGE_LOADING).assertDoesNotExist()
            }
        }
    }

    /** Every `CircuitUiState` in `:app-screens` with a `ContentState` or `PagedState` field. */
    private fun contentLoadingStates(): List<Class<*>> {
        val root = File(FeedState::class.java.protectionDomain.codeSource.location.toURI())
        val names =
            if (root.isDirectory) root.walk().filter { it.isFile }.map { it.relativeTo(root).path }.toList()
            else JarFile(root).use { jar -> jar.entries().asSequence().map { it.name }.toList() }
        return names
            .filter { it.endsWith("State.class") && '$' !in it }
            .map { Class.forName(it.removeSuffix(".class").replace(File.separatorChar, '.').replace('/', '.')) }
            .filter { CircuitUiState::class.java.isAssignableFrom(it) && !it.isInterface }
            .filter { state -> state.declaredFields.any { it.type == ContentState::class.java || it.type == PagedState::class.java } }
            .ifEmpty { fail("Found no content-loading states in $root") }
    }
}
