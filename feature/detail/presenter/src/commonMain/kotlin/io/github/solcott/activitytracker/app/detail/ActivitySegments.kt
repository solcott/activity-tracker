package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewBasic
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Whether the viewer can cut a segment from [record]'s route: it's [own], its full track and their privacy zones were read, and its sport
 * has a group.
 */
internal fun canCreateSegment(own: Boolean, ownRoute: OwnRoute, record: ActivityRecord): Boolean =
    own && ownRoute.track != null && ownRoute.zonesRead && SegmentSport.of(record.sport, record.subSport) != null

/**
 * Whether the viewer can create a dive site from [record] (dive-sites/site-creation): it's [own], a dive outside a pool, its track has a
 * position and their privacy zones were read, and it visits no live site ([site], as `activityView.diveSite`). Whether its entry may be
 * public the create screen says.
 */
internal fun canCreateDiveSite(own: Boolean, ownRoute: OwnRoute, record: ActivityRecord, site: DiveSiteViewBasic?): Boolean =
    own &&
        record.sport == Sport.DIVE &&
        record.subSport != SubSport.POOL &&
        record.subSport != SubSport.POOL_APNEA &&
        !ownRoute.track.isNullOrEmpty() &&
        ownRoute.zonesRead &&
        site?.let { SegmentStatus.of(it.status) == SegmentStatus.PROPOSED || SegmentStatus.of(it.status) == SegmentStatus.ACTIVE } != true

/** An activity's efforts as its page shows them: runs grouped by overlapping spans, and lifts apart (segments/segment-lifts). */
internal data class ActivityEfforts(
    val runs: ImmutableList<SegmentEffortGroup> = persistentListOf(),
    val lifts: ImmutableList<LiftRides> = persistentListOf(),
    /** The lift rides themselves, to name the laps they match ([namedLiftLaps]). */
    val liftEfforts: ImmutableList<EffortView> = persistentListOf(),
)

/**
 * [activity]'s segment efforts, its runs grouped by overlapping spans ([groupEfforts]) and its lift rides counted per lift ([liftRidesOf]),
 * reloaded with [loadKey] and retained with the page, in their own load: they're extra, so a slow or failed load never holds up the page,
 * and a failure keeps what was shown, quietly: none on a first load, so no section.
 */
@Composable
internal fun rememberSegments(activity: AtUri, loadKey: Int, segments: SegmentRepository): ActivityEfforts =
    produceRetainedContentState(ActivityEfforts(), loadKey) {
            outcomeFlow {
                val (lifts, runs) = segments.activitySegments(activity).partition(::isLiftRide)
                ActivityEfforts(groupEfforts(runs), liftRidesOf(lifts), lifts.toImmutableList())
            }
        }
        .data
