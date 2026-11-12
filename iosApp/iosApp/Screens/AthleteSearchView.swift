import IosShell
import SwiftUI

/// The Search tab's root (segments/segment-browse), drawn from the states `AthleteSearchPresenter` publishes: Athletes and Segments as a
/// segmented choice, the list's first row; in Athletes mode the system's search field over the matching accounts, a page at a time, the prompt
/// while nothing is typed and the message when nothing matched; in Segments mode the native segment browse (`SegmentBrowseView`), which
/// the host runs as a screen this page's record embeds, so what it opens goes on the Search tab. What it says that follows a rule is Kotlin's (`AthleteSearchTexts`), shared with the Compose page. The query, the mode
/// and the results are the presenter's, so they are kept while the athlete visits other tabs.
struct AthleteSearchView: View {
    let state: AthleteSearchState
    /// The query as typed, held here so the field follows each keystroke; it edits the state's field and follows it back.
    @State private var query: TextFieldModel
    /// Where segment browse's map was left, kept with this page, so switching modes back and forth reopens it there, as the shared tab
    /// does; its query and lists are kept by the host with this page's record.
    @State private var browseMap = SegmentsMapMemory()
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
                if let record {
                    EmbeddedScreenView(record, screen: SegmentBrowseScreen.shared) { (browse: SegmentBrowseState) in
                        SegmentBrowseView(state: browse, memory: browseMap)
                    }
                } else {
                    // Outside the host, as in a preview or a snapshot, there is no record to run browse in; its own previews show it.
                    Spacer()
                }
            }
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
