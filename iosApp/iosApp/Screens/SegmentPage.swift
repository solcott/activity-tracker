import SwiftUI

/// A segment's page (segments/segment-efforts, segments/segment-elevation, segments/segment-names, segments/segment-mayors), drawn from
/// the states `SegmentPresenter` publishes: Star in the bar, its line on the native route map (expanding to the enlarged map), "originally"
/// its old name, its length and type with a ski segment's icon, its gain, loss and grade, its high and low points and the native
/// elevation chart (`LineChartView`), who created it, its data's credit and licence, the status of a segment that isn't active with Open
/// the original, the Leaderboard button and the Mayor card for an active run, the names (`PlaceNamesSection`, not for a lift), then the
/// viewer's efforts. Every word and condition is Kotlin's (`SegmentPageTexts`, `EffortTexts`, `PlaceTexts`, `SegmentState`'s getters),
/// shared with the Compose page. The place-name sheet and its delete confirmation show over it (`placeNames(_:)`).
///
/// Named `SegmentPage` because `SegmentView` is the lexicon class it shows.
struct SegmentPage: View {
    let state: SegmentState

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
    }

    private func loaded(_ segment: SegmentView) -> some View {
        let texts = SegmentPageTexts.shared
        let lift = state.isLift
        return List {
            RefreshStatusView(state: state.content, errorKind: .LoadSegment, retry: retry).listStatusRow()
            if let map = state.routeMap {
                Section {
                    RouteMapView(map: map, expand: { send(SegmentEvent.OpenMap.shared) }).listRowInsets(EdgeInsets())
                }
            }
            Section {
                SegmentAbout(state: state, segment: segment, openOriginal: { send(SegmentEvent.OpenOriginal.shared) })
            }
            if state.offersLeaderboard || state.mayor != nil {
                Section {
                    if state.offersLeaderboard {
                        AppListRow(Text(texts.leaderboard)) { send(SegmentEvent.OpenLeaderboard.shared) }
                    }
                    if let mayor = state.mayor {
                        LeaderCardView(
                            title: texts.mayorTitle, leader: mayor.leader?.athlete, value: mayor.leader.map { texts.mayorValue(entry: $0) },
                            none: texts.mayorNone, own: mayor.viewer.map { texts.mayorYours(entry: $0) }
                        ) { send(SegmentEvent.OpenMayors.shared) }
                    }
                }
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
        }
    }

    private func retry() {
        send(SegmentEvent.Retry.shared)
    }

    private func send(_ event: any SegmentEvent) {
        state.eventSink(event)
    }
}

/// The segment's original name when renamed, its length and type, its elevation and profile, its creator, its data's credit, and its
/// status with what it means.
private struct SegmentAbout: View {
    let state: SegmentState
    let segment: SegmentView
    let openOriginal: () -> Void

    @Environment(\.stats) private var stats

    var body: some View {
        let texts = SegmentPageTexts.shared
        VStack(alignment: .leading, spacing: 4) {
            if let original = state.originalName {
                Text(PlaceNameTexts.shared.originally(original: original)).font(.subheadline).foregroundStyle(.secondary)
            }
            HStack(spacing: 8) {
                if let icon = SegmentTexts.shared.kindIcon(sport: segment.record.sport, kind: segment.record.kind) {
                    SegmentKindImage(name: icon)
                }
                Text(texts.summary(segment: segment, units: stats.units)).font(.headline)
            }
            if let elevation = texts.elevation(segment: segment, units: stats.units) {
                Text(elevation).font(.subheadline)
            }
            if let profile = state.profile {
                Text(texts.extremes(profile: profile, units: stats.units)).font(.subheadline)
            }
            Text(PlaceTexts.shared.createdBy(handle: segment.creator.handle.value)).font(.subheadline).foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        if let chart = state.chart(units: stats.units) {
            LineChartView(chart: chart).padding(.vertical, 4)
        }
        if let credit = state.credit {
            if let osm = texts.osmCredit(credit: credit), let url = URL(string: PlaceTexts.shared.OSM_COPYRIGHT_URL) {
                Link(destination: url) { Text(osm) }
            }
            if let agency = texts.agencyCredit(credit: credit) {
                Text(agency).font(.footnote).foregroundStyle(.secondary)
            }
            if let license = texts.license(credit: credit) {
                Text(license).font(.footnote).foregroundStyle(.secondary)
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
