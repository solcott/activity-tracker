import SwiftUI

/// A segment's page (segments/segment-page, segments/segment-efforts, segments/segment-elevation, segments/segment-names,
/// segments/segment-mayors, segments/segment-leaderboards), drawn from the states `SegmentPresenter` publishes, in the shared page's order:
/// Star in the bar, its line on the native route map (expanding to the enlarged map), the heading (what it is and its status, its name,
/// "originally" its old one, the trails it lies on), the segment group's choices, the figure row, the native elevation chart
/// (`LineChartView`) headed by its high and low points, the Leaderboard and Mayor cards for an active run, its Trail details in their
/// cards (`TrailDetailsSection`), the names (`PlaceNamesSection`, not for a lift), the viewer's efforts, then who created it, its data's
/// credit and licence, and the status of a segment that isn't active with Open the original. Every word and condition is Kotlin's (`SegmentPageTexts`,
/// `EffortTexts`, `PlaceTexts`, `SegmentState`'s getters), shared with the Compose page. The place-name sheet and its delete confirmation
/// show over it (`placeNames(_:)`).
///
/// Named `SegmentPage` because `SegmentView` is the lexicon class it shows.
struct SegmentPage: View {
    let state: SegmentState
    @Environment(\.stats) private var stats

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadSegment, retry: retry) {
            // The segment is read from the typed getter, never from the content's erased value.
            if let segment = state.segment {
                loaded(segment)
            }
        }
        .pageTitle(Text(verbatim: state.segment?.currentName ?? "")) {
            if state.segment != nil, let starred = state.starred {
                AppIconButton(.star, label: SegmentPageTexts.shared.star(starred: starred), filled: starred) {
                    send(SegmentEvent.ToggleStar.shared)
                }
            }
        }
        .placeNames(state.names)
        .trailSheets(state.trail)
    }

    private func loaded(_ segment: SegmentView) -> some View {
        let texts = SegmentPageTexts.shared
        let lift = state.isLift
        // The parts in the order segments/segment-page gives them, as the shared page lays them out.
        return List {
            RefreshStatusView(state: state.content, errorKind: .LoadSegment, showsProgress: state.showsRefreshProgress, retry: retry)
                .listStatusRow()
            if let map = state.routeMap {
                Section {
                    RouteMapView(map: map, expand: { send(SegmentEvent.OpenMap.shared) }).listRowInsets(EdgeInsets())
                }
            }
            Section {
                SegmentHeading(state: state, segment: segment) { send(SegmentEvent.OpenTrail(uri: $0)) }
            }
            if let group = state.group, group.offered {
                Section {
                    GroupChoices(group: group, send: send)
                }
            }
            Section {
                SegmentFigures(figures: state.figures(units: stats.units))
            }
            if let profile = state.profile, let chart = state.chart(units: stats.units) {
                Section {
                    LineChartView(chart: chart).padding(.vertical, 4)
                } header: {
                    Text(texts.extremes(profile: profile, units: stats.units))
                }
            }
            if state.showsBoards {
                Section {
                    SegmentBoards(state: state, send: send)
                }
            }
            // After a recent ride, the details riders check (segments/trail-detail-suggestions).
            if let check = state.trail.quickCheck {
                QuickCheckSection(check: check, send: state.trail.eventSink)
            }
            TrailDetailsSection(
                groups: state.trailGroups(), lastConfirmed: state.trailLastConfirmed,
                open: { state.trail.eventSink(TrailEvent.Open(key: $0)) })
            if state.invitesTrailDetails || state.offersMissingDetail {
                MissingDetailsSection(fields: state.trail, invite: state.invitesTrailDetails)
            }
            if !lift {
                PlaceNamesSection(
                    fields: state.names, proposals: segment.nameProposals, history: segment.nameHistory,
                    canPropose: state.canPropose, canVote: state.canVote)
            }
            Section {
                let efforts = state.efforts.elements(as: EffortView.self)
                if efforts.isEmpty {
                    Text(texts.noEfforts).foregroundStyle(.secondary)
                }
                ForEach(efforts, id: \.self) { effort in
                    EffortRowView(effort: effort) { send(SegmentEvent.OpenActivity(uri: effort.activity)) }
                }
            } header: {
                Text(texts.efforts(lift: lift))
            }
            Section {
                SegmentFooter(state: state, segment: segment, openOriginal: { send(SegmentEvent.OpenOriginal.shared) })
            }
        }
    }

    private func retry() {
        send(SegmentEvent.Retry.shared)
    }

    private func send(_ event: any SegmentEvent) {
        state.eventSink(event)
    }
}

/// The segment group's sport groups as a menu chip and the shown group's directions as a segmented choice (segments/segment-groups); choosing one
/// sends the event Kotlin builds, which replaces the page with that segment's. A one-way sport group's one direction is a disabled choice.
private struct GroupChoices: View {
    let group: GroupSwitcher
    let send: (any SegmentEvent) -> Void

    var body: some View {
        let directions = group.directions
        VStack(alignment: .leading, spacing: 8) {
            if group.showsSports {
                AppMenuChip(
                    nil, filled: true,
                    selection: Binding(
                        get: { group.currentSport }, set: { sport in if let event = group.sportEvent(sport: sport) { send(event) } }),
                    options: group.sports,
                    optionLabel: { AppStrings.string(GroupSwitcherTexts.shared.sport(sport: $0)) })
            }
            if group.oneWay, let direction = directions.first {
                // A one-way sport group's one direction (segments/segment-direction): the choice, shown but not changeable.
                Picker(selection: .constant(0)) {
                    Text(GroupSwitcherTexts.shared.oneWay(direction: direction)).tag(0)
                } label: {
                    EmptyView()
                }
                .pickerStyle(.segmented)
                .disabled(true)
            } else if directions.count > 1 {
                Picker(
                    selection: Binding(
                        get: { directions.firstIndex { group.isCurrent(direction: $0) } ?? 0 },
                        set: { index in if let event = group.directionEvent(direction: directions[index]) { send(event) } }
                    )
                ) {
                    ForEach(directions.indices, id: \.self) { index in
                        Text(GroupSwitcherTexts.shared.direction(direction: directions[index])).tag(index)
                    }
                } label: {
                    EmptyView()
                }
                .pickerStyle(.segmented)
            }
        }
    }
}

/// The heading (segments/segment-page): what it is and its status with a ski segment's icon, its current name, "originally" its old one,
/// and "on" the trails it lies on, each opening its Trail page.
private struct SegmentHeading: View {
    let state: SegmentState
    let segment: SegmentView
    let openTrail: (AtUri) -> Void

    var body: some View {
        let texts = SegmentPageTexts.shared
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                if let icon = SegmentTexts.shared.kindIcon(sport: segment.record.sport, kind: segment.record.kind) {
                    SegmentKindImage(name: icon)
                }
                Text(texts.heading(segment: segment)).font(.subheadline).foregroundStyle(.secondary)
            }
            Text(verbatim: segment.currentName).font(.title2.weight(.bold))
            if let original = state.originalName {
                Text(PlaceNameTexts.shared.originally(original: original)).font(.subheadline).foregroundStyle(.secondary)
            }
            let trails = state.shownTrails
            if !trails.isEmpty {
                FlowRow(spacing: 6, lineSpacing: 4) {
                    Text(texts.onTrails).font(.subheadline)
                    ForEach(trails, id: \.self) { trail in
                        Button {
                            openTrail(trail.uri)
                        } label: {
                            Text(verbatim: trail.name).font(.subheadline.weight(.medium))
                        }
                        .buttonStyle(.borderless)
                    }
                }
            }
        }
        .padding(.vertical, 2)
    }
}

/// The figure row (segments/segment-page): each figure's value over its label, and the loss under the gain, side by side.
private struct SegmentFigures: View {
    let figures: [SegmentFigure]

    var body: some View {
        HStack(alignment: .top, spacing: 4) {
            ForEach(Array(figures.enumerated()), id: \.offset) { _, figure in
                VStack(spacing: 2) {
                    Text(figure.value).font(.headline).monospacedDigit()
                    Text(figure.label).font(.caption).foregroundStyle(.secondary)
                    if let detail = figure.detail {
                        Text(detail).font(.caption2).foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.vertical, 4)
    }
}

/// The boards row (segments/segment-page): the Leaderboard card and the Mayor card side by side, one under the other when the row is too
/// narrow, and the Leaderboard card alone when the Mayor isn't shown. The Leaderboard card shows its title alone until its board loads.
private struct SegmentBoards: View {
    let state: SegmentState
    let send: (any SegmentEvent) -> Void

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .top, spacing: 16) { cards }
            VStack(alignment: .leading, spacing: 12) { cards }
        }
    }

    @ViewBuilder private var cards: some View {
        let texts = SegmentPageTexts.shared
        let board = state.leaderboard
        LeaderCardView(
            title: texts.leaderboard, leader: board?.leader?.athlete, value: board?.leader.map { texts.leaderboardValue(entry: $0) },
            none: board.map { _ in texts.leaderboardNone }, own: board?.viewer.map { texts.leaderboardYours(entry: $0) }, stacked: true
        ) { send(SegmentEvent.OpenLeaderboard.shared) }
        .frame(minWidth: 150, maxWidth: .infinity, alignment: .leading)
        if let mayor = state.mayor {
            LeaderCardView(
                title: texts.mayorTitle, leader: mayor.leader?.athlete, value: mayor.leader.map { texts.mayorValue(entry: $0) },
                none: texts.mayorNone, own: mayor.viewer.map { texts.mayorYours(entry: $0) }, stacked: true
            ) { send(SegmentEvent.OpenMayors.shared) }
            .frame(minWidth: 150, maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// The footer (segments/segment-page): who created it, its data's credit and licence, and the status of a segment that isn't active with
/// what it means and Open the original.
private struct SegmentFooter: View {
    let state: SegmentState
    let segment: SegmentView
    let openOriginal: () -> Void

    var body: some View {
        let texts = SegmentPageTexts.shared
        Text(PlaceTexts.shared.createdBy(handle: segment.creator.handle.value)).font(.subheadline).foregroundStyle(.secondary)
        if let credit = state.credit {
            let links = SegmentCreditTexts.shared.links(credit: credit)
            ForEach(Array(SegmentCreditTexts.shared.lines(credit: credit).enumerated()), id: \.offset) { index, line in
                if index == 0, links, let url = URL(string: PlaceTexts.shared.OSM_COPYRIGHT_URL) {
                    Link(destination: url) { Text(line) }
                } else {
                    Text(line).font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
        if let help = texts.statusHelp(segment: segment) {
            VStack(alignment: .leading, spacing: 2) {
                if let status = PlaceTexts.shared.status(status: segment.status) {
                    Text(status).font(.subheadline.weight(.semibold)).foregroundStyle(.tint)
                }
                Text(help).font(.footnote).foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)
            if texts.offersOriginal(segment: segment) {
                Button(action: openOriginal) { Text(PlaceTexts.shared.openOriginal) }
            }
        }
    }
}

/// The image beside a ski segment's type, in the secondary colour at the text's size; decorative, since the type says it.
private struct SegmentKindImage: View {
    let name: String
    @ScaledMetric(relativeTo: .headline) private var size: CGFloat = 20

    var body: some View {
        Image(drawable: name)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .foregroundStyle(.secondary)
            .accessibilityHidden(true)
    }
}

/// One of the viewer's efforts as a row: its date, why leaderboards don't rank it and e-bike under it, its rank in the top 10, PR on
/// their fastest, and its time; a tap opens its activity. Its words are Kotlin's (`EffortTexts`), as the shared row's are.
struct EffortRowView: View {
    let effort: EffortView
    let open: () -> Void

    var body: some View {
        let texts = EffortTexts.shared
        Button(action: open) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(texts.date(effort: effort))
                    ForEach(Array(notes.enumerated()), id: \.offset) { _, note in
                        Text(note).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let rank = texts.rank(effort: effort) {
                    Text(rank).font(.footnote.weight(.bold)).foregroundStyle(.tint)
                }
                if let pr = texts.pr(effort: effort) {
                    Text(pr).font(.footnote.weight(.bold)).foregroundStyle(Color(.like))
                }
                Text(verbatim: texts.time(effort: effort)).fontWeight(.medium).monospacedDigit()
            }
            .foregroundStyle(.primary)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    private var notes: [any AppText] {
        let texts = EffortTexts.shared
        return [texts.notRanked(effort: effort), texts.eBike(effort: effort)].compactMap { $0 }
    }
}

#if DEBUG
    #Preview("Segment") {
        SamplePages(Samples.segment) { state in NavigationStack { SegmentPage(state: state) } }
            .environment(\.clock, Samples.clock)
    }

    #Preview("Segment, beyond loading") {
        SamplePages(Samples.segmentVariants) { state in NavigationStack { SegmentPage(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
