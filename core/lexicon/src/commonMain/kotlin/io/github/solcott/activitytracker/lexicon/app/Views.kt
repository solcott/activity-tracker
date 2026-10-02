package io.github.solcott.activitytracker.lexicon.app

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** `app.activitytracker.actor.defs#profileViewBasic` */
@Serializable
data class ProfileViewBasic(
    val did: Did,
    val handle: Handle,
    val displayName: String? = null,
)

/** `app.activitytracker.actor.defs#profileView` */
@Serializable
data class ProfileView(
    val did: Did,
    val handle: Handle,
    val displayName: String? = null,
    val description: String? = null,
    val units: String? = null,
    val activitiesCount: Long? = null,
    val followersCount: Long? = null,
    val followsCount: Long? = null,
    val viewer: ProfileViewerState? = null,
)

@Serializable data class ProfileViewerState(val following: AtUri? = null)

/** `app.activitytracker.feed.defs#activityView` */
@Serializable
data class ActivityView(
    val uri: AtUri,
    val cid: Cid,
    val author: ProfileViewBasic,
    val record: ActivityRecord,
    val kudosCount: Long,
    val commentCount: Long,
    val indexedAt: Instant,
    val viewer: ActivityViewerState? = null,
)

@Serializable data class ActivityViewerState(val kudos: AtUri? = null)

/** `app.activitytracker.feed.defs#commentView` */
@Serializable
data class CommentView(
    val uri: AtUri,
    val cid: Cid,
    val author: ProfileViewBasic,
    val text: String,
    val createdAt: Instant,
)
