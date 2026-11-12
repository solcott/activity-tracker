import SwiftUI

/// Picking another of the athlete's activities to combine with this one (feed/combined-activities), drawn from the states
/// `CombinePickerPresenter` publishes: the explanation, then each candidate with its type's icon, title, start and length, and device; a
/// tap combines it, a spinner on that row while the others wait; why combining failed; and a message when there is none. Every word is
/// Kotlin's (`CombinePickerTexts`), as the shared picker's are.
struct CombinePickerView: View {
    let state: CombinePickerState

    @Environment(\.clock) private var clock

    var body: some View {
        let texts = CombinePickerTexts.shared
        ContentStateView(state.content, errorKind: .LoadCombineCandidates, retry: retry) {
            List {
                Section {
                    Text(texts.explained).font(.subheadline).foregroundStyle(.secondary)
                }
                RefreshStatusView(state: state.content, errorKind: .LoadCombineCandidates, retry: retry).listStatusRow()
                if let error = state.error {
                    ErrorMessage(error, layout: .row)
                }
                let candidates = state.candidates.elements(as: ActivityView.self)
                Section {
                    if candidates.isEmpty {
                        Text(texts.none).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.vertical, 24)
                    } else {
                        ForEach(candidates, id: \.uri.value) { activity in candidate(activity) }
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
        .pageTitle(Text(texts.title))
    }

    private func candidate(_ activity: ActivityView) -> some View {
        let texts = CombinePickerTexts.shared
        return Button {
            state.eventSink(texts.pick(activity: activity))
        } label: {
            HStack(spacing: 12) {
                SportIcon(texts.type(activity: activity)).foregroundStyle(.tint)
                VStack(alignment: .leading, spacing: 2) {
                    Text(texts.heading(activity: activity)).font(.subheadline.weight(.semibold))
                    clock.text(texts.detail(activity: activity, now: clock.now, zone: clock.kotlinZone))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if let device = texts.device(activity: activity) {
                        Text(device).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if texts.isCombining(state: state, activity: activity) {
                    AppProgress(small: true)
                }
            }
            .foregroundStyle(.primary)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!texts.picks(state: state))
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    private func retry() {
        state.eventSink(CombinePickerEvent.Retry.shared)
    }
}

#if DEBUG
    #Preview("Combine with") {
        SamplePages(Samples.combinePicker) { state in NavigationStack { CombinePickerView(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
