# Design

## Context

The host (`:app-host`, dev/presenter-host) runs each shown `HostRecord`'s presenter in one Molecule composition per scope
(`RunRecords`): the top record of the chosen tab and any record the UI reports visible run, each inside a `SaveableStateProvider` and a
`RetainedStateProvider` keyed by the record, so a covered record gets its state back and a record that leaves its stack is forgotten.
A SubCircuit (`circuitx-subcircuit` 0.39.0) is a `SubScreen<OuterEvent>`, a `SubPresenter.present(outerEventSink)` found by
`SubPresenterFactory.create(screen)`, and a `SubUi`. `SubCircuitContent` remembers the presenter per screen (`remember(screen)`) and keys
its content on the screen, so the sub-presenter lives in the parent UI's composition, under the parent record's retained state, and an
unequal screen remounts it. The session graph has the `Set<SubPresenterFactory>` (`SubCircuitMultibinds`, `composeApp`); `AppScope` has
none.

What Swift export (Kotlin 2.5.0-Beta2) gives: `SubCircuitUiState` and `SubCircuitOuterEvent` are exported protocols, and a contract's
sub-screen state and outer events conform (`FeedFilterState: SubCircuitUiState`, `FeedFilterOuterEvent: SubCircuitOuterEvent`). The
generic `SubScreen` isn't: `FeedFilterScreen` is a plain class in Swift. A flow's element arrives by a cast, which works for objects
(Phase 2 findings).

## Goals / Non-Goals

**Goals:** the host runs sub-screens with Compose's lifetime rules; Swift draws and drives one; the Compose shell and the fallback
screens don't change.

**Non-Goals:** a native Feed, native report or place-name sheets, an `AppScope` SubCircuit, the type picker's search and sport collapse
in Swift.

## Decisions

1. **The UI asks for a sub-screen; the host doesn't discover them.** `HostRecord.sub(screen: Any, outerEventSink:
   (SubCircuitOuterEvent) -> Unit): HostSubRecord` returns the record's sub-record for a screen equal to `screen`, or makes one. This
   mirrors `SubCircuitContent`: the UI decides which sub-screens show (a sheet only while open), and the outer-event mapping stays in
   each UI, as the Compose UI already writes it. *Alternative:* a contract interface listing a state's embedded sub-screens with their
   outer-event mapping, which the host would run on its own. Deterministic, but every contract with a sub-screen changes, the mapping
   moves out of the Compose UIs (or is written twice), and a generic holder in a contract meets Swift export's erased generics. Rejected.
   The latest closure wins: asking again replaces the sink, as a recomposed `SubCircuitContent` passes its newest lambda.
2. **`screen: Any`, checked in Kotlin.** Swift can't name `SubScreen<*>`, and a parameter of a type the export can't carry would be a stub.
   The host checks `screen is SubScreen<*>` and fails naming it otherwise, and fails naming the screen when no factory presents it
   (the host's rule for screens).
3. **A sub-record runs inside its parent's composition.** `RunRecord(parent)` presents the parent, then each sub-record under
   `key(sub.key)`, so a sub-screen's `rememberRetained` and `rememberSaveable` sit under the parent's providers exactly as they do under
   `SubCircuitContent`: covered with the parent (saved), back with it (restored), forgotten when the parent leaves. It needs no visibility
   report of its own. *Alternative:* a sub-record that runs while Swift reports its view on screen. SwiftUI's `onDisappear` fires for a
   covered view too, after the push animates, and its order against the host's recomposition isn't fixed, so a covered parent's
   sub-screen could be dropped instead of saved. Rejected.
4. **Lifetime: until the parent leaves, the UI closes it, or a new screen of its class replaces it.** A sub-record has no view lifetime
   in SwiftUI it can tie to reliably (a `@State` initial value is rebuilt on every parent body), so the host holds sub-records on their
   parent record: `HostSubRecord.close()` drops one (a dismissed sheet), and asking for a screen of the same class that isn't equal to a
   held one drops the held one, as `SubCircuitContent` at one call site remounts on a changed screen. A record holds at most one
   sub-record per sub-screen class: no contract embeds two of one class.
5. **Sub-presenters come from the session graph.** `SessionHost` takes `Set<SubPresenterFactory>` beside the presenter factories
   (`Presenters` grows a sub-screen lookup), and every record of its stacks and single screens can make sub-records. `AppHost`'s sign-in
   record has none: asking it for a sub-screen fails naming the screen.
6. **Swift's view.** `HostSubRecord.state` is a `StateFlow<SubCircuitUiState?>`; Swift follows it with `Following` and casts to the
   sub-screen's state, as `NativeScreen` does for a screen. A small `SubScreenView(record:screen:outer:content:)` in `Shell/` asks the
   record and draws the cast state. The native `FeedFilterBar(state:)` draws chips with `AppMenuChip`, a date `Menu`, Clear, a type sheet
   (quick types and categories as toggles, Apply and Cancel) and a custom range sheet (a graphical `DatePicker`, one pick per date),
   texts from `FeedFilterTexts`. It is placed in no page.
7. **Probe first, throwaway.** A throwaway Swift test calls a throwaway Kotlin helper that builds a record over a fake parent embedding
   a real `FeedFilterScreen` and the real `FeedFilterPresenter`, asks it for the sub-record from Swift, sends `SetRange` and `Clear`, and
   reads the published state and the outer events. It answers whether `Any`, the closure over `SubCircuitOuterEvent` and the state flow
   cross. Removed after; the JVM host tests cover the host's rules permanently, and a `composeApp` JVM test runs the real filter presenter
   through a `SessionHost`.

## Risks / Trade-offs

- [The UI forgets to close a sheet's sub-screen] → its state lasts until the parent leaves, and an equal sheet reopens with it. Each
  native sheet closes its sub-record when dismissed; the rule file says so.
- [An equal screen never remounts] → the host keys by equality like Compose. `FeedPresenter`'s Clear makes `FeedFilterScreen(Default)`,
  equal to the first one when the athlete started from the default, so in both UIs the control keeps its filter after an outside clear.
  Recorded as a finding for the Feed change; not fixed here.
- [A sub-screen of a covered parent emits an outer event] → it can't: it stops with its parent.
