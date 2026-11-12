import SwiftUI

/// The feed (feed/feed-filter, feed/posts, feed/import-cards, data/outbox), the Feed tab's root, drawn from the states `FeedPresenter`
/// publishes: one inset grouped list of the filter bar (the host's sub-screen, `FeedFilterBar`), the upload rows opening the queue, a
/// refresh's failure and a failed action, the segments and grouping prompts, and the timeline as native activity, import and post cards
/// in its order, or the empty message, with Clear filters when the filters left nothing. Until the first answer the bar stays and the
/// load's progress or failure shows under it. Its large title names the scope, which a chip leading the filters chooses (a large
/// title shows no title menu) as the title's menu does once it collapses inline; Add sits in the bar; the list pulls to refresh. Every word and condition is Kotlin's (`FeedTexts`, `ImportCardTexts`, `FeedState`), shared with the Compose feed.
///
/// The quote sheet is the posts' (`quoteSheet`), and the report sheet shows once a post's Report sends `ReportingEvent.Open`
/// (`reporting(_:)`), which the native post card's menu doesn't offer yet. The feed loads one page, on every platform, so it asks for
/// no more.
struct FeedPage: View {
    let state: FeedState
    /// The filter bar's state where no host runs it (previews and snapshots); never given in the app.
    var filterSample: FeedFilterState? = nil
    @Environment(\.hostRecord) private var record
    @Environment(\.clock) private var clock

    var body: some View {
        let shown = ContentShown.Companion.shared.of(state: state.content, errorKind: .LoadFeed)
        List {
            filterBar.listStatusRow()
            switch shown.sealedType() {
            case .progress:
                AppProgress().frame(maxWidth: .infinity).padding(.vertical, 24).listStatusRow()
            case let .failure(failure):
                ErrorMessage(failure.value.error, retry: refresh).frame(maxWidth: .infinity).padding(.vertical, 24).listStatusRow()
            case .content:
                loaded
            }
        }
        .listStyle(.insetGrouped)
        .pullToRefresh(refreshing: refreshing(shown)) { refresh() }
        .pageTitle(Text(FeedTexts.shared.title(state: state))) { addMenu }
        .toolbarTitleMenu { scopeMenu }
        .quoteSheet(state.quote)
        .reporting(state.reporting)
    }

    private func refreshing(_ shown: ContentShown) -> Bool {
        if case let .content(content) = shown.sealedType() { return content.value.refreshing }
        return false
    }

    // MARK: The bar

    @ViewBuilder private var filterBar: some View {
        if let record {
            SubScreenView(record, screen: state.filterScreen, outerEvent: filterChanged) { (bar: FeedFilterState) in
                FeedFilterBar(state: bar, leading: AnyView(scopeChip))
            }
        } else if let filterSample {
            FeedFilterBar(state: filterSample, leading: AnyView(scopeChip))
        }
    }

    /// The scopes as a chip leading the filters: a large title shows no title menu, so this is where the scope is chosen while the title
    /// is large. It never fills, since a scope chooses whose activities rather than narrowing them.
    private var scopeChip: some View {
        let options = FeedTexts.shared.scopes(state: state)
        return AppMenuChip(
            nil, filled: false, selection: Binding(get: { state.scope }, set: { send(FeedEvent.SetScope(scope: $0)) }),
            options: options.map(\.scope)
        ) { scope in
            options.first { $0.scope == scope }.map { AppStrings.string($0.label) } ?? ""
        }
    }

    private func filterChanged(_ event: any SubCircuitOuterEvent) {
        if let changed = event as? FeedFilterOuterEvent.Changed {
            send(FeedEvent.FilterChanged(filter: changed.filter))
        }
    }

    /// The scopes again in the title's menu, which the system offers once the title has collapsed inline.
    private var scopeMenu: some View {
        let options = FeedTexts.shared.scopes(state: state)
        return Picker(selection: Binding(get: { state.scope }, set: { send(FeedEvent.SetScope(scope: $0)) })) {
            ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                Text(option.label).tag(option.scope)
            }
        } label: {
            Text(FeedTexts.shared.title(state: state))
        }
        .pickerStyle(.inline)
    }

    private var addMenu: some View {
        Menu {
            ForEach(Array(FeedTexts.shared.addChoices(state: state, recordInMenu: true).enumerated()), id: \.offset) { _, choice in
                Button { send(choice.event) } label: {
                    Label { Text(choice.label) } icon: { Image(Self.icon(choice.kind)) }
                }
            }
        } label: {
            Image(.add).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
        }
        .accessibilityLabel(Text(FeedTexts.shared.add()))
    }

    private static func icon(_ kind: FeedAddKind) -> AppIcon {
        switch kind {
        case .Record: .record
        case .Manual: .manualEntry
        case .Import: .import
        default: .add
        }
    }

    // MARK: The list

    @ViewBuilder private var loaded: some View {
        FeedUploadRows(uploads: state.uploads) { send(FeedEvent.OpenUploads.shared) }
        // The pull's own spinner shows a refresh running; a failed one says why here, with Retry.
        RefreshStatusView(state: state.content, errorKind: .LoadFeed, showsProgress: false, retry: refresh).listStatusRow()
        if let error = state.error {
            ErrorMessage(error, layout: .row).listStatusRow()
        }
        if state.askSegments {
            Section { SegmentsPrompt(send: send) }
        }
        if state.askGroups {
            Section { GroupsPrompt(send: send) }
        }
        ForEach(items, id: \.key) { entry in
            Section { row(entry.item) }
        }
        if let empty = FeedTexts.shared.empty(state: state) {
            VStack(spacing: 8) {
                Text(empty).foregroundStyle(.secondary).multilineTextAlignment(.center)
                if FeedTexts.shared.emptyOffersClear(state: state) {
                    AppTextButton(.feedFilterClear) { send(FeedEvent.ClearFilters.shared) }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
            .listStatusRow()
        }
    }

    /// The timeline's items with the keys both UIs give them, leaving out kinds this version can't show.
    private var items: [(key: String, item: Any)] {
        state.items.elements(as: Any.self).compactMap { item in
            guard let item = item as? any TimelineItem, !(item is TimelineItem.Unknown) else { return nil }
            return (state.keyOf(item: item), item)
        }
    }

    @ViewBuilder private func row(_ item: Any) -> some View {
        if let activity = item as? TimelineItem.Activity {
            let uri = activity.view.uri.value
            ActivityCardView(
                content: ActivityCardContent.Companion.shared.of(activity: activity.view, viewerDid: state.viewerDid),
                revealed: state.revealed.contains(key: uri),
                send: { send(FeedEvent.Activity(event: $0)) },
                reveal: { state.revealed.eventSink(uri) }
            )
        } else if let card = item as? TimelineItem.ImportCard {
            FeedImportCard(card: card.card) { send(FeedEvent.OpenImportBatch(card: card.card)) }
        } else if let post = item as? TimelineItem.Post {
            PostCardView(
                content: PostCardContent.Companion.shared.of(post: post.post, viewerDid: state.viewerDid, reason: nil),
                send: { send(FeedEvent.Post(event: $0)) })
        } else if let repost = item as? TimelineItem.Repost {
            PostCardView(
                content: PostCardContent.Companion.shared.of(
                    post: repost.reposted.post, viewerDid: state.viewerDid, reason: repost.reposted.reason),
                send: { send(FeedEvent.Post(event: $0)) })
        }
    }

    private func refresh() {
        send(FeedEvent.Refresh.shared)
    }

    private func send(_ event: any FeedEvent) {
        state.eventSink(event)
    }
}

/// The upload rows over the timeline (data/outbox): the changes the server refused, in red, and what is uploading with the imports'
/// and the server's pauses under it, each opening the upload queue.
struct FeedUploadRows: View {
    let uploads: UploadSummary
    let open: () -> Void
    @Environment(\.clock) private var clock

    var body: some View {
        if let refused = FeedUploadTexts.shared.refused(uploads: uploads) {
            Button(action: open) { Text(refused).foregroundStyle(.red) }
                .buttonStyle(.borderless)
                .listStatusRow()
        }
        if let uploading = FeedUploadTexts.shared.uploading(uploads: uploads) {
            Button(action: open) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(uploading).font(.footnote)
                    if let paused = FeedUploadTexts.shared.importsPaused(uploads: uploads) {
                        Text(paused).font(.footnote).foregroundStyle(.secondary)
                    }
                    if let until = FeedUploadTexts.shared.pausedUntil(uploads: uploads, now: clock.now, zone: clock.kotlinZone) {
                        clock.text(until).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.borderless)
            .listStatusRow()
        }
    }
}

/// An athlete's batch import in the timeline (feed/import-cards): the author line with its months, how many, the sports and distance,
/// and up to four route sketches. No likes or comments; a tap opens the batch, which is also its VoiceOver action.
struct FeedImportCard: View {
    let card: ImportCardView
    let open: () -> Void
    @Environment(\.clock) private var clock
    @Environment(\.stats) private var stats

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Avatar(url: card.author.avatar, name: AppStrings.string(ImportCardTexts.shared.author(card: card)), size: 32)
                    .blur(radius: ImportCardTexts.shared.avatarBlurred(card: card) ? 6 : 0)
                VStack(alignment: .leading, spacing: 2) {
                    Text(ImportCardTexts.shared.author(card: card)).font(.subheadline.weight(.semibold))
                    Text(ImportCardTexts.shared.range(card: card, zone: clock.kotlinZone)).font(.footnote).foregroundStyle(.secondary)
                }
            }
            Text(ImportCardTexts.shared.title(card: card)).font(.headline)
            Text(ImportCardTexts.shared.summary(card: card, units: stats.units)).font(.subheadline)
            let routes = ImportCardTexts.shared.routes(card: card)
            if !routes.isEmpty {
                HStack(spacing: 8) {
                    ForEach(Array(routes.enumerated()), id: \.offset) { _, route in
                        RouteSketch { size in
                            RouteShape.Companion.shared.of(
                                polyline: route.polyline, gaps: route.polylineGaps, width: size.width, height: size.height, pad: 8)
                        }
                        .frame(height: 72)
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    }
                }
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture(perform: open)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: Text(ImportCardTexts.shared.open()), open)
    }
}

/// Asks once whether to turn segment matching on (segments/segment-track); either answer is saved, so it isn't asked again.
struct SegmentsPrompt: View {
    let send: (any FeedEvent) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(.feedSegmentsTitle).font(.headline)
            Text(.feedSegmentsBody).font(.subheadline)
            HStack(spacing: 12) {
                Spacer()
                AppTextButton(.feedSegmentsNotNow) { send(FeedEvent.AnswerSegments(on: false)) }
                AppButton(.feedSegmentsTurnOn) { send(FeedEvent.AnswerSegments(on: true)) }
            }
        }
        .padding(.vertical, 4)
    }
}

/// Asks once whose activities the athlete's may be grouped with (feed/group-activities); any answer is saved, so it isn't asked again.
/// The three answers wrap under one another where they don't fit on a line.
struct GroupsPrompt: View {
    let send: (any FeedEvent) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(.feedGroupsTitle).font(.headline)
            Text(.feedGroupsBody).font(.subheadline)
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 12) {
                    Spacer()
                    answers
                }
                VStack(alignment: .trailing, spacing: 8) { answers }.frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder private var answers: some View {
        AppTextButton(.feedSegmentsNotNow) { send(FeedEvent.AnswerGroups(choice: GroupChoice.OFF)) }
        AppSecondaryButton(.feedGroupsConnected) { send(FeedEvent.AnswerGroups(choice: GroupChoice.CONNECTED)) }
        AppButton(.feedGroupsAnyone) { send(FeedEvent.AnswerGroups(choice: GroupChoice.ANYONE)) }
    }
}

#if DEBUG
    #Preview("Feed") {
        SamplePages(Samples.feed) { state in
            NavigationStack { FeedPage(state: state, filterSample: Samples.feedFilterBar(for: state)).environment(\.tabRoot, true) }
        }
        .environment(\.clock, Samples.clock)
    }

    #Preview("Mine, empty, prompts and pauses") {
        SamplePages(Samples.feedVariants) { state in
            NavigationStack { FeedPage(state: state, filterSample: Samples.feedFilterBar(for: state)).environment(\.tabRoot, true) }
        }
        .environment(\.clock, Samples.clock)
    }
#endif
