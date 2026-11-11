import ActivityTrackerCoreData
import ActivityTrackerCoreLexicon
import AppCommonApi
import ComSlackCircuitCircuitRuntime
import CoreTextfield
import FeatureGearApi
import FeatureInvitesApi
import FeatureLoginApi
import FeatureProfileApi
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

// Training zones, threshold history and Privacy zones, still the shared UI's: what a native page will read, which `ZonesContractsTests`
// reads now. `Zone` and `PrivacyZone` have Swift types because `ZoneTexts`' functions name them (dev/ios-swift-interop).
typealias TrainingZonesState = SettingsPackage.TrainingZonesState
typealias TrainingZones = SettingsPackage.TrainingZones
typealias ZoneSummary = SettingsPackage.ZoneSummary
typealias SportZonesState = SettingsPackage.SportZonesState
typealias ZoneKindOption = SettingsPackage.ZoneKindOption
typealias ZoneTexts = SettingsPackage.ZoneTexts
typealias ThresholdHistoryState = SettingsPackage.ThresholdHistoryState
typealias ThresholdHistoryGroup = SettingsPackage.ThresholdHistoryGroup
typealias ThresholdHistorySection = SettingsPackage.ThresholdHistorySection
typealias PrivacyZonesState = SettingsPackage.PrivacyZonesState
typealias TrainingPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.data.training
typealias ZoneKind = TrainingPackage.ZoneKind
typealias Zone = TrainingPackage.Zone
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
