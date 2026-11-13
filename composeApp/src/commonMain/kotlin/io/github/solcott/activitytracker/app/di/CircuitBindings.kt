package io.github.solcott.activitytracker.app.di

import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import com.slack.circuit.serialization.CircuitSerializerRegistration
import com.slack.circuit.serialization.SerializableCircuitSaver
import com.slack.circuit.subcircuit.SubCircuit
import com.slack.circuit.subcircuit.SubPresenterFactory
import com.slack.circuit.subcircuit.SubUiFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionScope

/** Circuit for signed-out screens (`@CircuitInject(..., AppScope::class)`). */
class AppCircuit(val circuit: Circuit)

/** Circuit for signed-in screens (`@CircuitInject(..., SessionScope::class)`). */
class SessionCircuit(val circuit: Circuit)

/**
 * Screens are saved with kotlinx.serialization. Metro contributes a [CircuitSerializerRegistration] for each `@CircuitSerializable(scope)`
 * screen into that scope's set.
 */
private fun circuit(
    presenters: Set<Presenter.Factory>,
    uis: Set<Ui.Factory>,
    registrations: Set<CircuitSerializerRegistration>,
): Circuit =
    Circuit.Builder().addPresenterFactories(presenters).addUiFactories(uis).setCircuitSaver(SerializableCircuitSaver(registrations)).build()

@ContributesTo(AppScope::class)
@BindingContainer
object AppCircuitBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun appCircuit(
        presenters: Set<Presenter.Factory>,
        uis: Set<Ui.Factory>,
        registrations: Set<CircuitSerializerRegistration>,
    ): AppCircuit = AppCircuit(circuit(presenters, uis, registrations))
}

@ContributesTo(SessionScope::class)
@BindingContainer
object SessionCircuitBindings {
    @Provides
    @SingleIn(SessionScope::class)
    fun sessionCircuit(
        presenters: Set<Presenter.Factory>,
        uis: Set<Ui.Factory>,
        registrations: Set<CircuitSerializerRegistration>,
    ): SessionCircuit = SessionCircuit(circuit(presenters, uis, registrations))

    /**
     * Embeddable sub-screens (`@SubCircuitInject(..., SessionScope::class)`), rendered with `SubCircuitContent` through `LocalSubCircuit`.
     * See the SubCircuit section of `.claude/rules/compose-screens.md`.
     */
    @Provides
    @SingleIn(SessionScope::class)
    fun subCircuit(presenters: Set<SubPresenterFactory>, uis: Set<SubUiFactory>): SubCircuit =
        SubCircuit.builder().addPresenterFactories(presenters).addUiFactories(uis).build()
}

/**
 * Declares the sub-screens' UI factories so the graph is valid before (and without) any `@SubCircuitInject` contribution. Their presenter
 * factories are declared with the session graph (`SubPresenterMultibinds`), since the iOS host runs them too.
 */
@ContributesTo(SessionScope::class)
interface SubUiMultibinds {
    @Multibinds(allowEmpty = true) fun subUiFactories(): Set<SubUiFactory>
}

/**
 * The Compose apps' Circuit for signed-out screens. Contributed here, not declared on [AppGraph], so the graphs this module's platforms
 * declare (Android, desktop, web) implement it and the iOS graph, generated where this module isn't on the classpath, binds no `Ui.Factory`
 * (ui/ios-native-shell).
 */
@ContributesTo(AppScope::class)
interface AppCircuits {
    val appCircuit: AppCircuit
}

/** The Compose apps' Circuit for signed-in screens and their sub-screens, contributed for the reason [AppCircuits] is. */
@ContributesTo(SessionScope::class)
interface SessionCircuits {
    val sessionCircuit: SessionCircuit
    val subCircuit: SubCircuit
}

/** This graph's [AppCircuits]. Every graph the Compose apps create implements it, since Metro merges what this module contributes. */
val AppGraph.circuits: AppCircuits
    get() = this as AppCircuits

/** This session graph's [SessionCircuits], on any session graph a Compose app's graph creates. */
val SessionGraph.circuits: SessionCircuits
    get() = this as SessionCircuits
