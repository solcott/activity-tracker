import IosShell
import SwiftUI
import UIKit

/// The Search tab's root (segments/segment-browse), drawn from the states `AthleteSearchPresenter` publishes: Athletes and Segments as a
/// segmented choice, the list's first row; in Athletes mode the system's search field over the matching accounts, a page at a time, the prompt
/// while nothing is typed and the message when nothing matched; in Segments mode the shared segment browse screen, embedded until segment
/// browse is native. What it says that follows a rule is Kotlin's (`AthleteSearchTexts`), shared with the Compose page. The query, the mode
/// and the results are the presenter's, so they are kept while the athlete visits other tabs.
struct AthleteSearchView: View {
    let state: AthleteSearchState
    /// The query as typed, held here so the field follows each keystroke; it edits the state's field and follows it back.
    @State private var query: TextFieldModel
    /// The embedded browse screen's controller, made the first time Segments shows and kept with this page, so switching modes back and
    /// forth keeps its map and its query, as the shared tab does.
    @State private var browse = SegmentBrowseController()
    @Environment(\.hostRecord) private var record

    init(state: AthleteSearchState) {
        self.state = state
        _query = State(initialValue: TextFieldModel(state.query))
    }

    var body: some View {
        content
            .pageTitle(Text(AthleteSearchTexts.shared.title(mode: state.mode)))
    }

    @ViewBuilder private var content: some View {
        if state.mode == .Segments {
            // No list to scroll: the choice stays over the browse screen.
            VStack(spacing: 0) {
                modes.padding(.horizontal, 16).padding(.vertical, 8)
                SegmentBrowseEmbed(record: record, controller: browse)
            }
            .background(Color(.systemGroupedBackground))
        } else {
            athletes
        }
    }

    /// Athletes, then Segments, in the order Kotlin declares them.
    private var modes: some View {
        Picker(selection: Binding(get: { state.mode }, set: { send(AthleteSearchEvent.ModeChanged(mode: $0)) })) {
            ForEach(SearchMode.allCases, id: \.self) { mode in
                Text(AthleteSearchTexts.shared.mode(mode: mode)).tag(mode)
            }
        } label: {
            Text(.searchTabTitle)
        }
        .pickerStyle(.segmented)
    }

    private var athletes: some View {
        PagedList(
            state.results, of: ProfileViewBasic.self, errorKind: .SearchAthletes, id: { $0.did.value }, keepsTop: true,
            retry: { send(AthleteSearchEvent.Retry.shared) }, loadMore: { send(AthleteSearchEvent.LoadMore.shared) }
        ) {
            // The choice is the list's first row, scrolling under the large title as the feed's filters do.
            modes.listStatusRow()
            // What to type, before anything is: a search that hasn't run has nothing else to show.
            if let prompt = AthleteSearchTexts.shared.prompt(state: state) {
                Text(prompt).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    .padding(.vertical, 24).listStatusRow()
            }
        } row: { account in
            let name = AppPackage.getShownName(account)
            AppItemRow(
                Text(verbatim: name), lines: [Text(LabelText.shared.handle(handle: account.handle.value))],
                opens: { send(AthleteSearchEvent.Open(did: account.did)) }
            ) {
                Avatar(url: account.avatar, name: name)
            }
        } empty: {
            if let empty = AthleteSearchTexts.shared.empty(state: state) {
                Text(empty).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
            }
        }
        .searchable(
            text: Binding(get: { query.text }, set: { query.edit($0) }), placement: .navigationBarDrawer(displayMode: .always),
            prompt: Text(.searchLabel)
        )
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .task(id: ObjectIdentifier(state.query)) {
            query.bind(to: state.query)
            await query.follow(state.query.values.asAsyncSequence())
        }
    }

    private func send(_ event: any AthleteSearchEvent) {
        state.eventSink(event)
    }
}

/// The view controller of the shared segment browse screen one Search page embeds, made once.
@MainActor
final class SegmentBrowseController {
    private var made: UIViewController?

    func controller(for record: HostRecord) -> UIViewController {
        if let made { return made }
        let controller = IosApp.shared.segmentBrowseViewController(record: record)
        made = controller
        return controller
    }
}

/// The shared segment browse screen under the native Search page's choice (segments/segment-browse), run by Kotlin for the Search tab's
/// record: what it opens goes on the Search tab's stack. Compose pads for the keyboard itself, so SwiftUI doesn't. Outside the host, as in
/// a preview or a snapshot, there is no record to run it, and the space is left empty.
private struct SegmentBrowseEmbed: View {
    let record: HostRecord?
    let controller: SegmentBrowseController

    var body: some View {
        if let record {
            Embedded(make: { controller.controller(for: record) }).ignoresSafeArea(.keyboard)
        } else {
            Color(.systemGroupedBackground)
        }
    }

    private struct Embedded: UIViewControllerRepresentable {
        let make: () -> UIViewController

        // The fallback screens' container, which takes the controller from one that held it before.
        func makeUIViewController(context: Context) -> FallbackContainerController { FallbackContainerController(content: make()) }

        func updateUIViewController(_ container: FallbackContainerController, context: Context) {}
    }
}

#if DEBUG
    #Preview("Search") {
        SamplePages(Samples.athleteSearch) { state in
            NavigationStack { AthleteSearchView(state: state).environment(\.tabRoot, true) }
        }
    }

    #Preview("Search, blank, no match and Segments") {
        SamplePages(Samples.athleteSearchVariants) { state in
            NavigationStack { AthleteSearchView(state: state).environment(\.tabRoot, true) }
        }
    }
#endif
