import ActivityTrackerCoreData
import ActivityTrackerCoreLexicon
import AppCommonApi
import ComSlackCircuitCircuitRuntime
import CoreTextfield
import FeatureGearApi
import FeatureLoginApi
import FeatureProfileApi
import FeatureSettingsApi
import IoGithubSolcottDataresult
import IoGithubSolcottUistate
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

// Gear and the blocked and muted lists, Settings' pages in the gear and profile contract modules.
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
typealias ProfilePackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.profile
typealias BlockListScreen = ProfilePackage.BlockListScreen
typealias BlockListState = ProfilePackage.BlockListState
typealias BlockListEvent = ProfilePackage.BlockListEvent
typealias BlockListAccount = ProfilePackage.BlockListAccount
typealias BlockListKind = ProfilePackage.BlockListKind
typealias BlockListTexts = ProfilePackage.BlockListTexts
typealias ProfileViewBasic = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
typealias Did = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.Did

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
typealias LoginTexts = LoginPackage.LoginTexts
typealias UnitSystem = ExportedKotlinPackages.io.github.solcott.activitytracker.data.units.UnitSystem
typealias KotlinActivityType = ExportedKotlinPackages.io.github.solcott.activitytracker.lexicon.app.ActivityType
typealias KotlinContentState = ExportedKotlinPackages.io.github.solcott.uistate.ContentState
typealias KotlinPagedState = ExportedKotlinPackages.io.github.solcott.uistate.PagedState
typealias LoadStatus = ExportedKotlinPackages.io.github.solcott.uistate.LoadStatus
typealias DataError = ExportedKotlinPackages.io.github.solcott.dataresult.DataError
