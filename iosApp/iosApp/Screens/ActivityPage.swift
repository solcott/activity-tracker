import SwiftUI

/// An activity's page (feed/activity-page-layout), drawn from the states `ActivityDetailPresenter` publishes: the route on the native map
/// (expanding to the enlarged map) and the photos as one mosaic (`MediaMosaicView`), who, when and what, the hero figures, likes and who
/// liked it with the comments in brief in one section (`CommentsPreviewRows`, which open the Comments screen), Together with, the training
/// load, the cards in the sport's order (the lead open with its native charts, zones and figures, the rest as one-row strips that open
/// them, each table's first rows with Show all), the segment efforts and lifts, and a last row opening All data (`ActivityDataView`). The
/// owner's menu and Report on someone else's activity are in the bar (`ActivityPageMenu`), the report sheet over it (`reporting(_:)`).
/// From a regular width of 840 points the mosaic, header and the All data row (or All data itself) sit in a 400-point pane beside the
/// cards, as the shared page lays it out from 840dp; narrower, section chips stay under the bar once the mosaic has scrolled away. Every
/// word and condition is Kotlin's (`ActivityPageTexts`, `FigureTexts`, `CardLines`, `ActivityCardContent`, `MediaMosaic`,
/// `ActivityDataTexts`), shared with the Compose page.
///
/// Named `ActivityPage` because `ActivityView` is the lexicon class it shows. Every chart and table the shared page draws is drawn
/// natively; a combined activity's cards name their sources, and its owner edits the combination in a native sheet
/// (`EditCombinationSheet`).
struct ActivityPage: View {
    let state: ActivityDetailState

    @State private var exporting = false

    var body: some View {
        let page = state.content.data as? ActivityDetailPage
        ContentStateView(state.content, as: ActivityDetailPage.self, errorKind: .LoadActivity, retry: retry) { page in
            ActivityPageBody(page: page, content: state.content, exporting: $exporting, retry: retry)
        }
        // Named for the activity and its sport once it has loaded, so the bar keeps saying what the page is as it scrolls.
        .pageTitle(
            Text(page.map { ActivityPageTexts.shared.heading(activity: $0.activity) } ?? ActivityPageTexts.shared.title),
            subtitle: page.flatMap { ActivityPageTexts.shared.typeLine(activity: $0.activity) }.map { Text($0) }
        ) {
            if let page {
                ActivityPageMenu(page: page, exporting: $exporting)
            }
        }
    }

    private func retry() {
        state.eventSink(ActivityDetailEvent.Retry.shared)
    }
}

/// The loaded page: behind the hidden warning, or laid out by width, with its sheets and the combine prompt.
private struct ActivityPageBody: View {
    let page: ActivityDetailPage
    let content: KotlinContentState
    @Binding var exporting: Bool
    let retry: () -> Void

    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.clock) private var clock
    @State private var openings = CardOpenings()
    @State private var memo = PageMemo()

    var body: some View {
        let texts = ActivityPageTexts.shared
        Group {
            if texts.waitsHidden(page: page) {
                // Opened by URI: lists never show it, so the page waits behind a warning.
                VStack(alignment: .leading, spacing: 8) {
                    Text(texts.hiddenTitle).font(.headline)
                    Text(texts.hiddenMessage).font(.subheadline)
                    AppSecondaryButton(texts.hiddenShow) { page.revealed.eventSink(texts.hiddenKey(page: page)) }
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else {
                GeometryReader { geometry in
                    if sizeClass == .regular && geometry.size.width >= 840 {
                        ActivityPanes(page: page, openings: openings, memo: memo, content: content, retry: retry)
                    } else {
                        ActivityColumn(page: page, openings: openings, memo: memo, content: content, retry: retry)
                    }
                }
            }
        }
        .onChange(of: PageCard.openKey(page, openings: nil)) { openings.settle(page) }
        .reporting(page.reporting)
        .sheet(isPresented: $exporting) {
            if let export = page.export {
                ActivityExportSheet(export: export, send: send)
            }
        }
        // The presenter built the file: offer it, then say it was handed off, whatever came of it. A file that couldn't be written for
        // the share sheet is never offered: the sheet stays open and says so.
        .task(id: page.export?.file?.name) {
            guard let file = page.export?.file else { return }
            if await ActivityExportHandOff.share(file) {
                exporting = false
                send(ActivityDetailEvent.ExportHandedOff.shared)
            } else {
                send(ActivityDetailEvent.ExportShareFailed.shared)
            }
        }
        .alert(
            Text(page.combineSuggestion.map { texts.combineCue(cue: $0.cue) } ?? texts.title),
            isPresented: .shown(page.combineSuggestion?.asPrompt == true) { send(ActivityDetailEvent.NotNowSuggestion.shared) }
        ) {
            Button {
                send(ActivityDetailEvent.CombineSuggested.shared)
            } label: {
                Text(texts.combine)
            }
            Button(role: .cancel) {
                send(ActivityDetailEvent.NotNowSuggestion.shared)
            } label: {
                Text(texts.notNow)
            }
        } message: {
            if let suggestion = page.combineSuggestion {
                clock.text(texts.combineMessage(suggestion: suggestion, now: clock.now, zone: clock.kotlinZone))
            }
        }
    }

    private func send(_ event: any ActivityDetailEvent) {
        page.eventSink(event)
    }
}

/// Under 840 points, or a compact width: one list, the shared page's compact order, the mosaic first. Once the mosaic has scrolled
/// away, the page's section chips (`ActivityPageTexts.jumps`) stay under the bar; a chip scrolls to its section, opening the strip it
/// names, and the chip of the first section on screen is tinted.
private struct ActivityColumn: View {
    let page: ActivityDetailPage
    let openings: CardOpenings
    let memo: PageMemo
    let content: KotlinContentState
    let retry: () -> Void

    @State private var pinned = false
    @State private var visible: Set<String> = []
    /// The section a chip asked for, until the list has scrolled it on screen.
    @State private var pending: String?
    /// The section a chip last scrolled to, its chip selected until the reader scrolls themselves: the sections above it may still
    /// be on screen under the bar.
    @State private var jumped: String?

    var body: some View {
        let sections = ActivitySections(page: page, openings: openings, memo: memo) { key, seen in
            if seen { visible.insert(key) } else { visible.remove(key) }
        }
        let jumps = ActivityPageTexts.shared.jumps(page: page)
        // The chips show once the mosaic (or, without one, the header) has gone under the bar.
        let overview: CGFloat = sections.hasMosaic ? MediaMosaicLayout.height + 24 : 120
        ScrollViewReader { proxy in
            List {
                RefreshStatusView(state: content, errorKind: .LoadActivity, retry: retry).listStatusRow()
                sections.media
                sections.header(overview: !sections.hasMosaic)
                sections.hero
                sections.social
                sections.group
                sections.trainingLoad
                sections.cards(inPane: false)
                sections.segments
                sections.allData(inPane: false)
            }
            .listStyle(.insetGrouped)
            .cardSpacing()
            .cardMotion(PageCard.openKey(page, openings: openings))
            // Tried again while an opening card moves the rows, since a list scrolls only to a row it has placed.
            .task(id: pending) {
                guard let key = pending else { return }
                for _ in 0..<40 {
                    withAnimation { proxy.scrollTo(key, anchor: .top) }
                    try? await Task.sleep(for: .milliseconds(100))
                    if visible.contains(key) { break }
                }
                pending = nil
            }
            .onScrollPhaseChange { _, phase in
                if phase == .interacting { jumped = nil }
            }
            .onScrollGeometryChange(for: Bool.self) { geometry in
                geometry.contentOffset.y + geometry.contentInsets.top > overview
            } action: { _, past in
                withAnimation(.easeInOut(duration: 0.2)) { pinned = past }
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                if pinned && jumps.count > 1 {
                    let selected = jumps.firstIndex { $0.key == jumped } ?? jumps.firstIndex { visible.contains($0.key) }
                    SectionChips(labels: jumps.map(\.label), selected: selected) { index in
                        jump(jumps[index])
                    }
                    .background(.bar)
                    .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
        }
    }

    /// Scrolls to `jump`'s section, first opening the strip it names: `pending` until its row is on screen.
    private func jump(_ jump: PageJump) {
        if let section = jump.card,
            let card = page.page.cards.elements(as: MetricCard.self).first(where: { $0.section == section }),
            !openings.isOpen(card, on: page)
        {
            openings.toggle(card, on: page)
        }
        pending = jump.key
        jumped = jump.key
    }
}

/// From 840 points at a regular width: a 400-point pane with the mosaic, header, hero, likes and comments, group, training load and the
/// All data row, or All data itself once opened there, each scrolling on its own, beside the cards, segments and lifts.
private struct ActivityPanes: View {
    let page: ActivityDetailPage
    let openings: CardOpenings
    let memo: PageMemo
    let content: KotlinContentState
    let retry: () -> Void

    var body: some View {
        let sections = ActivitySections(page: page, openings: openings, memo: memo)
        HStack(spacing: 0) {
            Group {
                if page.allData != nil {
                    ActivityDataList(data: page.data, openGear: { page.eventSink(ActivityDetailEvent.OpenGear(uri: $0)) }) {
                        EmptyView()
                    } header: {
                        PageBar(
                            title: Text(ActivityDataTexts.shared.title),
                            actions: AnyView(
                                AppIconButton(.close, label: ActivityDataTexts.shared.close) {
                                    page.eventSink(ActivityDetailEvent.CloseAllData.shared)
                                })
                        )
                    }
                    // Afresh for each section it opens at.
                    .id(page.allData?.description)
                } else {
                    List {
                        RefreshStatusView(state: content, errorKind: .LoadActivity, retry: retry).listStatusRow()
                        sections.media
                        sections.header(overview: false)
                        sections.hero
                        sections.social
                        sections.group
                        sections.trainingLoad
                        sections.allData(inPane: true)
                    }
                    .listStyle(.insetGrouped)
                    .cardSpacing()
                }
            }
            .frame(width: 400)
            Divider()
            List {
                sections.cards(inPane: true)
                sections.segments
            }
            .listStyle(.insetGrouped)
            .cardSpacing()
            .cardMotion(PageCard.openKey(page, openings: openings))
            .frame(maxWidth: 1_040)
        }
        .frame(maxWidth: .infinity)
    }
}

extension View {
    /// Animates a card opening or closing (feed/activity-page-layout): its row grows or shrinks in place and the rows after it move to
    /// their new places, as the shared page's cards do. With Reduce Motion the change is immediate.
    func cardMotion(_ open: String) -> some View {
        modifier(CardMotion(open: open))
    }
}

private struct CardMotion: ViewModifier {
    let open: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content.animation(reduceMotion ? nil : .snappy(duration: 0.35), value: open)
    }
}

/// What the page draws that Kotlin takes a while to make, made once per route and activity rather than on every update: the route smoothed
/// for the map, and the activity's card content (which smooths it too). A card opening updates the page twice, once for the tap and once
/// for the presenter's state, and smoothing a long route each time held the main thread through the card's motion.
final class PageMemo {
    private var route: (key: RouteKey?, map: RouteMap?)?
    private var content: (activity: ActivityView, viewer: String?, card: ActivityCardContent)?

    /// A route by what it draws, since each state carries a new `ShownRoute` of the same line.
    private struct RouteKey: Equatable {
        let polyline: String
        let gaps: [Int32]?
        let ends: RouteEnds?

        init(_ route: ShownRoute) {
            polyline = route.polyline
            gaps = route.gaps?.elements(as: Int32.self)
            ends = route.ends
        }

        static func == (a: RouteKey, b: RouteKey) -> Bool { a.polyline == b.polyline && a.gaps == b.gaps && a.ends === b.ends }
    }

    func routeMap(_ page: ActivityDetailPage) -> RouteMap? {
        let key = page.route.map(RouteKey.init)
        if let route, route.key == key { return route.map }
        let map = ActivityPageTexts.shared.routeMap(page: page)
        route = (key, map)
        return map
    }

    func card(_ page: ActivityDetailPage) -> ActivityCardContent {
        let viewer = page.viewerDid.value
        if let content, content.activity === page.activity, content.viewer == viewer { return content.card }
        let card = ActivityCardContent.Companion.shared.of(activity: page.activity, viewerDid: viewer)
        content = (page.activity, viewer, card)
        return card
    }
}

/// The cards a tap has just opened or closed, shown so at once, in the same update as the tap, while the presenter's state follows a few
/// frames later; each is dropped once the state agrees, so the state stays what decides.
@Observable
final class CardOpenings {
    private var shown: [String: Bool] = [:]

    func isOpen(_ card: MetricCard, on page: ActivityDetailPage) -> Bool {
        shown[card.section.description] ?? page.isOpen(card: card)
    }

    /// Opens or closes `card` now, and asks the presenter to.
    func toggle(_ card: MetricCard, on page: ActivityDetailPage) {
        shown[card.section.description] = !isOpen(card, on: page)
        page.eventSink(ActivityDetailEvent.ToggleCard(section: card.section))
    }

    /// Forgets what `page`'s state now agrees with.
    func settle(_ page: ActivityDetailPage) {
        let cards = page.page.cards.elements(as: MetricCard.self)
        shown = shown.filter { key, open in cards.first { $0.section.description == key }.map { page.isOpen(card: $0) != open } ?? false }
    }
}

/// The page's cards in the sport's order, each in a section of its own keyed by its card (`PageJump.card`), so opening or closing one
/// changes rows inside its section rather than moving them between sections; "More charts" heads the first closed card after an open one.
private struct PageCard {
    let card: MetricCard
    let open: Bool
    let heading: Bool

    var id: String { PageJump.Companion.shared.card(section: card.section) }

    /// Which cards show open, as one value the list animates its changes on; with no `openings`, which the presenter has open.
    static func openKey(_ page: ActivityDetailPage, openings: CardOpenings?) -> String {
        page.page.cards.elements(as: MetricCard.self)
            .filter { openings?.isOpen($0, on: page) ?? page.isOpen(card: $0) }
            .map { $0.section.description }
            .joined(separator: ",")
    }

    static func of(_ page: ActivityDetailPage, openings: CardOpenings) -> [PageCard] {
        var sawOpen = false
        var headed = false
        return page.page.cards.elements(as: MetricCard.self).map { card in
            let open = openings.isOpen(card, on: page)
            let heading = !open && sawOpen && !headed
            headed = headed || heading
            sawOpen = sawOpen || open
            return PageCard(card: card, open: open, heading: heading)
        }
    }
}

/// The page's sections, each placed by the layout above. `seen` hears which of the chips' sections are on screen.
private struct ActivitySections {
    let page: ActivityDetailPage
    let openings: CardOpenings
    let memo: PageMemo
    var seen: ((String, Bool) -> Void)?

    private var texts: ActivityPageTexts { ActivityPageTexts.shared }

    private func send(_ event: any ActivityDetailEvent) {
        page.eventSink(event)
    }

    private func anchor(_ key: String) -> PageAnchor {
        PageAnchor(key: key) { seen?(key, $0) }
    }

    private var card: ActivityCardContent { memo.card(page) }

    private var mosaic: MediaMosaic? {
        MediaMosaic.Companion.shared.of(
            hasRoute: memo.routeMap(page) != nil, photoCount: Int32(card.media.elements(as: MediaPhoto.self).count))
    }

    var hasMosaic: Bool { mosaic != nil }

    /// Who and when; the overview's mark when there's no mosaic to carry it.
    func header(overview: Bool) -> some View {
        Section { ActivityHeader(page: page, card: card).anchored(overview ? anchor(PageJump.Companion.shared.OVERVIEW) : nil) }
    }

    /// The route and photos as one mosaic, with the map's lock under it.
    @ViewBuilder var media: some View {
        if let mosaic {
            let card = card
            let map = memo.routeMap(page)
            let lock = texts.mapLock(page: page)
            Section {
                MediaMosaicView(
                    mosaic: mosaic, content: card, revealed: page.revealed.contains(key: page.activity.uri.value),
                    reveal: { page.revealed.eventSink(page.activity.uri.value) },
                    open: { send(ActivityDetailEvent.OpenMedia(index: $0)) }
                ) {
                    if let map {
                        RouteMapView(map: map, height: MediaMosaicLayout.height, expand: { send(ActivityDetailEvent.OpenMap.shared) })
                    }
                }
                // Marked before the row's insets and background, which an id would otherwise keep from reaching the list.
                .anchored(anchor(PageJump.Companion.shared.OVERVIEW))
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            } footer: {
                if let lock {
                    HStack(spacing: 4) {
                        Image(.lock)
                        Text(lock.text)
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(lock.spoken))
                }
            }
        }
    }

    @ViewBuilder var hero: some View {
        let figures = page.page.hero.elements(as: Figure.self)
        if !figures.isEmpty {
            Section { HeroFigures(figures: figures).padding(.vertical, 4) }
        }
    }

    /// Everything social about the activity in one section: its counts and likers, then its comments in brief.
    var social: some View {
        Section {
            ActivityLikes(page: page)
            CommentsPreviewRows(preview: page.comments)
        }
    }

    @ViewBuilder var group: some View {
        if page.showsGroup {
            ActivityTogether(page: page)
        }
    }

    /// A group's efforts; the first group's first row marks the Segments section for its chip.
    private func effortGroup(_ group: SegmentEffortGroup, first: Bool) -> some View {
        let rows: [SegmentEffortRow] = group.rows.elements(as: SegmentEffortRow.self)
        // Every bar in a group starts past its deepest indent, so the bars share one axis and their spans line up.
        let deepest: Int = rows.map { Int($0.depth) }.max() ?? 0
        let barIndent: CGFloat = CGFloat(deepest) * EffortGuides.indent
        return ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
            effortRow(row, barIndent: barIndent, first: first && index == 0)
        }
    }

    /// An effort's row, opening its segment.
    private func effortRow(_ row: SegmentEffortRow, barIndent: CGFloat, first: Bool) -> some View {
        let mark: PageAnchor? = first ? anchor(PageJump.Companion.shared.SEGMENTS) : nil
        let open: () -> Void = { send(texts.openEffort(effort: row.effort)) }
        return SegmentEffortRowView(row: row, barIndent: barIndent, anchor: mark, open: open)
    }

    var trainingLoad: some View {
        TrainingLoadSection(figures: page.page.trainingLoad.elements(as: Figure.self))
    }

    /// The cards in the sport's order, each in its own section: its header row, and once open the rest of it (a table's first rows,
    /// then Show all). A closed card is a strip that opens it.
    @ViewBuilder func cards(inPane: Bool) -> some View {
        if let charts = page.page.charts {
            ForEach(PageCard.of(page, openings: openings), id: \.id) { item in
                cardSection(item, charts: charts, inPane: inPane)
            }
        }
    }

    private func cardSection(_ item: PageCard, charts: ActivityCharts, inPane: Bool) -> some View {
        let card = item.card
        let section = card.section
        let toggle: (() -> Void)? = DetailPackage.getIsTable(section) ? nil : { openings.toggle(card, on: page) }
        let table: DataSection? = DataSection.Companion.shared.of(section: section)
        let openAll: () -> Void = { send(ActivityDetailEvent.OpenAllData(section: table, inPane: inPane)) }
        return Section {
            CardHeaderRow(card: card, charts: charts, open: item.open, toggle: toggle)
                .anchored(anchor(item.id))
            if item.open {
                MetricCardRows(
                    card: card, charts: charts, missingZones: page.chartsMissingZones,
                    caption: texts.sourceCaption(page: page, section: section),
                    shownHeadline: 2,
                    maxRows: Int(DetailPackage.TABLE_PREVIEW_ROWS),
                    showAll: ActivityDataTexts.shared.showAll(section: section, charts: charts),
                    openAll: openAll,
                    setThresholds: { send(ActivityDetailEvent.OpenTrainingZones.shared) })
            }
        } header: {
            if item.heading { Text(texts.moreCharts).transition(.opacity) }
        }
    }

    /// The page's last row, opening All data: in the side pane on a wide window (`inPane`), else as its own page.
    @ViewBuilder func allData(inPane: Bool) -> some View {
        let data = page.data
        if ActivityDataTexts.shared.offered(data: data) {
            Section {
                AppListRow(Text(ActivityDataTexts.shared.rowTitle), summary: Text(ActivityDataTexts.shared.rowSummary(data: data))) {
                    send(ActivityDetailEvent.OpenAllData(section: nil, inPane: inPane))
                }
                .anchored(anchor(PageJump.Companion.shared.ALL_DATA))
            }
        }
    }

    @ViewBuilder var segments: some View {
        let groups = page.segments.elements(as: SegmentEffortGroup.self)
        if !groups.isEmpty {
            Section {
                ForEach(Array(groups.enumerated()), id: \.offset) { groupIndex, group in
                    effortGroup(group, first: groupIndex == 0)
                }
            } header: {
                Text(texts.segmentsHeading)
            }
        }
        let lifts = page.lifts.elements(as: LiftRides.self)
        if !lifts.isEmpty {
            Section {
                ForEach(Array(lifts.enumerated()), id: \.offset) { _, lift in
                    Button {
                        send(texts.openLift(lift: lift))
                    } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(verbatim: lift.name)
                                Text(texts.liftRides(lift: lift)).font(.footnote).foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            Text(verbatim: texts.liftTime(lift: lift)).fontWeight(.medium).monospacedDigit()
                        }
                        .foregroundStyle(.primary)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isButton)
                }
            } header: {
                Text(texts.liftsHeading)
            }
        }
    }

}

/// Who, when and what: the labels' notices, the author with the tier's badge, the start (with its lock), the title and type, the dive
/// site, the description, the other recordings and members, the combine banner, and what an action reported.
private struct ActivityHeader: View {
    let page: ActivityDetailPage
    let card: ActivityCardContent

    @Environment(\.clock) private var clock
    @Environment(\.stats) private var stats
    /// The owner's combination sheet is open; a split that leaves nothing combined closes it.
    @State private var editing = false

    var body: some View {
        let texts = ActivityPageTexts.shared
        let activity = page.activity
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(card.notices().enumerated()), id: \.offset) { _, notice in
                NoticeChip(notice: notice)
            }
            HStack(spacing: 8) {
                AppTextButton(texts.author(activity: activity)) {
                    send(ActivityDetailEvent.OpenAuthor.shared)
                }
                .font(.subheadline.weight(.semibold))
                if let badge = card.badge() {
                    Text(badge)
                        .font(.caption2)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color(.secondarySystemFill), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        .accessibilityLabel(card.badgeDescription().map { Text($0) } ?? Text(badge))
                }
            }
            if let start = page.page.start {
                HStack(spacing: 8) {
                    Text(verbatim: FigureValueText.string(start.value, units: stats.units, clock: clock)).font(.footnote).foregroundStyle(
                        .secondary)
                    MarkBadge(mark: start.mark)
                }
            }
            Text(texts.heading(activity: activity)).font(.title2.weight(.semibold))
            if let type = texts.typeLine(activity: activity) {
                Label {
                    Text(type)
                } icon: {
                    SportIcon(card.type)
                }
                .font(.subheadline)
                .foregroundStyle(.tint)
            }
            if let site = texts.diveSite(activity: activity) {
                if texts.opensDiveSite(activity: activity) {
                    Button {
                        send(ActivityDetailEvent.OpenDiveSite.shared)
                    } label: {
                        Label {
                            Text(site)
                        } icon: {
                            Image(.location)
                        }
                    }
                    .buttonStyle(.borderless)
                    .font(.subheadline)
                } else {
                    Label {
                        Text(site)
                    } icon: {
                        Image(.location)
                    }.font(.subheadline).foregroundStyle(.secondary)
                }
            }
            if let about = card.descriptionText() {
                Text(verbatim: about).font(.body).padding(.top, 2)
            }
            recordings
            members
            if let suggestion = page.combineSuggestion, !suggestion.asPrompt {
                CombineBanner(suggestion: suggestion, send: send)
            }
            if let error = page.manage?.error { ErrorMessage(error, layout: .row) }
            if let error = page.routeError { ErrorMessage(error, layout: .row) }
            ForEach(Array(texts.notices(page: page).enumerated()), id: \.offset) { _, notice in
                Text(notice).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
        .sheet(isPresented: Binding(get: { editing && CombinationTexts.shared.offersEdit(page: page) }, set: { editing = $0 })) {
            EditCombinationSheet(page: page)
        }
    }

    /// The other devices that recorded this activity, each opening its recording; or, on a recording, the activity it records.
    @ViewBuilder private var recordings: some View {
        let texts = RecordingTexts.shared
        let activity = page.activity
        if let recordingOf = activity.record.recordingOf {
            Text(texts.recordingOf).font(.subheadline)
            AppTextButton(texts.openRecorded) {
                send(ActivityDetailEvent.OpenActivity(uri: recordingOf))
            }
        } else if let recordings = activity.recordings, !recordings.isEmpty {
            Text(texts.recordings).font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(Array(recordings.enumerated()), id: \.offset) { _, recording in
                AppTextButton(texts.recordingName(recording: recording, activityStart: activity.record.startedAt)) {
                    send(ActivityDetailEvent.OpenActivity(uri: recording.uri))
                }
            }
        }
    }

    /// A combined activity's members, each opening its page; or, on a member, the combined activity it's part of.
    @ViewBuilder private var members: some View {
        let texts = RecordingTexts.shared
        let activity = page.activity
        if let combinedInto = activity.combinedInto {
            Text(texts.memberOf).font(.subheadline)
            AppTextButton(texts.openCombined) {
                send(ActivityDetailEvent.OpenActivity(uri: combinedInto))
            }
        } else if let members = activity.members, !members.isEmpty {
            HStack {
                Text(texts.members).font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
                Spacer(minLength: 8)
                if CombinationTexts.shared.offersEdit(page: page) {
                    AppTextButton(CombinationTexts.shared.edit) {
                        editing = true
                    }
                    .font(.footnote)
                    .accessibilityLabel(Text(CombinationTexts.shared.editDescription))
                }
            }
            ForEach(Array(members.enumerated()), id: \.offset) { _, member in
                AppTextButton(texts.memberName(member: member)) {
                    send(ActivityDetailEvent.OpenActivity(uri: member.uri))
                }
            }
        }
    }

    private func send(_ event: any ActivityDetailEvent) {
        page.eventSink(event)
    }
}

/// On the owner's page until they decide: Combine, or Don't combine, which stops suggesting the pair.
private struct CombineBanner: View {
    let suggestion: CombineSuggestionState
    let send: (any ActivityDetailEvent) -> Void

    @Environment(\.clock) private var clock

    var body: some View {
        let texts = ActivityPageTexts.shared
        VStack(alignment: .leading, spacing: 8) {
            Text(texts.combineCue(cue: suggestion.cue)).font(.subheadline.weight(.semibold))
            clock.text(texts.combineMessage(suggestion: suggestion, now: clock.now, zone: clock.kotlinZone)).font(.subheadline)
            if let error = suggestion.error { ErrorMessage(error, layout: .row) }
            HStack(spacing: 12) {
                AppButton(texts.combine) { send(ActivityDetailEvent.CombineSuggested.shared) }
                    .disabled(suggestion.busy)
                AppTextButton(texts.dontCombine) {
                    send(ActivityDetailEvent.DontCombineSuggestion.shared)
                }
                .disabled(suggestion.busy)
            }
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Color(.separator), lineWidth: 0.5))
        .padding(.top, 4)
    }
}

/// The counts row (the like count, after the like button on someone else's, which shows or hides who liked it, and the comment count),
/// why a like couldn't be given, and who liked it once shown.
private struct ActivityLikes: View {
    let page: ActivityDetailPage

    var body: some View {
        let texts = ActivityPageTexts.shared
        let toggles = texts.showsLikesToggle(page: page)
        SocialCountsRow {
            SocialCount(
                icon: .like,
                count: texts.likeNumber(activity: page.activity),
                label: texts.likeCount(activity: page.activity),
                actionLabel: texts.likesToggle(shown: page.likesList != nil),
                action: toggles ? { send(ActivityDetailEvent.ToggleLikesList.shared) } : nil,
                filled: page.activity.viewer?.like != nil,
                iconLabel: texts.like,
                iconAction: page.isOwn ? nil : { send(ActivityDetailEvent.ToggleLike.shared) }
            )
            CommentCount(preview: page.comments)
        }
        if let error = page.likeError {
            ErrorMessage(error, layout: .row)
        }
        if let error = page.blockError {
            ErrorMessage(error, layout: .row)
        }
        if let likers = page.likesList {
            LikersList(list: likers) { send(ActivityDetailEvent.OpenProfile(did: $0.did)) }
        }
    }

    private func send(_ event: any ActivityDetailEvent) {
        page.eventSink(event)
    }
}

/// The athletes it was done together with, each opening their activity, and on the owner's page Leave or Rejoin group, Add people and,
/// on their copy of another's activity, Leave group.
private struct ActivityTogether: View {
    let page: ActivityDetailPage

    var body: some View {
        let texts = ActivityPageTexts.shared
        Section {
            ForEach(Array(page.partners.elements(as: GroupPartner.self).enumerated()), id: \.offset) { _, partner in
                let name = AppPackage.getShownName(partner.author)
                AppItemRow(Text(verbatim: name), opens: { send(ActivityDetailEvent.OpenActivity(uri: partner.uri)) }) {
                    Avatar(url: partner.author.avatar, name: name, size: 32)
                }
            }
            if let own = page.ownGroup {
                if own.left { Text(texts.groupLeft).font(.footnote).foregroundStyle(.secondary) }
                Button {
                    send(texts.groupEvent(own: own))
                } label: {
                    Text(texts.groupButton(own: own))
                }
                .disabled(own.busy)
                if let error = own.error { ErrorMessage(error, layout: .row) }
            }
            if page.isOwnCopy {
                Button {
                    send(ActivityDetailEvent.LeaveCopyGroup.shared)
                } label: {
                    Text(texts.leaveCopyGroup)
                }
                .disabled(page.leavingCopyGroup)
                if let error = page.copyGroupError { ErrorMessage(error, layout: .row) }
            }
            if page.canAddPeople {
                Button {
                    send(ActivityDetailEvent.AddPeople.shared)
                } label: {
                    Text(texts.addPeople)
                }
            }
        } header: {
            if !page.partners.elements(as: GroupPartner.self).isEmpty { Text(texts.togetherWith) }
        }
    }

    private func send(_ event: any ActivityDetailEvent) {
        page.eventSink(event)
    }
}

/// One of the activity's efforts: its segment's name, indented under the effort it's nested in and joined to it by guide lines, why it
/// isn't ranked and e-bike under it, its rank and PR, and its time, with a bar under it marking its span within its group when the group
/// has more than one; a tap opens its segment. Its words are Kotlin's (`EffortTexts`), as the shared row's are.
private struct SegmentEffortRowView: View {
    let row: SegmentEffortRow
    let barIndent: CGFloat
    /// The Segments chip's mark, on the first row.
    var anchor: PageAnchor?
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            VStack(alignment: .leading, spacing: 8) {
                EffortLine(effort: row.effort, label: Text(verbatim: row.label))
                    .padding(.leading, CGFloat(row.depth) * EffortGuides.indent)
                if let start = row.spanStart, let end = row.spanEnd {
                    EffortSpanBar(start: CGFloat(start), end: CGFloat(end))
                        .padding(.leading, barIndent)
                }
            }
            .padding(.vertical, EffortGuides.padding)
            .background(alignment: .topLeading) { EffortGuides(row: row) }
            .foregroundStyle(.primary)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .anchored(anchor)
        .listRowInsets(EdgeInsets(top: 0, leading: 20, bottom: 0, trailing: 20))
        // A nested effort's guides run on from the row above, so no line parts them.
        .listRowSeparator(row.depth > 0 ? .hidden : .automatic, edges: .top)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

/// A nested effort's guide lines in its indent, as the shared card draws them: each ancestor's that continues past it as a full-height
/// line, and its own from the top turning to meet its name, ending there when it's the last nested under its parent.
struct EffortGuides: View {
    let row: SegmentEffortRow

    /// How far each depth indents, with its guide line down the middle.
    static let indent: CGFloat = 20
    /// The row's padding above its name.
    static let padding: CGFloat = 10

    @ScaledMetric(relativeTo: .body) private var lineHeight: CGFloat = 22

    var body: some View {
        if row.depth > 0 {
            let guides = (0..<row.guideCount).map { row.guideAt(index: $0) }
            let depth = Int(row.depth)
            let meet = Self.padding + lineHeight / 2
            let last = row.lastChild
            Canvas { context, size in
                var path = Path()
                for (i, continues) in guides.enumerated() where continues {
                    let x = Self.indent * CGFloat(i) + Self.indent / 2
                    path.move(to: CGPoint(x: x, y: 0))
                    path.addLine(to: CGPoint(x: x, y: size.height))
                }
                let own = Self.indent * CGFloat(depth - 1) + Self.indent / 2
                path.move(to: CGPoint(x: own, y: 0))
                path.addLine(to: CGPoint(x: own, y: last ? meet : size.height))
                path.move(to: CGPoint(x: own, y: meet))
                path.addLine(to: CGPoint(x: Self.indent * CGFloat(depth) - 2, y: meet))
                context.stroke(path, with: .color(Color(.separator)), lineWidth: 1)
            }
            .environment(\.layoutDirection, .leftToRight)
            .flipsForRightToLeftLayoutDirection(true)
            .accessibilityHidden(true)
        }
    }
}

/// A thin track with the stretch from `start` to `end` (fractions of its width) filled.
struct EffortSpanBar: View {
    let start: CGFloat
    let end: CGFloat

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            Capsule().fill(Color(.secondarySystemFill))
                .overlay(alignment: .leading) {
                    Capsule().fill(Color.accentColor)
                        .frame(width: max(geometry.size.height, width * (end - start)))
                        .offset(x: width * start)
                }
        }
        .frame(height: 4)
        .clipShape(Capsule())
        .accessibilityHidden(true)
    }
}

#if DEBUG
    #Preview("Activity") {
        SamplePages(Samples.activityDetail) { state in NavigationStack { ActivityPage(state: state) } }
            .environment(\.clock, Samples.clock)
    }

    #Preview("Activity, its mosaics") {
        SamplePages(Samples.activityDetailMosaics) { state in NavigationStack { ActivityPage(state: state) } }
            .environment(\.clock, Samples.clock)
    }

    #Preview("Activity, beyond loading") {
        SamplePages(Samples.activityDetailVariants) { state in NavigationStack { ActivityPage(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
