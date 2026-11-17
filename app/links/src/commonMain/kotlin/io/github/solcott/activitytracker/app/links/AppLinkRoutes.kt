package io.github.solcott.activitytracker.app.links

import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.CombinePickerScreen
import io.github.solcott.activitytracker.app.divesite.CreateDiveSiteScreen
import io.github.solcott.activitytracker.app.divesite.DiveSiteBrowseScreen
import io.github.solcott.activitytracker.app.divesite.DiveSiteRegularsScreen
import io.github.solcott.activitytracker.app.divesite.DiveSiteScreen
import io.github.solcott.activitytracker.app.edit.EditActivityScreen
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.feed.ImportBatchScreen
import io.github.solcott.activitytracker.app.gear.EditGearCollectionScreen
import io.github.solcott.activitytracker.app.gear.EditGearScreen
import io.github.solcott.activitytracker.app.gear.GearCollectionScreen
import io.github.solcott.activitytracker.app.gear.GearListScreen
import io.github.solcott.activitytracker.app.gear.GearScreen
import io.github.solcott.activitytracker.app.gear.IgnoredSensorsScreen
import io.github.solcott.activitytracker.app.invites.AcceptInviteScreen
import io.github.solcott.activitytracker.app.invites.ActivityInvitesScreen
import io.github.solcott.activitytracker.app.invites.AddPeopleScreen
import io.github.solcott.activitytracker.app.login.LoginScreen
import io.github.solcott.activitytracker.app.map.RouteMapScreen
import io.github.solcott.activitytracker.app.media.MediaGalleryScreen
import io.github.solcott.activitytracker.app.moderation.ModerationScreen
import io.github.solcott.activitytracker.app.post.PostPageScreen
import io.github.solcott.activitytracker.app.post.PostQuotesScreen
import io.github.solcott.activitytracker.app.profile.BlockListKind
import io.github.solcott.activitytracker.app.profile.BlockListScreen
import io.github.solcott.activitytracker.app.profile.EditProfileScreen
import io.github.solcott.activitytracker.app.profile.FollowListKind
import io.github.solcott.activitytracker.app.profile.FollowListScreen
import io.github.solcott.activitytracker.app.profile.FollowRequestsScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.search.AthleteSearchScreen
import io.github.solcott.activitytracker.app.segment.CreateSegmentScreen
import io.github.solcott.activitytracker.app.segment.PathScreen
import io.github.solcott.activitytracker.app.segment.SegmentBrowseScreen
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardScreen
import io.github.solcott.activitytracker.app.segment.SegmentMapScreen
import io.github.solcott.activitytracker.app.segment.SegmentMayorsScreen
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.segment.StarredSegmentsScreen
import io.github.solcott.activitytracker.app.settings.AccountScreen
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsScreen
import io.github.solcott.activitytracker.app.settings.LeaveScreen
import io.github.solcott.activitytracker.app.settings.LeftElsewhereScreen
import io.github.solcott.activitytracker.app.settings.PrivacyZonesScreen
import io.github.solcott.activitytracker.app.settings.SegmentsAndGroupsScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.SportZonesScreen
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.app.settings.ZonesScreen
import io.github.solcott.activitytracker.app.uploads.UploadsScreen
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.isDid
import kotlin.reflect.KClass

/**
 * The path of every screen a link can open, both ways (ui/deep-links): [plan] reads a link into the stack it builds, and [linkOf] writes a
 * screen's canonical link, which the web's address bar shows (ui/web-history). Paths are written with DIDs and read with DIDs or handles.
 * Arguments that only say how a page was reached (a name to show before it loads, `suggestOnOpen`) are never in a link.
 */
// One function per part of the grammar, so each reads like the paths it matches.
@Suppress("TooManyFunctions")
object AppLinkRoutes {
    /** The first segments that are links, which Android claims on the link host and nothing else. */
    val topLevelPrefixes: List<String> =
        listOf(
            PROFILE,
            SEGMENT,
            TRAIL,
            DIVE_SITE,
            SEARCH,
            STARRED_SEGMENTS,
            FOLLOW_REQUESTS,
            INVITES,
            EDIT_PROFILE,
            UPLOADS,
            RECORD,
            IMPORT,
            SETTINGS,
        )

    /**
     * Screens that are never on a tab's stack, so no link opens them: sign-in, leaving and the choice after leaving elsewhere, and the
     * browse lists Search shows inside itself, which `/search` covers.
     */
    val excluded: Set<KClass<out Screen>> =
        setOf(
            LoginScreen::class,
            LeaveScreen::class,
            LeftElsewhereScreen::class,
            SegmentBrowseScreen::class,
            DiveSiteBrowseScreen::class,
        )

    /**
     * What [link] opens for [viewer] (a DID), resolving a handle in it with [resolve] (null when it doesn't resolve); null when no page has
     * its path.
     */
    suspend fun plan(link: AppLink, viewer: String, resolve: suspend (String) -> String?): LinkPlan? {
        val segments = link.segments
        val query = link.query
        val rest = segments.drop(1)
        return when (segments.firstOrNull()) {
            null -> LinkPlan(LinkTab.Feed, emptyList())
            SEARCH -> LinkPlan(LinkTab.Search, emptyList()).takeIf { rest.isEmpty() }
            PROFILE -> profile(rest, viewer, resolve)
            SEGMENT -> place(rest, Nsid.FEED_SEGMENT, resolve) { uri, tail -> segment(uri, tail, query) }
            TRAIL -> place(rest, Nsid.FEED_PATH, resolve) { uri, tail -> listOf(PathScreen(uri, query[SPORT])).takeIf { tail.isEmpty() } }
            DIVE_SITE -> place(rest, Nsid.FEED_DIVE_SITE, resolve) { uri, tail -> diveSite(uri, tail) }
            STARRED_SEGMENTS -> you(StarredSegmentsScreen).takeIf { rest.isEmpty() }
            FOLLOW_REQUESTS -> you(FollowRequestsScreen).takeIf { rest.isEmpty() }
            INVITES -> invites(rest, resolve)
            EDIT_PROFILE -> you(EditProfileScreen).takeIf { rest.isEmpty() }
            UPLOADS -> here(UploadsScreen).takeIf { rest.isEmpty() }
            RECORD ->
                when (rest) {
                    emptyList<String>() -> here(RecordScreen)
                    listOf(MANUAL) -> here(ManualActivityScreen)
                    else -> null
                }
            IMPORT -> here(ImportActivityScreen(query[COMBINE_INTO])).takeIf { rest.isEmpty() }
            SETTINGS -> settings(rest, query)?.let { LinkPlan(LinkTab.You, it) }
            else -> null
        }
    }

    /** [screen]'s canonical link, or null for a screen no link opens ([excluded]) or one holding a URI of another collection. */
    @Suppress("CyclomaticComplexMethod")
    fun linkOf(screen: Screen): AppLink? =
        when (screen) {
            FeedScreen -> AppLink.of()
            AthleteSearchScreen -> AppLink.of(SEARCH)
            is ProfileScreen -> AppLink.of(PROFILE, screen.actor)
            is FollowListScreen -> AppLink.of(PROFILE, screen.actor, screen.kind.segment)
            is ActivityDetailScreen -> activity(screen.uri)
            is RouteMapScreen -> activity(screen.uri, MAP)
            is MediaGalleryScreen -> activity(screen.uri, MEDIA, screen.index.toString())
            is EditActivityScreen -> activity(screen.uri, EDIT)
            is AddPeopleScreen -> activity(screen.activity, PEOPLE)
            is CombinePickerScreen -> activity(screen.uri, COMBINE)
            is CreateSegmentScreen -> activity(screen.activityUri, NEW_SEGMENT)
            is CreateDiveSiteScreen -> activity(screen.diveUri, NEW_DIVE_SITE)
            is PostPageScreen -> owned(screen.uri, Nsid.FEED_POST, POST)
            is PostQuotesScreen -> owned(screen.uri, Nsid.FEED_POST, POST, QUOTES)
            is GearScreen -> owned(screen.uri, Nsid.GEAR_ITEM, GEAR)
            is ImportBatchScreen -> AppLink.of(PROFILE, screen.actor, BATCH, screen.batch)
            is SegmentScreen -> placed(screen.uri, Nsid.FEED_SEGMENT, SEGMENT, query = mapOf(BOARD to screen.board))
            is SegmentMapScreen -> placed(screen.uri, Nsid.FEED_SEGMENT, SEGMENT, MAP)
            is SegmentLeaderboardScreen -> placed(screen.uri, Nsid.FEED_SEGMENT, SEGMENT, LEADERBOARD, query = mapOf(BOARD to screen.board))
            is SegmentMayorsScreen -> placed(screen.uri, Nsid.FEED_SEGMENT, SEGMENT, MAYORS, query = mapOf(BOARD to screen.board))
            is PathScreen -> placed(screen.uri, Nsid.FEED_PATH, TRAIL, query = mapOf(SPORT to screen.sport))
            is DiveSiteScreen -> placed(screen.uri, Nsid.FEED_DIVE_SITE, DIVE_SITE)
            is DiveSiteRegularsScreen -> placed(screen.uri, Nsid.FEED_DIVE_SITE, DIVE_SITE, REGULARS)
            StarredSegmentsScreen -> AppLink.of(STARRED_SEGMENTS)
            FollowRequestsScreen -> AppLink.of(FOLLOW_REQUESTS)
            ActivityInvitesScreen -> AppLink.of(INVITES)
            is AcceptInviteScreen -> placed(screen.invite, Nsid.FEED_ACTIVITY_INVITE, INVITES)
            EditProfileScreen -> AppLink.of(EDIT_PROFILE)
            UploadsScreen -> AppLink.of(UPLOADS)
            RecordScreen -> AppLink.of(RECORD)
            ManualActivityScreen -> AppLink.of(RECORD, MANUAL)
            is ImportActivityScreen -> AppLink.of(IMPORT, query = mapOf(COMBINE_INTO to screen.combineInto))
            else -> settingsLink(screen)
        }

    private fun settingsLink(screen: Screen): AppLink? =
        when (screen) {
            SettingsScreen -> AppLink.of(SETTINGS)
            AccountScreen -> AppLink.of(SETTINGS, ACCOUNT)
            ActivityDefaultsScreen -> AppLink.of(SETTINGS, ACTIVITY_DEFAULTS)
            PrivacyZonesScreen -> AppLink.of(SETTINGS, PRIVACY_ZONES)
            SegmentsAndGroupsScreen -> AppLink.of(SETTINGS, SEGMENTS_AND_GROUPS)
            ModerationScreen -> AppLink.of(SETTINGS, MODERATION)
            is BlockListScreen -> AppLink.of(SETTINGS, screen.kind.segment)
            TrainingZonesScreen -> AppLink.of(SETTINGS, TRAINING_ZONES)
            ZonesScreen -> AppLink.of(SETTINGS, TRAINING_ZONES, DEFAULT)
            ThresholdHistoryScreen -> AppLink.of(SETTINGS, TRAINING_ZONES, HISTORY)
            is SportZonesScreen -> AppLink.of(SETTINGS, TRAINING_ZONES, screen.sport)
            GearListScreen -> AppLink.of(SETTINGS, GEAR)
            IgnoredSensorsScreen -> AppLink.of(SETTINGS, GEAR, IGNORED_SENSORS)
            is EditGearScreen ->
                screen.rkey?.let { AppLink.of(SETTINGS, GEAR, it, EDIT) }
                    ?: AppLink.of(SETTINGS, GEAR, NEW, query = mapOf(MOUNT_ON to screen.mountOn, REPLACING to screen.replacing))
            is GearCollectionScreen -> AppLink.of(SETTINGS, GEAR, COLLECTION, screen.rkey)
            is EditGearCollectionScreen ->
                screen.rkey?.let { AppLink.of(SETTINGS, GEAR, COLLECTION, it, EDIT) } ?: AppLink.of(SETTINGS, GEAR, COLLECTION, NEW)
            else -> null
        }

    @Suppress("ReturnCount") // A path that doesn't match returns null as soon as it shows.
    private suspend fun profile(rest: List<String>, viewer: String, resolve: suspend (String) -> String?): LinkPlan? {
        val actor = rest.firstOrNull() ?: return null
        val did = if (actor.isDid()) actor else resolve(actor)
        val own = did == viewer
        val tab = if (own) LinkTab.You else LinkTab.Current
        val profile = ProfileScreen(did ?: actor)
        val tail = rest.drop(1)
        FollowListKind.entries
            .firstOrNull { tail == listOf(it.segment) }
            ?.let { kind ->
                return LinkPlan(tab, listOfNotNull(profile.takeUnless { own }, FollowListScreen(did ?: actor, kind)))
            }
        // A record needs the athlete's DID: an unknown handle opens their profile, which says they weren't found.
        if (tail.isEmpty() || did == null) return LinkPlan(tab, listOfNotNull(profile.takeUnless { own }))
        val rkey = tail.getOrNull(1) ?: return null
        val after = tail.drop(2)
        val chain =
            when (tail.first()) {
                ACTIVITY -> activityChain(AtUri.of(Did(did), Nsid.FEED_ACTIVITY, rkey).value, after, own)
                POST -> postChain(AtUri.of(Did(did), Nsid.FEED_POST, rkey).value, after)
                GEAR -> listOf(GearScreen(AtUri.of(Did(did), Nsid.GEAR_ITEM, rkey).value)).takeIf { after.isEmpty() }
                BATCH -> listOf(ImportBatchScreen(did, rkey)).takeIf { after.isEmpty() }
                else -> null
            }
        return chain?.let { LinkPlan(LinkTab.Current, it) }
    }

    private fun activityChain(uri: String, tail: List<String>, own: Boolean): List<Screen>? {
        val activity = ActivityDetailScreen(uri)
        val page: Screen? =
            when (tail) {
                emptyList<String>() -> null
                listOf(MAP) -> RouteMapScreen(uri)
                listOf(NEW_SEGMENT) -> CreateSegmentScreen(uri)
                listOf(NEW_DIVE_SITE) -> CreateDiveSiteScreen(uri)
                // The owner's forms: anyone else gets the activity itself.
                listOf(EDIT) -> EditActivityScreen(uri).takeIf { own }
                listOf(PEOPLE) -> AddPeopleScreen(uri).takeIf { own }
                listOf(COMBINE) -> CombinePickerScreen(uri).takeIf { own }
                else ->
                    tail
                        .takeIf { it.size == 2 && it[0] == MEDIA }
                        ?.get(1)
                        ?.toIntOrNull()
                        ?.takeIf { it >= 0 }
                        ?.let { MediaGalleryScreen(uri, it) } ?: return null
            }
        return listOfNotNull(activity, page)
    }

    private fun postChain(uri: String, tail: List<String>): List<Screen>? =
        when (tail) {
            emptyList<String>() -> listOf(PostPageScreen(uri))
            listOf(QUOTES) -> listOf(PostPageScreen(uri), PostQuotesScreen(uri))
            else -> null
        }

    /**
     * A place keyed `/{did}/{rkey}/…` in [collection]: [chain] given its URI and what follows, or the profile of a handle that won't
     * resolve.
     */
    @Suppress("ReturnCount") // A path that doesn't match returns null as soon as it shows.
    private suspend fun place(
        rest: List<String>,
        collection: String,
        resolve: suspend (String) -> String?,
        chain: (uri: String, tail: List<String>) -> List<Screen>?,
    ): LinkPlan? {
        if (rest.size < 2) return null
        val actor = rest[0]
        val did = (if (actor.isDid()) actor else resolve(actor)) ?: return LinkPlan(LinkTab.Current, listOf(ProfileScreen(actor)))
        return chain(AtUri.of(Did(did), collection, rest[1]).value, rest.drop(2))?.let { LinkPlan(LinkTab.Current, it) }
    }

    private fun segment(uri: String, tail: List<String>, query: Map<String, String>): List<Screen>? {
        val board = query[BOARD]
        return when (tail) {
            emptyList<String>() -> listOf(SegmentScreen(uri, board))
            listOf(MAP) -> listOf(SegmentScreen(uri), SegmentMapScreen(uri))
            listOf(LEADERBOARD) -> listOf(SegmentScreen(uri, board), SegmentLeaderboardScreen(uri, board = board))
            listOf(MAYORS) -> listOf(SegmentScreen(uri, board), SegmentMayorsScreen(uri, board = board))
            else -> null
        }
    }

    private fun diveSite(uri: String, tail: List<String>): List<Screen>? =
        when (tail) {
            emptyList<String>() -> listOf(DiveSiteScreen(uri))
            listOf(REGULARS) -> listOf(DiveSiteScreen(uri), DiveSiteRegularsScreen(uri))
            else -> null
        }

    private suspend fun invites(rest: List<String>, resolve: suspend (String) -> String?): LinkPlan? =
        when (rest.size) {
            0 -> you(ActivityInvitesScreen)
            2 -> {
                val did = if (rest[0].isDid()) rest[0] else resolve(rest[0])
                did?.let {
                    LinkPlan(
                        LinkTab.You,
                        listOf(ActivityInvitesScreen, AcceptInviteScreen(AtUri.of(Did(it), Nsid.FEED_ACTIVITY_INVITE, rest[1]).value)),
                    )
                } ?: you(ActivityInvitesScreen)
            }
            else -> null
        }

    // One branch per settings page; a path that doesn't match returns null as soon as it shows.
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun settings(rest: List<String>, query: Map<String, String>): List<Screen>? {
        val page: List<Screen>? =
            when (rest.firstOrNull()) {
                null -> emptyList()
                ACCOUNT -> listOf(AccountScreen)
                ACTIVITY_DEFAULTS -> listOf(ActivityDefaultsScreen)
                PRIVACY_ZONES -> listOf(PrivacyZonesScreen)
                SEGMENTS_AND_GROUPS -> listOf(SegmentsAndGroupsScreen)
                MODERATION -> listOf(ModerationScreen)
                BLOCKED,
                MUTED -> BlockListKind.entries.first { it.segment == rest[0] }.let { listOf(BlockListScreen(it)) }
                TRAINING_ZONES -> return trainingZones(rest.drop(1))
                GEAR -> return gear(rest.drop(1), query)
                else -> null
            }
        return page?.takeIf { rest.size <= 1 }?.let { listOf(SettingsScreen) + it }
    }

    private fun trainingZones(tail: List<String>): List<Screen>? {
        val base = listOf(SettingsScreen, TrainingZonesScreen)
        return when {
            tail.isEmpty() -> base
            tail.size > 1 -> null
            tail[0] == DEFAULT -> base + ZonesScreen
            tail[0] == HISTORY -> base + ThresholdHistoryScreen
            else -> base + SportZonesScreen(tail[0])
        }
    }

    private fun gear(tail: List<String>, query: Map<String, String>): List<Screen>? {
        val base = listOf(SettingsScreen, GearListScreen)
        return when {
            tail.isEmpty() -> base
            tail == listOf(IGNORED_SENSORS) -> base + IgnoredSensorsScreen
            tail == listOf(NEW) -> base + EditGearScreen(mountOn = query[MOUNT_ON], replacing = query[REPLACING])
            tail == listOf(COLLECTION, NEW) -> base + EditGearCollectionScreen()
            tail.size == 2 && tail[0] == COLLECTION -> base + GearCollectionScreen(tail[1])
            tail.firstOrNull() == COLLECTION && tail.drop(2) == listOf(EDIT) ->
                base + GearCollectionScreen(tail[1]) + EditGearCollectionScreen(tail[1])
            tail.size == 2 && tail[1] == EDIT -> base + EditGearScreen(tail[0])
            else -> null
        }
    }

    private fun you(screen: Screen) = LinkPlan(LinkTab.You, listOf(screen))

    private fun here(screen: Screen) = LinkPlan(LinkTab.Current, listOf(screen))

    private fun activity(uri: String, vararg tail: String): AppLink? = owned(uri, Nsid.FEED_ACTIVITY, ACTIVITY, *tail)

    /** `/profile/{did}/{kind}/{rkey}/…` for [uri], a record of [collection]. */
    @Suppress("ReturnCount") // A path that doesn't match returns null as soon as it shows.
    private fun owned(uri: String, collection: String, kind: String, vararg tail: String): AppLink? {
        val at = AtUri.parseOrNull(uri)?.takeIf { it.collection == collection } ?: return null
        val rkey = at.rkey ?: return null
        return AppLink.of(PROFILE, at.authority, kind, rkey, *tail)
    }

    /** `/{first}/{did}/{rkey}/…` for [uri], a record of [collection]. */
    @Suppress("ReturnCount") // A path that doesn't match returns null as soon as it shows.
    private fun placed(
        uri: String,
        collection: String,
        first: String,
        vararg tail: String,
        query: Map<String, String?> = emptyMap(),
    ): AppLink? {
        val at = AtUri.parseOrNull(uri)?.takeIf { it.collection == collection } ?: return null
        val rkey = at.rkey ?: return null
        return AppLink.of(first, at.authority, rkey, *tail, query = query)
    }

    private val FollowListKind.segment: String
        get() =
            when (this) {
                FollowListKind.Followers -> "followers"
                FollowListKind.Following -> "following"
            }

    private val BlockListKind.segment: String
        get() =
            when (this) {
                BlockListKind.Blocked -> BLOCKED
                BlockListKind.Muted -> MUTED
            }

    private const val PROFILE = "profile"
    private const val SEGMENT = "segment"
    private const val TRAIL = "trail"
    private const val DIVE_SITE = "dive-site"
    private const val SEARCH = "search"
    private const val STARRED_SEGMENTS = "starred-segments"
    private const val FOLLOW_REQUESTS = "follow-requests"
    private const val INVITES = "invites"
    private const val EDIT_PROFILE = "edit-profile"
    private const val UPLOADS = "uploads"
    private const val RECORD = "record"
    private const val MANUAL = "manual"
    private const val IMPORT = "import"
    private const val SETTINGS = "settings"
    private const val ACTIVITY = "activity"
    private const val POST = "post"
    private const val GEAR = "gear"
    private const val BATCH = "batch"
    private const val MAP = "map"
    private const val MEDIA = "media"
    private const val EDIT = "edit"
    private const val PEOPLE = "people"
    private const val COMBINE = "combine"
    private const val NEW_SEGMENT = "new-segment"
    private const val NEW_DIVE_SITE = "new-dive-site"
    private const val QUOTES = "quotes"
    private const val LEADERBOARD = "leaderboard"
    private const val MAYORS = "mayors"
    private const val REGULARS = "regulars"
    private const val ACCOUNT = "account"
    private const val ACTIVITY_DEFAULTS = "activity-defaults"
    private const val PRIVACY_ZONES = "privacy-zones"
    private const val SEGMENTS_AND_GROUPS = "segments-and-groups"
    private const val MODERATION = "moderation"
    private const val BLOCKED = "blocked"
    private const val MUTED = "muted"
    private const val TRAINING_ZONES = "training-zones"
    private const val DEFAULT = "default"
    private const val HISTORY = "history"
    private const val IGNORED_SENSORS = "ignored-sensors"
    private const val NEW = "new"
    private const val COLLECTION = "collection"
    private const val BOARD = "board"
    private const val SPORT = "sport"
    private const val COMBINE_INTO = "combineInto"
    private const val MOUNT_ON = "mountOn"
    private const val REPLACING = "replacing"
}
