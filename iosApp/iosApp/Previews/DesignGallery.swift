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
        private let supportingText: String?
        private let isError: Bool
        @State private var model: TextFieldModel

        init(text: String, supportingText: String? = nil, isError: Bool = false) {
            let state = TextFieldPackage.textFieldStateOf(initialText: text)
            self.state = state
            self.supportingText = supportingText
            self.isError = isError
            _model = State(initialValue: TextFieldModel(state))
        }

        var body: some View {
            AppTextField(label: .loginHandleLabel, model: model, state: state, supportingText: supportingText, isError: isError)
        }
    }

    #Preview("Buttons") { DesignGallery.buttons }
    #Preview("Fields") { DesignGallery.fields }
    #Preview("Rows") { DesignGallery.rows }
    #Preview("Page bar") { DesignGallery.pageBar }
    #Preview("Images") { DesignGallery.images }
    #Preview("Cards") { DesignGallery.cards }
    #Preview("Icons") { DesignGallery.icons }
    #Preview("Messages") { DesignGallery.messages }
    #Preview("Content") {
        TabView {
            ForEach(DesignGallery.contentNames, id: \.self) { DesignGallery.content($0) }
        }
        .tabViewStyle(.page)
    }
#endif
