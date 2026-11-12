#if DEBUG
    import SwiftUI

    /// The design system's pieces, each in the states it has: what their previews show and their snapshot tests compare
    /// (ui/ios-design-system, dev/ios-snapshot-tests).
    enum DesignGallery {
        static var buttons: some View {
            VStack(spacing: 16) {
                AppButton(.loginSignIn) {}
                AppButton(.loginSignIn) {}.disabled(true)
                AppSecondaryButton(.actionCancel) {}
                AppTextButton(.actionRetry) {}
                AppIconButton(.settings, label: .actionEdit) {}
                // A row's pair at the small size: a secondary action beside the main one.
                HStack(spacing: 8) {
                    AppSecondaryButton(.followRequestsDecline, small: true) {}
                    AppButton(.followRequestsApprove, small: true) {}
                }
            }
            .padding(24)
        }

        static var fields: some View {
            VStack(spacing: 16) {
                GalleryField(text: "alice.test")
                GalleryField(text: "", supportingText: "Your handle, without the @.")
                GalleryField(text: "twelve", supportingText: "Enter a number", isError: true)
            }
            .padding(24)
        }

        /// Form rows: text fields as rows (one marked wrong, one blank with a hint) and as several lines, and menu choices, one
        /// beside a label too long for its line.
        static var formRows: some View {
            Form {
                Section {
                    GalleryField(text: "Gravel bike", layout: .row)
                    GalleryField(text: "", layout: .row, supportingText: "Give your gear a name", isError: true)
                    GalleryField(text: "", layout: .row, hint: Text(.sportZonesDefault("190")))
                    AppPickerRow(
                        "Type", selection: .constant("bike"), options: ["bike", "shoes"],
                        optionLabel: { $0 == "bike" ? "Bike" : "Shoes" })
                    AppPickerRow(
                        name: "Gear", selection: .constant("self"), options: ["public", "self"],
                        optionLabel: { $0 == "public" ? "Public" : "Only me" }
                    ) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(verbatim: "Gear")
                            Text(verbatim: "The gear you used, with its name, kind and photo").font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
                Section {
                    GalleryField(text: "Bought used from a friend.\nNew chain in March.", layout: .multiline)
                }
            }
        }

        static var rows: some View {
            List {
                Section {
                    AppListRow(Text(verbatim: "Units"), summary: Text(verbatim: "Imperial"), icon: .settings, opens: {})
                    AppListRow(Text(verbatim: "Hide from leaderboards")) { Toggle(isOn: .constant(true)) { EmptyView() }.labelsHidden() }
                    AppListRow(Text(verbatim: "Version"), summary: Text(verbatim: "1.0"))
                    AppListRow(Text(verbatim: "Privacy zones"), summary: Text(verbatim: "2 zones"), selected: true, opens: {})
                } header: {
                    Text(verbatim: "Settings")
                }
            }
        }

        /// A page's header in a column beside another screen (`pageTitle` in a pane), with the rows that scroll under it.
        static var pageBar: some View {
            List {
                Section {
                    ForEach(1..<16) { AppListRow(Text(verbatim: "Row \($0)"), summary: Text(verbatim: "Followers")) }
                } header: {
                    Text(verbatim: "Who can see each detail")
                }
            }
            .pageTitle(Text(verbatim: "Segments & groups"))
            .environment(\.inPane, true)
        }

        /// The same page's header with actions at its trailing end.
        static var pageBarActions: some View {
            List {
                ForEach(1..<6) { AppListRow(Text(verbatim: "Row \($0)")) }
            }
            .pageTitle(Text(verbatim: "Gear")) {
                AppIconButton(.add, label: .gearAdd) {}
                AppIconButton(.more, label: .gearMore) {}
            }
            .environment(\.inPane, true)
        }

        /// A deeper page's header in a column: a back button at its leading end, alone and with actions at the trailing end.
        static var pageBarBack: some View {
            VStack(spacing: 0) {
                List { ForEach(1..<4) { AppListRow(Text(verbatim: "Row \($0)")) } }
                    .pageTitle(Text(verbatim: "Leave Activity Tracker"), back: {})
                List { ForEach(1..<4) { AppListRow(Text(verbatim: "Row \($0)")) } }
                    .pageTitle(Text(verbatim: "Sport zones"), back: {}) {
                        AppIconButton(.add, label: .gearAdd) {}
                    }
            }
            .environment(\.inPane, true)
            .environment(\.paneRoot, false)
        }

        /// A page's title with a subtitle under it: in the stack's bar, and in a column header with a back button.
        static var pageBarSubtitle: some View {
            VStack(spacing: 0) {
                NavigationStack {
                    List { ForEach(1..<4) { AppListRow(Text(verbatim: "Row \($0)")) } }
                        .pageTitle(Text(verbatim: "Followers"), subtitle: Text(verbatim: "@alice.test"))
                }
                List { ForEach(1..<4) { AppListRow(Text(verbatim: "Row \($0)")) } }
                    .pageTitle(Text(verbatim: "Following"), subtitle: Text(verbatim: "@bob.test"), back: {})
                    .environment(\.inPane, true)
                    .environment(\.paneRoot, false)
            }
        }

        /// A deeper page that can't go back right now, as a running leave: no back button.
        static var pageBarNoBack: some View {
            List { ForEach(1..<4) { AppListRow(Text(verbatim: "Row \($0)")) } }
                .pageTitle(Text(verbatim: "Leave Activity Tracker"), back: {}, canGoBack: false)
                .environment(\.inPane, true)
                .environment(\.paneRoot, false)
        }

        /// Pictures before they load, or with none: avatars with their initials, at two sizes, and a thumbnail's fill.
        static var images: some View {
            HStack(spacing: 16) {
                Avatar(url: nil, name: "Bob")
                Avatar(url: nil, name: "carol")
                Avatar(url: nil, name: "Dave", size: 64)
                Thumbnail(url: nil)
                Thumbnail(url: nil, size: 64)
            }
            .padding(24)
        }

        /// Rows of things: one that opens with a summary, one with a control at its end, the control unavailable, and a row with no
        /// control and a note.
        static var itemRows: some View {
            List {
                Section {
                    AppItemRow(
                        Text(verbatim: "Gravel bike"), lines: [Text(verbatim: "Bike · Public")], summary: Text(verbatim: "3,200 km"),
                        opens: {}
                    ) { Thumbnail(url: nil) }
                    AppItemRow(Text(verbatim: "Bob"), lines: [Text(verbatim: "@bob.test")], opens: {}) {
                        Avatar(url: nil, name: "Bob")
                    } trailing: {
                        AppSecondaryButton(.actionUnblock, small: true) {}
                    }
                    AppItemRow(Text(verbatim: "Carol"), lines: [Text(verbatim: "@carol.test")], opens: {}) {
                        Avatar(url: nil, name: "Carol")
                    } trailing: {
                        AppSecondaryButton(.actionUnblock, small: true) {}.disabled(true)
                    }
                    AppItemRow(
                        Text(verbatim: "dave.test"), lines: [Text(verbatim: "@dave.test"), Text(.blockedOnBluesky)], opens: {}
                    ) { Avatar(url: nil, name: "dave.test") } trailing: { EmptyView() }
                }
            }
        }

        /// Filter menus over a list: one narrowing it (filled), one showing everything, and a choice with nothing to choose, as its label.
        static var menuChips: some View {
            List {
                HStack(spacing: 8) {
                    AppMenuChip(
                        nil, filled: true, selection: .constant("45–54"), options: ["All ages", "45–54"], optionLabel: { $0 })
                    AppMenuChip(
                        "Gender", filled: false, selection: .constant("All genders"), options: ["All genders", "Women"],
                        optionLabel: { $0 })
                    Text(verbatim: "Run").font(.subheadline.weight(.medium))
                }
                .listStatusRow()
            }
        }

        /// A paged list in the state the block list's sample `name` is in (`PagedStates`: loading, failed, empty, loaded, refreshing,
        /// refreshFailed, loadingMore, pageFailed, end), with plain rows.
        static func paged(_ name: String) -> some View {
            PagedList(
                Samples.blockList[name]!.accounts, of: BlockListAccount.self, errorKind: .LoadBlocks, id: { $0.profile.did.value },
                retry: {}, loadMore: {}
            ) {
                EmptyView()
            } row: { account in
                Text(verbatim: account.profile.handle.value)
            } empty: {
                Text(.blockedAccountsEmpty).foregroundStyle(.secondary).frame(maxWidth: .infinity)
            }
        }

        /// A paged list with rows of its own around the items: "Choices" over them and "Your place" after them, loaded (`after`), and
        /// while a first page loads with the top kept (`keepsTop`).
        static func pagedWithRows(_ name: String) -> some View {
            let state = Samples.blockList[name == "keepsTop" ? "loading" : "loadingMore"]!.accounts
            return PagedList(
                state, of: BlockListAccount.self, errorKind: .LoadBlocks, id: { $0.profile.did.value }, keepsTop: name == "keepsTop",
                retry: {}, loadMore: {}
            ) {
                Text(verbatim: "Choices").listStatusRow()
            } row: { account in
                Text(verbatim: account.profile.handle.value)
            } after: {
                Section { Text(verbatim: "carol.test") } header: { Text(.leaderboardYourPlace) }
            } empty: {
                Text(.blockedAccountsEmpty).foregroundStyle(.secondary).frame(maxWidth: .infinity)
            }
        }

        nonisolated static let pagedWithRowsNames = ["after", "keepsTop"]

        nonisolated static let pagedNames = [
            "loading", "failed", "empty", "loaded", "refreshing", "refreshFailed", "loadingMore", "pageFailed", "end",
        ]

        static var cards: some View {
            VStack(spacing: 16) {
                AppCard {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(verbatim: "Distance").font(.caption).foregroundStyle(.secondary)
                        Text(verbatim: Stats(units: .Imperial).distance(4_828)).font(.title2.bold())
                    }
                }
                AppCard {
                    HStack(spacing: 12) {
                        SportIcon(KotlinActivityType(sport: "run", subSport: nil))
                        Text(verbatim: Labels.sport("run"))
                        Spacer()
                        Image(.like, filled: true).foregroundStyle(Color(.like))
                    }
                }
                HStack(spacing: 24) {
                    AppProgress()
                    AppProgress(small: true)
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color(.systemGroupedBackground))
        }

        /// Line charts: one in the accent colour with a gap, a profile tinted by grade, and that profile with its readout shown.
        static var charts: some View {
            List {
                LineChartView(chart: Samples.plainChart)
                LineChartView(chart: Samples.profileChart)
                LineChartView(chart: Samples.profileChart, selected: 80)
            }
        }

        /// An activity card's lines: a run's pace with the faster pace at the top, and a ride's heart rate, each its average dashed.
        static var activityCharts: some View {
            List {
                LineChartView(chart: Samples.paceChart)
                LineChartView(chart: Samples.heartRateChart, selected: 20)
            }
        }

        /// Step charts: a threshold's steps with its dots and values, the same with a readout, and its sparkline.
        static var stepCharts: some View {
            List {
                LineChartView(chart: Samples.stepChart, height: 150)
                LineChartView(chart: Samples.stepChart, height: 150, selected: 1)
                LineChartView(chart: Samples.sparklineChart, height: 26).frame(width: 150)
            }
        }

        static var icons: some View {
            VStack(alignment: .leading, spacing: 16) {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 8), spacing: 12) {
                    ForEach(AppIcon.allCases, id: \.self) { Image($0) }
                }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 8), spacing: 12) {
                    ForEach(["run", "ride", "swim", "hike", "ski", "yoga", "dive", "quidditch"], id: \.self) { sport in
                        SportIcon(KotlinActivityType(sport: sport, subSport: nil)).foregroundStyle(.tint)
                    }
                }
            }
            .padding(24)
        }

        static var messages: some View {
            VStack(spacing: 24) {
                ErrorMessage(offline) {}
                ErrorMessage(UiError(kind: .SaveProfile, reason: nil))
                ErrorMessage(offline, layout: .row) {}
            }
            .padding(24)
        }

        /// The loaded-content layout in the state `name` gives: `loading`, `failed`, `loaded`, `refreshing` or `refreshFailed`.
        static func content(_ name: String) -> some View {
            let state = contentStates[name]!
            return ContentStateView(state, as: UiError.self, errorKind: .LoadFeed, retry: {}) { _ in
                List {
                    RefreshStatusView(state: state, errorKind: .LoadFeed) {}
                    ForEach(1..<4) { Text(verbatim: "Loaded row \($0)") }
                }
            }
        }

        nonisolated static let contentNames = ["loading", "failed", "loaded", "refreshing", "refreshFailed"]

        private static var offline: UiError { UiError(kind: .LoadFeed, reason: DataError.Network.shared) }

        private static var contentStates: [String: KotlinContentState] {
            // Any Kotlin object stands in for the loaded value: the layout only needs something to hand its content.
            let data = UiError(kind: .LoadFeed, reason: nil)
            let network = DataError.Network.shared
            return [
                "loading": Samples.content.loading(data: data),
                "failed": Samples.content.failed(data: data, reason: network),
                "loaded": Samples.content.loaded(data: data, origin: .Network),
                "refreshing": Samples.content.refreshing(data: data),
                "refreshFailed": Samples.content.refreshFailed(data: data, reason: network),
            ]
        }
    }

    /// A field over a text field state of its own, as a presenter would give one.
    private struct GalleryField: View {
        private let state: KotlinTextFieldState
        private let layout: AppTextField.Layout
        private let supportingText: String?
        private let isError: Bool
        private let hint: Text?
        @State private var model: TextFieldModel

        init(
            text: String, layout: AppTextField.Layout = .stacked, hint: Text? = nil, supportingText: String? = nil, isError: Bool = false
        ) {
            let state = TextFieldPackage.textFieldStateOf(initialText: text)
            self.state = state
            self.layout = layout
            self.hint = hint
            self.supportingText = supportingText
            self.isError = isError
            _model = State(initialValue: TextFieldModel(state))
        }

        var body: some View {
            switch layout {
            case .stacked:
                AppTextField(label: .loginHandleLabel, model: model, state: state, supportingText: supportingText, isError: isError)
            case .row:
                AppTextField(
                    label: .gearName, model: model, state: state, layout: .row, hint: hint, supportingText: supportingText,
                    isError: isError)
            case .multiline:
                AppTextField(label: .gearNotes, model: model, state: state, layout: .multiline)
            case .bare:
                AppTextField(label: .detailCommentPlaceholder, model: model, state: state, layout: .bare, hint: hint)
            }
        }
    }

    #Preview("Buttons") { DesignGallery.buttons }
    #Preview("Fields") { DesignGallery.fields }
    #Preview("Form rows") { DesignGallery.formRows }
    #Preview("Rows") { DesignGallery.rows }
    #Preview("Page bar") { DesignGallery.pageBar }
    #Preview("Page bar with actions") { DesignGallery.pageBarActions }
    #Preview("Page bar with back") { DesignGallery.pageBarBack }
    #Preview("Page bar with a subtitle") { DesignGallery.pageBarSubtitle }
    #Preview("Page bar that can't go back") { DesignGallery.pageBarNoBack }
    #Preview("Images") { DesignGallery.images }
    #Preview("Item rows") { DesignGallery.itemRows }
    #Preview("Filter menus") { DesignGallery.menuChips }
    #Preview("Paged list") {
        TabView {
            ForEach(DesignGallery.pagedNames, id: \.self) { DesignGallery.paged($0) }
        }
        .tabViewStyle(.page)
    }
    #Preview("Paged list with rows around") {
        TabView {
            ForEach(DesignGallery.pagedWithRowsNames, id: \.self) { DesignGallery.pagedWithRows($0) }
        }
        .tabViewStyle(.page)
    }
    #Preview("Cards") { DesignGallery.cards }
    #Preview("Charts") { DesignGallery.charts }
    #Preview("Activity charts") { DesignGallery.activityCharts }
    #Preview("Step charts") { DesignGallery.stepCharts }
    #Preview("Icons") { DesignGallery.icons }
    #Preview("Messages") { DesignGallery.messages }
    #Preview("Content") {
        TabView {
            ForEach(DesignGallery.contentNames, id: \.self) { DesignGallery.content($0) }
        }
        .tabViewStyle(.page)
    }
#endif
