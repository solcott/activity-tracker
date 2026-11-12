import ActivityTrackerCoreData
import ActivityTrackerCoreLexicon
import ActivityTrackerCoreLocation
import AppCommonApi
import ComSlackCircuitCircuitRuntime
import ComSlackCircuitCircuitxSubcircuit
import CoreTextfield
import FeatureDivesiteApi
import FeatureFeedApi
import FeatureFeedfilterApi
import FeatureGearApi
import FeatureInvitesApi
import FeatureLoginApi
import FeaturePostApi
import FeatureProfileApi
import FeatureReportApi
import FeatureSearchApi
import FeatureSegmentApi
import FeatureSettingsApi
import FeatureUploadsApi
import IoGithubSolcottDataresult
import IoGithubSolcottUistate
import OrgJetbrainsKotlinxKotlinxDatetime
import OrgJetbrainsKotlinxKotlinxIoBytestring
import PresenterHost

// Swift export gives a Kotlin module top-level names only when it is exported whole with a root package. The presenter host and Circuit
// reach Swift through other modules' signatures, so their types are spelt in full; these name the ones the shell uses.
typealias HostPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.host
typealias AppHost = HostPackage.AppHost
typealias SessionHost = HostPackage.SessionHost
typealias TabStack = HostPackage.TabStack
typealias HostRecord = HostPackage.HostRecord
typealias SettingsSplit = HostPackage.SettingsSplit
typealias RootState = HostPackage.RootState
typealias SessionMode = ExportedKotlinPackages.io.github.solcott.activitytracker.app.SessionMode
typealias TopLevelDestination = ExportedKotlinPackages.io.github.solcott.activitytracker.app.nav.TopLevelDestination
typealias CircuitUiState = ExportedKotlinPackages.com.slack.circuit.runtime.CircuitUiState

// The screen contracts are exported whole, but with no root package of their own, so their types are spelt in full too. A native screen
// adds the ones it draws from here; naming them is also what links their generated modules into the app (see `ScreenRegistry`).
typealias TextFieldPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.textfield
typealias KotlinTextFieldState = TextFieldPackage.TextFieldState
typealias FailureReason = ExportedKotlinPackages.io.github.solcott.activitytracker.app.FailureReason
typealias LoginPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.login
typealias LoginScreen = LoginPackage.LoginScreen
typealias LoginState = LoginPackage.LoginState
typealias LoginEvent = LoginPackage.LoginEvent
typealias AuthStatus = ExportedKotlinPackages.io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status

// Settings (ui/settings-layout): its list's contract, and the pages its rows open, which live in three contract modules.
typealias SettingsPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.settings
typealias SettingsScreen = SettingsPackage.SettingsScreen
typealias SettingsState = SettingsPackage.SettingsState
typealias SettingsEvent = SettingsPackage.SettingsEvent
typealias SettingsPage = SettingsPackage.SettingsPage
typealias SettingsPane = SettingsPackage.SettingsPane
typealias SettingsTexts = SettingsPackage.SettingsTexts
typealias ActivityDefaultsScreen = SettingsPackage.ActivityDefaultsScreen
typealias ActivityDefaultsState = SettingsPackage.ActivityDefaultsState
typealias SegmentsAndGroupsScreen = SettingsPackage.SegmentsAndGroupsScreen
typealias SegmentsAndGroupsState = SettingsPackage.SegmentsAndGroupsState
typealias SegmentsAndGroupsEvent = SettingsPackage.SegmentsAndGroupsEvent
typealias GroupOption = SettingsPackage.GroupOption
typealias GroupChoice = ExportedKotlinPackages.io.github.solcott.activitytracker.data.segments.GroupChoice
typealias AccountScreen = SettingsPackage.AccountScreen
typealias AccountState = SettingsPackage.AccountState
typealias AccountEvent = SettingsPackage.AccountEvent
typealias AccountTexts = SettingsPackage.AccountTexts
typealias DataExportStatus = SettingsPackage.DataExportStatus
typealias EndingSession = SettingsPackage.EndingSession
typealias LeaveScreen = SettingsPackage.LeaveScreen
typealias LeaveState = SettingsPackage.LeaveState
typealias LeaveEvent = SettingsPackage.LeaveEvent
typealias LeaveTexts = SettingsPackage.LeaveTexts
typealias LeaveRun = SettingsPackage.LeaveRun
typealias LeaveExport = SettingsPackage.LeaveExport
typealias SharedPlacesSummary = SettingsPackage.SharedPlacesSummary
typealias AuthPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.data.auth
typealias GrantStatus = AuthPackage.GrantStatus
typealias ExportArchive = ExportedKotlinPackages.io.github.solcott.activitytracker.data.export.ExportArchive

// Training zones, the default and sport zones forms and Privacy zones (ios-native-zones-pages), what they say and limit, and the
// training types they read; threshold history, still the shared UI's. `Zone` and `PrivacyZone` have Swift types because `ZoneTexts`'
// functions name them (dev/ios-swift-interop).
typealias TrainingZonesScreen = SettingsPackage.TrainingZonesScreen
typealias TrainingZonesState = SettingsPackage.TrainingZonesState
typealias TrainingZonesEvent = SettingsPackage.TrainingZonesEvent
typealias TrainingZones = SettingsPackage.TrainingZones
typealias ZoneSummary = SettingsPackage.ZoneSummary
typealias ZonesScreen = SettingsPackage.ZonesScreen
typealias ZonesState = SettingsPackage.ZonesState
typealias ZonesEvent = SettingsPackage.ZonesEvent
typealias SportZonesScreen = SettingsPackage.SportZonesScreen
typealias SportZonesState = SettingsPackage.SportZonesState
typealias SportZonesEvent = SettingsPackage.SportZonesEvent
typealias PrivacyZonesScreen = SettingsPackage.PrivacyZonesScreen
typealias PrivacyZonesState = SettingsPackage.PrivacyZonesState
typealias PrivacyZonesEvent = SettingsPackage.PrivacyZonesEvent
typealias ZoneKindOption = SettingsPackage.ZoneKindOption
typealias ZoneThreshold = SettingsPackage.ZoneThreshold
typealias ZoneThresholdLine = SettingsPackage.ZoneThresholdLine
typealias ZoneTexts = SettingsPackage.ZoneTexts
typealias TrainingZonesTexts = SettingsPackage.TrainingZonesTexts
typealias SportZonesTexts = SettingsPackage.SportZonesTexts
typealias PrivacyZonesTexts = SettingsPackage.PrivacyZonesTexts
typealias ThresholdHistoryState = SettingsPackage.ThresholdHistoryState
typealias ThresholdHistoryGroup = SettingsPackage.ThresholdHistoryGroup
typealias ThresholdHistorySection = SettingsPackage.ThresholdHistorySection
typealias TrainingPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.data.training
typealias ZoneKind = TrainingPackage.ZoneKind
typealias Zone = TrainingPackage.Zone
typealias ZoneModel = TrainingPackage.ZoneModel
typealias ZoneBasis = TrainingPackage.ZoneBasis
typealias PaceUnit = TrainingPackage.PaceUnit
typealias PrivacyZone = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.app.PrivacyZone

// Gear and the blocked and muted lists, Settings' pages in the gear and profile contract modules, the gear pages they open, and the
// follow lists and requests a profile opens.
typealias GearPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.gear
typealias GearListScreen = GearPackage.GearListScreen
typealias GearListState = GearPackage.GearListState
typealias GearListEvent = GearPackage.GearListEvent
typealias GearLists = GearPackage.GearLists
typealias GearGroup = GearPackage.GearGroup
typealias GearRow = GearPackage.GearRow
typealias GearCollectionRow = GearPackage.GearCollectionRow
typealias GearListTexts = GearPackage.GearListTexts
typealias GearTexts = GearPackage.GearTexts
typealias GearCollectionScreen = GearPackage.GearCollectionScreen
typealias GearCollectionState = GearPackage.GearCollectionState
typealias GearCollectionEvent = GearPackage.GearCollectionEvent
typealias GearCollectionDetail = GearPackage.GearCollectionDetail
typealias IgnoredSensorsScreen = GearPackage.IgnoredSensorsScreen
typealias IgnoredSensorsState = GearPackage.IgnoredSensorsState
typealias IgnoredSensorsEvent = GearPackage.IgnoredSensorsEvent
typealias LinkedSensor = GearPackage.LinkedSensor
typealias LinkedSensorTexts = GearPackage.LinkedSensorTexts
typealias ProfilePackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.profile
typealias BlockListScreen = ProfilePackage.BlockListScreen
typealias BlockListState = ProfilePackage.BlockListState
typealias BlockListEvent = ProfilePackage.BlockListEvent
typealias BlockListAccount = ProfilePackage.BlockListAccount
typealias BlockListKind = ProfilePackage.BlockListKind
typealias BlockListTexts = ProfilePackage.BlockListTexts
typealias FollowListScreen = ProfilePackage.FollowListScreen
typealias FollowListState = ProfilePackage.FollowListState
typealias FollowListEvent = ProfilePackage.FollowListEvent
typealias FollowListKind = ProfilePackage.FollowListKind
typealias FollowListTexts = ProfilePackage.FollowListTexts
typealias FollowRequestsScreen = ProfilePackage.FollowRequestsScreen
typealias FollowRequestsState = ProfilePackage.FollowRequestsState
typealias FollowRequestsEvent = ProfilePackage.FollowRequestsEvent
typealias FollowRequestsTab = ProfilePackage.FollowRequestsTab
typealias FollowRequestsTexts = ProfilePackage.FollowRequestsTexts
typealias ProfileViewBasic = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
typealias Did = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.Did

// Activity invites (feed/activity-invites): the invitee's list and the owner's Add people, and the lexicon views they list.
typealias InvitesPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.invites
typealias ActivityInvitesScreen = InvitesPackage.ActivityInvitesScreen
typealias ActivityInvitesState = InvitesPackage.ActivityInvitesState
typealias ActivityInvitesEvent = InvitesPackage.ActivityInvitesEvent
typealias ActivityInvitesTab = InvitesPackage.ActivityInvitesTab
typealias ActivityInvitesTexts = InvitesPackage.ActivityInvitesTexts
typealias AddPeopleScreen = InvitesPackage.AddPeopleScreen
typealias AddPeopleState = InvitesPackage.AddPeopleState
typealias AddPeopleEvent = InvitesPackage.AddPeopleEvent
typealias AddPeopleTexts = InvitesPackage.AddPeopleTexts
typealias InviteView = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.app.InviteView
typealias InviteeView = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.app.InviteeView

// Text, icons and loading rules chosen in Kotlin (dev/shared-display-text), and the types they take.
typealias AppPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app
typealias TextPackage = AppPackage.text
typealias AppText = TextPackage.AppText
typealias TextSource = TextPackage.TextSource
typealias StatText = TextPackage.StatText
typealias LabelText = TextPackage.LabelText
typealias ErrorTexts = TextPackage.ErrorTexts
typealias SportIconNames = AppPackage.icon.SportIconNames
typealias UiError = AppPackage.UiError
typealias VisibilityFields = AppPackage.visibility.VisibilityFields
typealias VisibilityEvent = AppPackage.visibility.VisibilityEvent
typealias CategoryVisibility = AppPackage.visibility.CategoryVisibility
typealias VisibilityTexts = AppPackage.visibility.VisibilityTexts
typealias ContentShown = AppPackage.ContentShown
typealias PagedShown = AppPackage.PagedShown
typealias SignInAgainNotice = AppPackage.SignInAgainNotice
typealias LoginTexts = LoginPackage.LoginTexts
typealias UnitSystem = ExportedKotlinPackages.io.github.solcott.activitytracker.data.units.UnitSystem
typealias KotlinActivityType = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.app.ActivityType
typealias KotlinContentState = ExportedKotlinPackages.io.github.solcott.uistate.ContentState
typealias KotlinPagedState = ExportedKotlinPackages.io.github.solcott.uistate.PagedState
typealias LoadStatus = ExportedKotlinPackages.io.github.solcott.uistate.LoadStatus
typealias DataError = ExportedKotlinPackages.io.github.solcott.dataresult.DataError

// The gear editors (ios-native-gear-editors): the item and collection forms, what they say and limit, the type sheets' groups, and the
// photo and date types their events carry.
typealias EditGearScreen = GearPackage.EditGearScreen
typealias EditGearState = GearPackage.EditGearState
typealias EditGearEvent = GearPackage.EditGearEvent
typealias EditGearCollectionScreen = GearPackage.EditGearCollectionScreen
typealias EditGearCollectionState = GearPackage.EditGearCollectionState
typealias EditGearCollectionEvent = GearPackage.EditGearCollectionEvent
typealias EditGearTexts = GearPackage.EditGearTexts
typealias EditGearLimits = GearPackage.EditGearLimits
typealias GearKindOption = GearPackage.GearKindOption
typealias GearPhoto = GearPackage.GearPhoto
typealias GearMountedOn = GearPackage.GearMountedOn
typealias GearCollectionChoice = GearPackage.GearCollectionChoice
typealias PickedPhoto = AppPackage.edit.PickedPhoto
typealias SportTypeGroups = AppPackage.sport.SportTypeGroups
typealias SportTypeGroup = AppPackage.sport.SportTypeGroup
typealias SportTypeRow = AppPackage.sport.SportTypeRow
typealias KotlinLocalDate = ExportedKotlinPackages.kotlinx.datetime.LocalDate
typealias KotlinByteString = ExportedKotlinPackages.kotlinx.io.bytestring.ByteString

// The upload queue (data/upload-queue): its contract, the queue model from `:core-data` it shows, the shared length of time and day
// words, and the Kotlin clock types a `ShownClock` gives them.
typealias UploadsPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.uploads
typealias UploadsScreen = UploadsPackage.UploadsScreen
typealias UploadsState = UploadsPackage.UploadsState
typealias UploadsEvent = UploadsPackage.UploadsEvent
typealias UploadsTexts = UploadsPackage.UploadsTexts
typealias UploadImportTexts = UploadsPackage.UploadImportTexts
typealias UploadActivityPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.data.activity
typealias UploadQueueView = UploadActivityPackage.UploadQueueView
typealias UploadImport = UploadActivityPackage.UploadImport
typealias UploadItem = UploadActivityPackage.UploadItem
typealias RejectedChange = UploadActivityPackage.RejectedChange
typealias ImportSaveStopped = UploadActivityPackage.ImportSaveStatus.Stopped
typealias DurationText = TextPackage.DurationText
typealias DayText = TextPackage.DayText
typealias KotlinInstant = ExportedKotlinPackages.kotlin.time.Instant
typealias KotlinTimeZone = ExportedKotlinPackages.kotlinx.datetime.TimeZone

// The segment lists (ios-native-segment-lists): Starred segments, a segment's leaderboard and Mayors, a dive site's Regulars, the lexicon
// entries they list, and the board choices and texts both UIs ask.
typealias SegmentPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.segment
typealias StarredSegmentsScreen = SegmentPackage.StarredSegmentsScreen
typealias StarredSegmentsState = SegmentPackage.StarredSegmentsState
typealias StarredSegmentsEvent = SegmentPackage.StarredSegmentsEvent
typealias StarredSegment = SegmentPackage.StarredSegment
typealias SegmentLeaderboardScreen = SegmentPackage.SegmentLeaderboardScreen
typealias SegmentLeaderboardState = SegmentPackage.SegmentLeaderboardState
typealias SegmentLeaderboardEvent = SegmentPackage.SegmentLeaderboardEvent
typealias SegmentMayorsScreen = SegmentPackage.SegmentMayorsScreen
typealias SegmentMayorsState = SegmentPackage.SegmentMayorsState
typealias SegmentMayorsEvent = SegmentPackage.SegmentMayorsEvent
typealias DiveSitePackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.divesite
typealias DiveSiteRegularsScreen = DiveSitePackage.DiveSiteRegularsScreen
typealias DiveSiteRegularsState = DiveSitePackage.DiveSiteRegularsState
typealias DiveSiteRegularsEvent = DiveSitePackage.DiveSiteRegularsEvent
typealias LexiconAppPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.app
typealias SegmentView = LexiconAppPackage.SegmentView
typealias LeaderboardEntry = LexiconAppPackage.LeaderboardEntry
typealias MayorEntry = LexiconAppPackage.MayorEntry
typealias DiveSiteRegularEntry = LexiconAppPackage.DiveSiteRegularEntry
typealias AtUri = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.AtUri
typealias SegmentBoardTexts = SegmentPackage.SegmentBoardTexts
typealias StarredSegmentsTexts = SegmentPackage.StarredSegmentsTexts
typealias DiveSiteRegularsTexts = DiveSitePackage.DiveSiteRegularsTexts
typealias BoardChoice = AppPackage.boards.BoardChoice
typealias BoardOption = AppPackage.boards.BoardOption
typealias RankBoardTexts = AppPackage.boards.RankBoardTexts
// Edit profile (profile/private-details): its contract, what it says, limits and offers, and the detail groups its sections are for.
typealias EditProfileScreen = ProfilePackage.EditProfileScreen
typealias EditProfileState = ProfilePackage.EditProfileState
typealias EditProfileEvent = ProfilePackage.EditProfileEvent
typealias EditProfileTexts = ProfilePackage.EditProfileTexts
typealias EditProfileLimits = ProfilePackage.EditProfileLimits
typealias AvatarImage = ProfilePackage.AvatarImage
typealias GenderChoices = ProfilePackage.GenderChoices
typealias CountrySearch = ProfilePackage.CountrySearch
typealias CountryName = ProfilePackage.CountryName
typealias DetailGroup = ExportedKotlinPackages.io.github.solcott.activitytracker.data.profile.DetailGroup

// The activity card (ui/ios-design-system) and the first page drawn with it, a batch import's (feed/import-cards): what a card shows,
// the lexicon view it shows, the route it places and the shared list events it sends.
typealias ActivityListPackage = AppPackage.activitylist
typealias ActivityCardContent = ActivityListPackage.ActivityCardContent
typealias ActivityListEvent = ActivityListPackage.ActivityListEvent
typealias LockLine = ActivityListPackage.LockLine
typealias NoticeText = AppPackage.moderation.NoticeText
typealias Revealed = AppPackage.moderation.Revealed
typealias LabeledText = TextPackage.LabeledText
typealias MediaPhoto = AppPackage.media.MediaItem.Photo
typealias RouteShape = AppPackage.RouteShape
typealias RoutePiece = AppPackage.RoutePiece
typealias RouteBreak = AppPackage.RouteBreak
typealias ShapePoint = AppPackage.ShapePoint
typealias ActivityView = LexiconAppPackage.ActivityView
typealias FeedPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.feed
typealias ImportBatchScreen = FeedPackage.ImportBatchScreen
typealias ImportBatchState = FeedPackage.ImportBatchState
typealias ImportBatchEvent = FeedPackage.ImportBatchEvent
typealias ImportBatchTexts = FeedPackage.ImportBatchTexts
typealias ImportBatchMonth = FeedPackage.ImportBatchMonth

// The presenter host's sub-screens (ios-host-subcircuits): a record's SubCircuit, its state and outer events, and the feed's filter bar,
// the first drawn from one (`FeedFilterBar`).
typealias HostSubRecord = HostPackage.HostSubRecord
typealias SubCircuitUiState = ExportedKotlinPackages.com.slack.circuit.subcircuit.SubCircuitUiState
typealias SubCircuitOuterEvent = ExportedKotlinPackages.com.slack.circuit.subcircuit.SubCircuitOuterEvent
typealias FeedFilterPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.feedfilter
typealias FeedFilterScreen = FeedFilterPackage.FeedFilterScreen
typealias FeedFilterState = FeedFilterPackage.FeedFilterState
typealias FeedFilterEvent = FeedFilterPackage.FeedFilterEvent
typealias FeedFilterOuterEvent = FeedFilterPackage.FeedFilterOuterEvent
typealias FeedFilter = FeedFilterPackage.FeedFilter
typealias FeedFilterTexts = FeedFilterPackage.FeedFilterTexts
typealias DateRange = FeedFilterPackage.DateRange
typealias DateRangeChoice = FeedFilterPackage.DateRangeChoice
typealias TypePickerState = FeedFilterPackage.TypePickerState
typealias CategorySelection = FeedFilterPackage.CategorySelection
typealias CustomRangeState = FeedFilterPackage.CustomRangeState
typealias FeedFilterSelection = FeedFilterPackage.Selection
typealias SportCategory = ExportedKotlinPackages.io.github.solcott.activitytracker.data.sport.SportCategory

// The gear item page (gear/gear-usage, gear/gear-components): its contract, what it says, and the lexicon views it shows. The lexicon's
// `GearView` is `LexiconGearView` here, beside the page's `GearItemView`.
typealias GearScreen = GearPackage.GearScreen
typealias GearState = GearPackage.GearState
typealias GearEvent = GearPackage.GearEvent
typealias GearPage = GearPackage.GearPage
typealias GearPageTexts = GearPackage.GearPageTexts
typealias GearFigure = GearPackage.GearFigure
typealias GearMountPicker = GearPackage.GearMountPicker
typealias GearMountCandidate = GearPackage.GearMountCandidate
typealias LexiconGearView = LexiconAppPackage.GearView
typealias GearComponent = LexiconAppPackage.GearComponent
typealias GearMountView = LexiconAppPackage.GearMountView

// The profile (ios-native-profile): its contract, what it says and which actions it shows, and the lexicon and moderation types its
// header reads.
typealias ProfileScreen = ProfilePackage.ProfileScreen
typealias ProfileState = ProfilePackage.ProfileState
typealias ProfileEvent = ProfilePackage.ProfileEvent
typealias ProfileTexts = ProfilePackage.ProfileTexts
typealias ProfileActions = ProfilePackage.ProfileActions
typealias FollowLabel = ProfilePackage.FollowLabel
typealias ProfileEntry = ProfilePackage.ProfileEntry
typealias ProfileView = LexiconAppPackage.ProfileView
typealias ProfileDetails = LexiconAppPackage.ProfileDetails
typealias BlurReason = ExportedKotlinPackages.io.github.solcott.activitytracker.data.moderation.BlurReason
typealias ModerationTexts = AppPackage.moderation.ModerationTexts

// Posts (ios-native-post-card): the post card's content and events, the quote sheet, the comment list, the post page and the quotes
// list, and the profile's Posts tab. Kotlin's loaded `PostPage` is `LoadedPostPage` here, beside the native `PostPage` view.
typealias PostsPackage = AppPackage.posts
typealias PostPackage = AppPackage.post
typealias InteractionsPackage = AppPackage.interactions
typealias PostView = LexiconAppPackage.PostView
typealias RepostedPostView = LexiconAppPackage.RepostedPostView
typealias ReasonRepost = LexiconAppPackage.ReasonRepost
typealias CommentView = LexiconAppPackage.CommentView
typealias PostCardContent = PostsPackage.PostCardContent
typealias PostEvent = PostsPackage.PostEvent
typealias QuoteFields = PostsPackage.QuoteFields
typealias QuoteEvent = PostsPackage.QuoteEvent
typealias QuoteSheetTexts = PostsPackage.QuoteSheetTexts
typealias CommentsState = InteractionsPackage.CommentsState
typealias CommentEvent = InteractionsPackage.CommentEvent
typealias CommentsTexts = InteractionsPackage.CommentsTexts
typealias CommentRow = InteractionsPackage.CommentRow
typealias CommentRowKind = InteractionsPackage.CommentRowKind
typealias CommentMenuItem = InteractionsPackage.CommentMenuItem
typealias CommentAction = InteractionsPackage.CommentAction
typealias LikesList = InteractionsPackage.LikesList
typealias PostPageScreen = PostPackage.PostPageScreen
typealias PostPageState = PostPackage.PostPageState
typealias PostPageEvent = PostPackage.PostPageEvent
typealias LoadedPostPage = PostPackage.PostPage
typealias PostPageTexts = PostPackage.PostPageTexts
typealias PostQuotesScreen = PostPackage.PostQuotesScreen
typealias PostQuotesState = PostPackage.PostQuotesState
typealias PostQuotesEvent = PostPackage.PostQuotesEvent
typealias ProfileTab = ProfilePackage.ProfileTab

// The report sheet and the place-name sheet (ios-native-report-sheet): sheet sub-screens a native page shows (`reporting(_:)`,
// `placeNames(_:)`), their carriers, and what they say.
typealias ReportPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.report
typealias ReportScreen = ReportPackage.ReportScreen
typealias ReportState = ReportPackage.ReportState
typealias ReportEvent = ReportPackage.ReportEvent
typealias ReportOuterEvent = ReportPackage.ReportOuterEvent
typealias ReportTexts = ReportPackage.ReportTexts
typealias ReportReasonChoice = ReportPackage.ReportReasonChoice
typealias Reporting = ReportPackage.Reporting
typealias ReportingEvent = ReportPackage.ReportingEvent
typealias BlockOffer = ReportPackage.BlockOffer
typealias ReportReason = ExportedKotlinPackages.io.github.solcott.activitytracker.data.moderation.ReportReason
typealias NamesPackage = AppPackage.names
typealias ProposePlaceNameScreen = NamesPackage.ProposePlaceNameScreen
typealias ProposePlaceNameState = NamesPackage.ProposePlaceNameState
typealias ProposePlaceNameEvent = NamesPackage.ProposePlaceNameEvent
typealias ProposePlaceNameOuterEvent = NamesPackage.ProposePlaceNameOuterEvent
typealias ProposePlaceNameTexts = NamesPackage.ProposePlaceNameTexts
typealias PlaceNameFields = NamesPackage.PlaceNameFields
typealias PlaceNameEvent = NamesPackage.PlaceNameEvent
typealias ShareLimit = NamesPackage.ShareLimit

// The feed (ios-native-feed): its contract, what it says and offers, the timeline's items and the import card they hold, and the
// upload summary its rows show.
typealias FeedScreen = FeedPackage.FeedScreen
typealias FeedState = FeedPackage.FeedState
typealias FeedEvent = FeedPackage.FeedEvent
typealias FeedScope = FeedPackage.FeedScope
typealias FeedTexts = FeedPackage.FeedTexts
typealias FeedScopeOption = FeedPackage.FeedScopeOption
typealias FeedUploadTexts = FeedPackage.FeedUploadTexts
typealias FeedAddChoice = FeedPackage.FeedAddChoice
typealias FeedAddKind = FeedPackage.FeedAddKind
typealias ImportCardTexts = FeedPackage.ImportCardTexts
typealias TimelineItem = LexiconAppPackage.TimelineItem
typealias ImportCardView = LexiconAppPackage.ImportCardView
typealias CardRoute = LexiconAppPackage.CardRoute
typealias UploadSummary = UploadActivityPackage.UploadSummary

// Search (ios-native-search): the Search tab's contract and what it says; the left-elsewhere choice and what it says.
typealias SearchPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.search
typealias AthleteSearchScreen = SearchPackage.AthleteSearchScreen
typealias AthleteSearchState = SearchPackage.AthleteSearchState
typealias AthleteSearchEvent = SearchPackage.AthleteSearchEvent
typealias AthleteSearchTexts = SearchPackage.AthleteSearchTexts
typealias SearchMode = SearchPackage.SearchMode
typealias LeftElsewhereScreen = SettingsPackage.LeftElsewhereScreen
typealias LeftElsewhereState = SettingsPackage.LeftElsewhereState
typealias LeftElsewhereEvent = SettingsPackage.LeftElsewhereEvent
typealias LeftElsewhereTexts = SettingsPackage.LeftElsewhereTexts
// The route map and the dive site page (ios-native-map): what a map shows, the page's contract and what it and a place's names say.
typealias RouteMap = AppPackage.RouteMap
typealias MapLine = AppPackage.MapLine
typealias MapBreak = AppPackage.MapBreak
typealias MapBounds = AppPackage.MapBounds
typealias MapFrame = AppPackage.MapFrame
typealias RouteEnds = AppPackage.RouteEnds
typealias GeoPoint = ExportedKotlinPackages.io.github.solcott.activitytracker.location.GeoPoint
typealias PlaceTexts = SegmentPackage.PlaceTexts
typealias PlaceNameTexts = NamesPackage.PlaceNameTexts
typealias DiveSiteScreen = DiveSitePackage.DiveSiteScreen
typealias DiveSiteState = DiveSitePackage.DiveSiteState
typealias DiveSiteEvent = DiveSitePackage.DiveSiteEvent
typealias DiveSiteTexts = DiveSitePackage.DiveSiteTexts
typealias RegularsSummary = DiveSitePackage.RegularsSummary
typealias DiveSiteView = LexiconAppPackage.DiveSiteView
typealias DiveSiteConditions = LexiconAppPackage.DiveSiteConditions
typealias SegmentNameProposalView = LexiconAppPackage.SegmentNameProposalView
typealias SegmentNameAdoption = LexiconAppPackage.SegmentNameAdoption
