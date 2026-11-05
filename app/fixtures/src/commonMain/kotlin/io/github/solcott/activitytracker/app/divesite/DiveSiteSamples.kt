package io.github.solcott.activitytracker.app.divesite

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSite
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSiteConditions
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSiteRecord
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeRegularEntry
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.data.divesites.RegularsMeta
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.siteOutline
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object DiveSiteSamples {
    val site: DiveSiteView = fakeDiveSite()

    val dives: ImmutableList<ActivityView> =
        persistentListOf(
            fakeActivity(
                "3kdive1",
                fakeProfileBasic(Fixed.BOB),
                fakeActivityRecord(Sport.DIVE, "Blue Hole wall", distance = null, polyline = null),
            ),
            fakeActivity("3kdive2", record = fakeActivityRecord(Sport.DIVE, "Arch swim-through", distance = null, polyline = null)),
        )

    private fun state(content: ContentState<DiveSiteView?>, site: DiveSiteView? = content.data) =
        DiveSiteState(
            content = content,
            outline = site?.record?.let { siteOutline(it.lat, it.lng, it.radius) },
            conditions = site?.let { fakeDiveSiteConditions() },
            regulars =
                site?.let {
                    RegularsSummary(
                        fakeRegularEntry(rank = 1, athlete = fakeProfileBasic(Fixed.BOB), visits = 31),
                        fakeRegularEntry(rank = 4, athlete = fakeProfileBasic(), visits = 6),
                    )
                },
            dives = if (site == null) PagedStates.loading() else PagedStates.end(dives, Unit),
        )

    val loading: DiveSiteState
        get() = DiveSiteState(content = ContentStates.loading(null))

    val failed: DiveSiteState
        get() = DiveSiteState(content = ContentStates.failed(null))

    val loaded: DiveSiteState
        get() = state(ContentStates.loaded(site))

    val refreshing: DiveSiteState
        get() = state(ContentStates.refreshing(site))

    val refreshFailed: DiveSiteState
        get() = state(ContentStates.refreshFailed(site))

    /** An OpenStreetMap site still proposed, with no conditions, no Regulars and no dives the viewer may see. */
    val proposed: DiveSiteState
        get() {
            val osm =
                fakeDiveSite(record = fakeDiveSiteRecord("SS Thistlegorm", source = SegmentSource.OSM, license = "ODbL-1.0", radius = 150))
            return DiveSiteState(
                content = ContentStates.loaded(osm.copy(status = SegmentStatus.PROPOSED)),
                outline = osm.record.let { siteOutline(it.lat, it.lng, it.radius) },
                dives = PagedStates.end(emptyList(), Unit),
            )
        }

    val all: Map<String, DiveSiteState>
        get() =
            mapOf(
                SampleKind.LOADING to loading,
                SampleKind.FAILED to failed,
                SampleKind.LOADED to loaded,
                SampleKind.REFRESHING to refreshing,
                SampleKind.REFRESH_FAILED to refreshFailed,
                PROPOSED to proposed,
            )

    const val PROPOSED = "proposed"
}

object DiveSiteRegularsSamples {
    val entries: ImmutableList<DiveSiteRegularEntry> =
        persistentListOf(
            fakeRegularEntry(rank = 1, athlete = fakeProfileBasic(Fixed.BOB), visits = 31),
            fakeRegularEntry(rank = 2, athlete = fakeProfileBasic(Fixed.CAROL), visits = 17),
        )
    private val meta = RegularsMeta(viewer = null)

    val all: Map<String, DiveSiteRegularsState>
        get() = pagedSamples(entries, meta) { DiveSiteRegularsState(DiveSiteSamples.site.currentName, entries = it) }
}

object CreateDiveSiteSamples {
    private fun loadedState(name: String = "Ras Mohammed") =
        CreateDiveSiteState(
            content = ContentStates.loaded(Unit),
            outline = DiveSiteSamples.site.record.let { siteOutline(it.lat, it.lng, it.radius) },
            name = TextFieldState(name),
            named = name.isNotBlank(),
        )

    val all: Map<String, CreateDiveSiteState>
        get() =
            mapOf(
                SampleKind.LOADING to CreateDiveSiteState(content = ContentStates.loading(Unit)),
                SampleKind.FAILED to
                    CreateDiveSiteState(content = ContentStates.failed(Unit), loadErrorKind = UiError.Kind.LoadDivePosition),
                SampleKind.LOADED to loadedState(),
                SampleKind.INVALID to loadedState(name = ""),
                INSIDE_ZONE to
                    CreateDiveSiteState(content = ContentStates.loaded(Unit), unavailable = CreateDiveSiteUnavailable.InsideZone),
                DUPLICATE to loadedState().copy(duplicates = persistentListOf(fakeDiveSite(name = "Ras Mohammed Shark Reef"))),
            )

    const val INSIDE_ZONE = "insideZone"
    const val DUPLICATE = "duplicate"
}
