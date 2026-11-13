import SwiftUI

/// Creating a dive site from the athlete's own dive (dive-sites/site-creation), a grouped form over the states
/// `CreateDiveSitePresenter` publishes: the site's outline on the native map (`CreateDiveSiteState.routeMap`, no start or end), its
/// radius with a slider, the name, what will be public, any sites it duplicates to open, the allowance reached and a failed check or
/// publish; Publish is in the bar, with progress while it runs. A dive that can't make a site says why instead. What it says is
/// Kotlin's (`CreateDiveSiteTexts`), shared with the Compose form.
struct CreateDiveSiteView: View {
    let state: CreateDiveSiteState

    var body: some View {
        ContentStateView(state.content, errorKind: state.loadErrorKind, retry: { send(CreateDiveSiteEvent.Retry.shared) }) {
            if let unavailable = state.unavailable {
                Text(CreateDiveSiteTexts.shared.unavailable(reason: unavailable))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else if let map = state.routeMap {
                CreateDiveSiteForm(state: state, map: map)
            }
        }
        .pageTitle(Text(.createDiveSiteTitle)) {
            if state.unavailable == nil && state.routeMap != nil {
                if state.publishing {
                    ProgressView()
                } else {
                    Button(.createDiveSitePublish) { send(CreateDiveSiteEvent.Publish.shared) }
                        .fontWeight(.semibold)
                        .disabled(!state.canPublish)
                }
            }
        }
    }

    private func send(_ event: any CreateDiveSiteEvent) {
        state.eventSink(event)
    }
}

/// The form once the dive can make a site, made then so its name field follows the state from the start.
private struct CreateDiveSiteForm: View {
    let state: CreateDiveSiteState
    let map: RouteMap
    @State private var name: TextFieldModel
    @Environment(\.stats) private var stats

    init(state: CreateDiveSiteState, map: RouteMap) {
        self.state = state
        self.map = map
        _name = State(initialValue: TextFieldModel(state.name))
    }

    var body: some View {
        let limits = LexiconAppPackage.DiveSiteRecord.Companion.shared
        let duplicates = state.duplicates.elements(as: DiveSiteView.self)
        Form {
            if let error = state.actionError {
                Section { ErrorMessage(error, layout: .row) }
            }
            if state.limitReached {
                Section { Text(.createDiveSiteLimit).foregroundStyle(.red) }
            }
            if !duplicates.isEmpty {
                Section {
                    ForEach(duplicates, id: \.uri.value) { site in
                        AppListRow(Text(verbatim: site.currentName), icon: .location) {
                            state.eventSink(CreateDiveSiteEvent.OpenSite(uri: site.uri))
                        }
                    }
                } header: {
                    Text(.createDiveSiteDuplicates)
                }
            }
            Section {
                RouteMapView(map: map).listRowInsets(EdgeInsets())
            }
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text(CreateDiveSiteTexts.shared.radius(meters: state.radius, units: stats.units))
                    Slider(
                        value: Binding(
                            get: { Double(state.radius) },
                            set: { state.eventSink(CreateDiveSiteEvent.RadiusChanged(meters: Int32($0.rounded()))) }),
                        in: Double(limits.MIN_RADIUS)...Double(limits.MAX_RADIUS)
                    ) {
                        Text(CreateDiveSiteTexts.shared.radius(meters: state.radius, units: stats.units))
                    }
                }
                .disabled(state.publishing)
                AppTextField(label: .createDiveSiteName, model: name, state: state.name, layout: .row)
                    .disabled(state.publishing)
            } footer: {
                Text(CreateDiveSiteTexts.shared.publicNote(namesDive: state.namesDive))
            }
        }
    }
}

#if DEBUG
    #Preview("New dive site") {
        SamplePages(Samples.createDiveSite) { state in NavigationStack { CreateDiveSiteView(state: state) } }
    }

    #Preview("New dive site, beyond these") {
        SamplePages(Samples.createDiveSiteVariants) { state in NavigationStack { CreateDiveSiteView(state: state) } }
    }
#endif
