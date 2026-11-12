import SwiftUI

/// Creating a segment from the athlete's own activity (segments/segment-creation), a grouped form over the states
/// `CreateSegmentPresenter` publishes: the route as their privacy zones leave it on the native map with the chosen stretch highlighted
/// (`CreateSegmentState.routeMap`), a two-thumb slider choosing the stretch, what it is or why it can't be a segment, the name, what
/// will be public, any segments it duplicates to open, the allowance reached and a failed check or publish; Publish is in the bar, with
/// progress while it runs. An activity that can't make a segment says why instead. What it says is Kotlin's (`CreateSegmentTexts`),
/// shared with the Compose form.
struct CreateSegmentView: View {
    let state: CreateSegmentState

    var body: some View {
        ContentStateView(state.content, errorKind: state.loadErrorKind, retry: { send(CreateSegmentEvent.Retry.shared) }) {
            if let unavailable = state.unavailable {
                Text(CreateSegmentTexts.shared.unavailable(reason: unavailable))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else if let map = state.routeMap {
                CreateSegmentForm(state: state, map: map)
            }
        }
        .pageTitle(Text(.segmentCreateTitle)) {
            if state.unavailable == nil && state.routeMap != nil {
                if state.publishing {
                    ProgressView()
                } else {
                    Button(.segmentCreatePublish) { send(CreateSegmentEvent.Publish.shared) }
                        .fontWeight(.semibold)
                        .disabled(!state.canPublish)
                }
            }
        }
    }

    private func send(_ event: any CreateSegmentEvent) {
        state.eventSink(event)
    }
}

/// The form once the activity can make a segment, made then so its name field follows the state from the start.
private struct CreateSegmentForm: View {
    let state: CreateSegmentState
    let map: RouteMap
    @State private var name: TextFieldModel
    @Environment(\.stats) private var stats

    init(state: CreateSegmentState, map: RouteMap) {
        self.state = state
        self.map = map
        _name = State(initialValue: TextFieldModel(state.name))
    }

    var body: some View {
        let texts = CreateSegmentTexts.shared
        let duplicates = state.duplicates.elements(as: SegmentView.self)
        Form {
            if let error = state.actionError {
                Section { ErrorMessage(error, layout: .row) }
            }
            if state.limitReached {
                Section { Text(.segmentCreateLimit).foregroundStyle(.red) }
            }
            if !duplicates.isEmpty {
                Section {
                    ForEach(duplicates, id: \.uri.value) { segment in
                        AppListRow(Text(texts.duplicate(segment: segment, units: stats.units)), icon: .location) {
                            state.eventSink(CreateSegmentEvent.OpenSegment(uri: segment.uri))
                        }
                    }
                } header: {
                    Text(.segmentCreateDuplicates)
                }
            }
            Section {
                RouteMapView(map: map).listRowInsets(EdgeInsets())
            }
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    RangeSlider(
                        lower: Double(state.start), upper: Double(state.end), bounds: 0...Double(max(state.lengthMeters, 1)),
                        lowerLabel: Text(texts.startThumb), upperLabel: Text(texts.endThumb),
                        value: { Text(texts.along(meters: $0, units: stats.units)) }
                    ) { start, end in
                        state.eventSink(CreateSegmentEvent.SelectionChanged(start: Float(start), end: Float(end)))
                    }
                    Text(texts.selection(state: state, units: stats.units))
                        .foregroundStyle(texts.selectionFails(state: state) ? AnyShapeStyle(.red) : AnyShapeStyle(.primary))
                }
                .disabled(state.publishing)
            } header: {
                Text(texts.range)
            }
            Section {
                AppTextField(label: .segmentCreateName, model: name, state: state.name, layout: .row)
                    .disabled(state.publishing)
            } footer: {
                Text(verbatim: texts.publicNote(namesActivity: state.namesActivity).map(AppStrings.string).joined(separator: " "))
            }
        }
    }
}

#if DEBUG
    #Preview("New segment") {
        SamplePages(Samples.createSegment) { state in NavigationStack { CreateSegmentView(state: state) } }
    }

    #Preview("New segment, beyond these") {
        SamplePages(Samples.createSegmentVariants) { state in NavigationStack { CreateSegmentView(state: state) } }
    }
#endif
