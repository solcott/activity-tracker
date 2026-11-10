import ActivityTrackerCoreData
import AppCommonApi
import ComSlackCircuitCircuitRuntime
import CoreTextfield
import FeatureLoginApi
import PresenterHost

// Swift export gives a Kotlin module top-level names only when it is exported whole with a root package. The presenter host and Circuit
// reach Swift through other modules' signatures, so their types are spelt in full; these name the ones the shell uses.
typealias HostPackage = ExportedKotlinPackages.io.github.solcott.activitytracker.app.host
typealias AppHost = HostPackage.AppHost
typealias SessionHost = HostPackage.SessionHost
typealias TabStack = HostPackage.TabStack
typealias HostRecord = HostPackage.HostRecord
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
