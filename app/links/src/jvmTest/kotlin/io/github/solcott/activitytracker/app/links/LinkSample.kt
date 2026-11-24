package io.github.solcott.activitytracker.app.links

import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.comments.CommentsScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.CombinePickerScreen
import io.github.solcott.activitytracker.app.divesite.CreateDiveSiteScreen
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
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardScreen
import io.github.solcott.activitytracker.app.segment.SegmentMapScreen
import io.github.solcott.activitytracker.app.segment.SegmentMayorsScreen
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.segment.StarredSegmentsScreen
import io.github.solcott.activitytracker.app.segment.TrailPhotosScreen
import io.github.solcott.activitytracker.app.segment.TrailReportsScreen
import io.github.solcott.activitytracker.app.settings.AccountScreen
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsScreen
import io.github.solcott.activitytracker.app.settings.PrivacyZonesScreen
import io.github.solcott.activitytracker.app.settings.SegmentsAndGroupsScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.SportZonesScreen
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.app.settings.ZonesScreen
import io.github.solcott.activitytracker.app.uploads.UploadsScreen
import io.github.solcott.activitytracker.lexicon.Nsid

/** One linkable screen as an app would hold it, the screen its link opens (no reach-only arguments), and the link's path. */
data class LinkSample(val screen: Screen, val path: String, val canonical: Screen = screen)

/** The viewer of every sample, and the handle that resolves to them. */
const val VIEWER = "did:plc:alice"
const val VIEWER_HANDLE = "alice.test"
const val BOB = "did:plc:bob"

private const val ACT = "at://$BOB/${Nsid.FEED_ACTIVITY}/3kact"
private const val OWN_ACT = "at://$VIEWER/${Nsid.FEED_ACTIVITY}/3kown"
private const val SEG = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kseg"
private const val SITE = "at://did:plc:carol/${Nsid.FEED_DIVE_SITE}/3ksite"

/** Every linkable screen, by class name; [LinkCoverageTest] fails for a contract screen in neither this nor [AppLinkRoutes.excluded]. */
val linkSamples: Map<String, LinkSample> =
    listOf(
            LinkSample(FeedScreen, "/"),
            LinkSample(AthleteSearchScreen, "/search"),
            LinkSample(ProfileScreen(BOB), "/profile/$BOB"),
            LinkSample(FollowListScreen(BOB, FollowListKind.Followers), "/profile/$BOB/followers"),
            LinkSample(ActivityDetailScreen(ACT, suggestOnOpen = true), "/profile/$BOB/activity/3kact", ActivityDetailScreen(ACT)),
            LinkSample(RouteMapScreen(ACT), "/profile/$BOB/activity/3kact/map"),
            LinkSample(CommentsScreen(ACT), "/profile/$BOB/activity/3kact/comments"),
            LinkSample(MediaGalleryScreen(ACT, 2), "/profile/$BOB/activity/3kact/media/2"),
            LinkSample(EditActivityScreen(OWN_ACT), "/profile/$VIEWER/activity/3kown/edit"),
            LinkSample(AddPeopleScreen(OWN_ACT), "/profile/$VIEWER/activity/3kown/people"),
            LinkSample(CombinePickerScreen(OWN_ACT), "/profile/$VIEWER/activity/3kown/combine"),
            LinkSample(CreateSegmentScreen(OWN_ACT), "/profile/$VIEWER/activity/3kown/new-segment"),
            LinkSample(CreateDiveSiteScreen(OWN_ACT), "/profile/$VIEWER/activity/3kown/new-dive-site"),
            LinkSample(PostPageScreen("at://$BOB/${Nsid.FEED_POST}/3kpost"), "/profile/$BOB/post/3kpost"),
            LinkSample(PostQuotesScreen("at://$BOB/${Nsid.FEED_POST}/3kpost"), "/profile/$BOB/post/3kpost/quotes"),
            LinkSample(CommentsScreen("at://$BOB/${Nsid.FEED_POST}/3kpost"), "/profile/$BOB/post/3kpost/comments"),
            LinkSample(GearScreen("at://$BOB/${Nsid.GEAR_ITEM}/3kgear"), "/profile/$BOB/gear/3kgear"),
            LinkSample(ImportBatchScreen(BOB, "3m5batch", "Bob"), "/profile/$BOB/batch/3m5batch", ImportBatchScreen(BOB, "3m5batch")),
            LinkSample(SegmentScreen(SEG, "run"), "/segment/did:plc:carol/3kseg?board=run"),
            LinkSample(SegmentMapScreen(SEG), "/segment/did:plc:carol/3kseg/map"),
            LinkSample(
                SegmentLeaderboardScreen(SEG, "Hill", "run"),
                "/segment/did:plc:carol/3kseg/leaderboard?board=run",
                SegmentLeaderboardScreen(SEG, board = "run"),
            ),
            LinkSample(SegmentMayorsScreen(SEG, "Hill"), "/segment/did:plc:carol/3kseg/mayors", SegmentMayorsScreen(SEG)),
            LinkSample(PathScreen("at://did:plc:imp/${Nsid.FEED_PATH}/3kpath", "ride"), "/trail/did:plc:imp/3kpath?sport=ride"),
            LinkSample(TrailReportsScreen(SEG, "Hill"), "/segment/did:plc:carol/3kseg/reports", TrailReportsScreen(SEG)),
            LinkSample(TrailPhotosScreen(segment = SEG, start = 3), "/segment/did:plc:carol/3kseg/photos/3"),
            LinkSample(DiveSiteScreen(SITE), "/dive-site/did:plc:carol/3ksite"),
            LinkSample(DiveSiteRegularsScreen(SITE, "Blue Hole"), "/dive-site/did:plc:carol/3ksite/regulars", DiveSiteRegularsScreen(SITE)),
            LinkSample(StarredSegmentsScreen, "/starred-segments"),
            LinkSample(FollowRequestsScreen, "/follow-requests"),
            LinkSample(ActivityInvitesScreen, "/invites"),
            LinkSample(AcceptInviteScreen("at://$BOB/${Nsid.FEED_ACTIVITY_INVITE}/3kinv"), "/invites/$BOB/3kinv"),
            LinkSample(EditProfileScreen, "/edit-profile"),
            LinkSample(UploadsScreen, "/uploads"),
            LinkSample(RecordScreen, "/record"),
            LinkSample(ManualActivityScreen, "/record/manual"),
            LinkSample(ImportActivityScreen(OWN_ACT), "/import?combineInto=${OWN_ACT.replace(":", "%3A").replace("/", "%2F")}"),
            LinkSample(SettingsScreen, "/settings"),
            LinkSample(AccountScreen, "/settings/account"),
            LinkSample(ActivityDefaultsScreen, "/settings/activity-defaults"),
            LinkSample(PrivacyZonesScreen, "/settings/privacy-zones"),
            LinkSample(SegmentsAndGroupsScreen, "/settings/segments-and-groups"),
            LinkSample(ModerationScreen, "/settings/moderation"),
            LinkSample(BlockListScreen(BlockListKind.Muted), "/settings/muted"),
            LinkSample(TrainingZonesScreen, "/settings/training-zones"),
            LinkSample(ZonesScreen, "/settings/training-zones/default"),
            LinkSample(ThresholdHistoryScreen, "/settings/training-zones/history"),
            LinkSample(SportZonesScreen("ride"), "/settings/training-zones/ride"),
            LinkSample(GearListScreen, "/settings/gear"),
            LinkSample(IgnoredSensorsScreen, "/settings/gear/ignored-sensors"),
            LinkSample(EditGearScreen("3kgear"), "/settings/gear/3kgear/edit"),
            LinkSample(GearCollectionScreen("3kcol"), "/settings/gear/collection/3kcol"),
            LinkSample(EditGearCollectionScreen(), "/settings/gear/collection/new"),
        )
        .associateBy { it.screen::class.simpleName!! }
