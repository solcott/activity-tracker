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

/** Declares the sub-screen factory sets so the graph is valid before (and without) any `@SubCircuitInject` contribution. */
@ContributesTo(SessionScope::class)
interface SubCircuitMultibinds {
    @Multibinds(allowEmpty = true) fun subPresenterFactories(): Set<SubPresenterFactory>

    @Multibinds(allowEmpty = true) fun subUiFactories(): Set<SubUiFactory>
}
