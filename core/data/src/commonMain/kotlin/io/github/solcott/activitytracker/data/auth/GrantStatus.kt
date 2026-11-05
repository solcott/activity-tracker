package io.github.solcott.activitytracker.data.auth

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AthleteSpaceDeclaration
import io.github.solcott.activitytracker.lexicon.Nsid

/** Whether the session's sign-in grants everything the app asks for now (auth/account-page). */
@Immutable
sealed interface GrantStatus {
    /** The grant covers every collection the athlete space declares, on a PDS not known to lack Spaces. */
    data object Current : GrantStatus

    /** The PDS can't host spaces, so signing in again wouldn't help. */
    data object SpacesUnsupported : GrantStatus

    /** The sign-in predates part of what the app asks for: signing in again picks up [missing]. */
    data class Outdated(val missing: Set<GrantFeature>) : GrantStatus
}

/** What a missing part of the grant stops the athlete doing, grouped as they'd recognise it rather than by collection. */
enum class GrantFeature {
    /** Followers, Mutuals and Only me activities, their detail categories, tracks, originals, devices, invites and defaults. */
    PrivateActivities,

    /** Privacy zones and the route trimming that follows them. */
    PrivacyZones,

    /** Profile details that aren't public, and declined follow requests. */
    PrivateProfile,

    /** Likes, comments and comment hides on activities that aren't public. */
    PrivateInteractions,

    /** Blocks and mutes. */
    BlocksAndMutes,

    /** Gear that isn't public, gear defaults and collections. */
    Gear,

    /** Segment matching, leaderboards, brackets, grouping, stars and name votes. */
    SegmentsAndGroups;

    companion object {
        /** The feature each collection the athlete space declares belongs to; `GrantStatusTest` checks it covers every one. */
        val byCollection: Map<String, GrantFeature> = buildMap {
            listOf(
                    Nsid.FEED_ACTIVITY,
                    Nsid.FEED_ACTIVITY_DETAILS,
                    Nsid.FEED_ACTIVITY_STREAMS,
                    Nsid.FEED_ACTIVITY_ZONES,
                    Nsid.FEED_ACTIVITY_TRACK,
                    Nsid.FEED_ACTIVITY_SOURCE,
                    Nsid.FEED_ACTIVITY_DEVICES,
                    Nsid.FEED_ACTIVITY_INVITE,
                    Nsid.FEED_ACTIVITY_INVITE_DECLINE,
                    Nsid.ACTOR_ACTIVITY_DEFAULTS,
                )
                .forEach { put(it, PrivateActivities) }
            listOf(Nsid.ACTOR_PRIVACY_ZONES, Nsid.ACTOR_ROUTE_TRIM).forEach { put(it, PrivacyZones) }
            listOf(Nsid.ACTOR_PRIVATE_DETAILS, Nsid.GRAPH_FOLLOW_DECLINE).forEach { put(it, PrivateProfile) }
            listOf(Nsid.FEED_LIKE, Nsid.FEED_COMMENT, Nsid.FEED_COMMENT_HIDE).forEach { put(it, PrivateInteractions) }
            listOf(Nsid.GRAPH_BLOCK, Nsid.GRAPH_MUTE).forEach { put(it, BlocksAndMutes) }
            listOf(Nsid.GEAR_ITEM, Nsid.GEAR_AUTO_ASSIGN, Nsid.GEAR_COLLECTION).forEach { put(it, Gear) }
            listOf(
                    Nsid.ACTOR_SEGMENT_PREFS,
                    Nsid.ACTOR_LEADERBOARD_BRACKETS,
                    Nsid.ACTOR_GROUP_PREFS,
                    Nsid.FEED_SEGMENT_TRACK,
                    Nsid.FEED_SEGMENT_STAR,
                    Nsid.FEED_SEGMENT_NAME_VOTE,
                )
                .forEach { put(it, SegmentsAndGroups) }
        }

        /** Every feature, for a session with no athlete-space grant at all. */
        val all: Set<GrantFeature> = entries.toSet()

        /** The features [collections] belong to. A collection with no feature counts as private activities, the widest loss. */
        fun of(collections: Iterable<String>): Set<GrantFeature> =
            collections.mapTo(mutableSetOf()) { byCollection[it] ?: PrivateActivities }
    }
}

/**
 * The grant status of a session with [hasSpacesScope] on a PDS whose Spaces support is [unsupported] or not, where [canWrite] says whether
 * the grant covers a collection. A PDS without Spaces wins: signing in again can't fix it. Kept apart from `SessionClients` so it can be
 * tested without a session.
 */
internal fun grantStatusOf(hasSpacesScope: Boolean, unsupported: Boolean, canWrite: (String) -> Boolean): GrantStatus =
    when {
        unsupported -> GrantStatus.SpacesUnsupported
        !hasSpacesScope -> GrantStatus.Outdated(GrantFeature.all)
        else -> {
            val missing = AthleteSpaceDeclaration.collections.filterNot(canWrite)
            if (missing.isEmpty()) GrantStatus.Current else GrantStatus.Outdated(GrantFeature.of(missing))
        }
    }
