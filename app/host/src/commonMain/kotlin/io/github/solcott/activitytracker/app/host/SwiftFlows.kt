package io.github.solcott.activitytracker.app.host

import io.github.solcott.activitytracker.app.SessionMode
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

// What the host's flows hand a Swift UI. Swift export (Kotlin 2.5.0-Beta2) turns a flow's element into a Swift value with a cast, which
// works for an object and for a number or a string but converts neither a Kotlin enum into its Swift enum nor a list into an array: the
// cast fails and stops the app. A property of an object is converted properly, so each such flow has a twin here whose elements are
// objects holding the value. Kotlin code reads the plain flows; Swift follows these.

/** A stack's records at one moment, for `TabStack.shown`. */
class ShownRecords(val records: List<HostRecord>)

/** The session's mode at one moment, for `SessionHost.shownMode`. */
class ShownMode(val mode: SessionMode)

/** The chosen tab at one moment, for `SessionHost.shownTab`. */
class ShownTab(val destination: TopLevelDestination)

/** The athlete's unit system at one moment, for `SessionHost.shownUnits`. */
class ShownUnits(val units: UnitSystem)

/**
 * This flow with each value passed through [transform], still a [StateFlow]: it has a current value, which is what a UI draws before it
 * starts following. It holds nothing of its own, so it can never differ from its source.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> {
    val source = this
    return object : StateFlow<R> {
        override val value: R
            get() = transform(source.value)

        override val replayCache: List<R>
            get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<R>): Nothing = source.collect { collector.emit(transform(it)) }
    }
}
