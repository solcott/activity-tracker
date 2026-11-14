import SwiftUI

/// An activity's page (feed/activity-page-layout), drawn from the states `ActivityDetailPresenter` publishes: who, when and what, the
/// route on the native map (expanding to the enlarged map) with the photos, the hero figures, likes and who liked it, Together with,
/// the training load, a card per metric with its native charts, zones and laps, the segment efforts and lifts, every figure in the
/// stats list, and the comments (`CommentsSection`). The owner's menu and Report on someone else's activity are in the bar
/// (`ActivityPageMenu`), the report sheet over it (`reporting(_:)`). From a regular width of 840 points the map, header and stats sit in
/// a 400-point pane beside the cards, as the shared page lays it out from 840dp. Every word and condition is Kotlin's
/// (`ActivityPageTexts`, `FigureTexts`, `CardLines`, `ActivityCardContent`), shared with the Compose page.
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
        .pageTitle(Text(ActivityPageTexts.shared.title)) {
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
                        ActivityPanes(page: page, content: content, retry: retry)
                    } else {
                        ActivityColumn(page: page, content: content, retry: retry)
                    }
                }
            }
        }
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
            isPresented: Binding(get: { page.combineSuggestion?.asPrompt == true }, set: { shown in if !shown { send(ActivityDetailEvent.NotNowSuggestion.shared) } })
        ) {
            Button { send(ActivityDetailEvent.CombineSuggested.shared) } label: { Text(texts.combine) }
            Button(role: .cancel) { send(ActivityDetailEvent.NotNowSuggestion.shared) } label: { Text(texts.notNow) }
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

/// Under 840 points, or a compact width: one list, the shared page's compact order.
private struct ActivityColumn: View {
    let page: ActivityDetailPage
    let content: KotlinContentState
    let retry: () -> Void

    var body: some View {
        let sections = ActivitySections(page: page)
        List {
            RefreshStatusView(state: content, errorKind: .LoadActivity, retry: retry).listStatusRow()
            sections.header
            sections.media
            sections.hero
            sections.likes
            sections.group
            sections.trainingLoad
            sections.cards
            sections.segments
            sections.stats
            sections.comments
        }
        .listStyle(.insetGrouped)
    }
}

/// From 840 points at a regular width: a 400-point pane with the map, header, hero, likes, group, avg/max table, training load, stats
/// and comments, each scrolling on its own, beside the cards, segments and lifts.
private struct ActivityPanes: View {
    let page: ActivityDetailPage
    let content: KotlinContentState
    let retry: () -> Void

    var body: some View {
        let sections = ActivitySections(page: page)
        HStack(spacing: 0) {
            List {
                RefreshStatusView(state: content, errorKind: .LoadActivity, retry: retry).listStatusRow()
                sections.media
                sections.header
                sections.hero
                sections.likes
                sections.group
                AvgMaxSection(content: page.page)
                sections.trainingLoad
                sections.stats
                sections.comments
            }
            .listStyle(.insetGrouped)
            .frame(width: 400)
            Divider()
            List {
                sections.cards
                sections.segments
            }
            .listStyle(.insetGrouped)
            .frame(maxWidth: 1_040)
        }
        .frame(maxWidth: .infinity)
    }
}

/// The page's sections, each placed by the layout above.
private struct ActivitySections {
    let page: ActivityDetailPage

    private var texts: ActivityPageTexts { ActivityPageTexts.shared }

    private func send(_ event: any ActivityDetailEvent) {
        page.eventSink(event)
    }

    var header: some View {
        Section { ActivityHeader(page: page) }
    }

    @ViewBuilder var media: some View {
        let card = ActivityCardContent.Companion.shared.of(activity: page.activity, viewerDid: page.viewerDid.value)
        let map = texts.routeMap(page: page)
        let lock = texts.mapLock(page: page)
        if map != nil || !card.media.elements(as: MediaPhoto.self).isEmpty {
            Section {
                if let map {
                    RouteMapView(map: map, expand: { send(ActivityDetailEvent.OpenMap.shared) }).listRowInsets(EdgeInsets())
                }
                if !card.media.elements(as: MediaPhoto.self).isEmpty {
                    MediaStrip(
                        content: card, revealed: page.revealed.contains(key: page.activity.uri.value),
                        reveal: { page.revealed.eventSink(page.activity.uri.value) }, showsRoute: false
                    ) { index in
                        send(ActivityDetailEvent.OpenMedia(index: index))
                    }
                }
                if let lock {
                    HStack(spacing: 4) {
                        Image(.lock)
                        Text(lock.text)
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
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

    var likes: some View {
        Section { ActivityLikes(page: page) }
    }

    @ViewBuilder var group: some View {
        if page.showsGroup {
            ActivityTogether(page: page)
        }
    }

    var trainingLoad: some View {
        TrainingLoadSection(figures: page.page.trainingLoad.elements(as: Figure.self))
    }

    @ViewBuilder var cards: some View {
        if let charts = page.page.charts {
            ForEach(Array(page.page.cards.elements(as: MetricCard.self).enumerated()), id: \.offset) { _, card in
                MetricCardSection(
                    card: card, charts: charts, missingZones: page.chartsMissingZones,
                    caption: texts.sourceCaption(page: page, section: card.section),
                    setThresholds: { send(ActivityDetailEvent.OpenTrainingZones.shared) })
            }
        }
    }

    @ViewBuilder var segments: some View {
        let groups = page.segments.elements(as: SegmentEffortGroup.self)
        if !groups.isEmpty {
            Section {
                ForEach(Array(groups.enumerated()), id: \.offset) { _, group in
                    let rows = group.rows.elements(as: SegmentEffortRow.self)
                    // Every bar in a group starts past its deepest indent, so the bars share one axis and their spans line up.
                    let deepest: Int = rows.map { Int($0.depth) }.max() ?? 0
                    let barIndent: CGFloat = CGFloat(deepest) * EffortGuides.indent
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        SegmentEffortRowView(row: row, barIndent: barIndent) { send(texts.openEffort(effort: row.effort)) }
                    }
                }
            } header: {
                Text(texts.segmentsHeading)
            }
        }
        let lifts = page.lifts.elements(as: LiftRides.self)
        if !lifts.isEmpty {
            Section {
                ForEach(Array(lifts.enumerated()), id: \.offset) { _, lift in
                    Button { send(texts.openLift(lift: lift)) } label: {
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

    var stats: some View {
        StatsSections(groups: page.page.stats.elements(as: StatsGroup.self)) { send(ActivityDetailEvent.OpenGear(uri: $0)) }
    }

    var comments: some View {
        CommentsSection(state: DetailPackage.getCommentsState(page), reporting: page.reporting)
    }
}

/// Who, when and what: the labels' notices, the author with the tier's badge, the start (with its lock), the title and type, the dive
/// site, the description, the other recordings and members, the combine banner, and what an action reported.
private struct ActivityHeader: View {
    let page: ActivityDetailPage

    @Environment(\.clock) private var clock
    @Environment(\.stats) private var stats
    /// The owner's combination sheet is open; a split that leaves nothing combined closes it.
    @State private var editing = false

    var body: some View {
        let texts = ActivityPageTexts.shared
        let activity = page.activity
        let card = ActivityCardContent.Companion.shared.of(activity: activity, viewerDid: page.viewerDid.value)
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(card.notices().enumerated()), id: \.offset) { _, notice in
                Text(notice.text)
                    .font(.caption)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .foregroundStyle(notice.isError ? Color.red : Color.primary)
                    .background(
                        notice.isError ? Color.red.opacity(0.15) : Color(.secondarySystemFill),
                        in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            HStack(spacing: 8) {
                Button { send(ActivityDetailEvent.OpenAuthor.shared) } label: {
                    Text(texts.author(activity: activity)).font(.subheadline.weight(.semibold))
                }
                .buttonStyle(.borderless)
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
                    Text(verbatim: FigureValueText.string(start.value, units: stats.units, clock: clock)).font(.footnote).foregroundStyle(.secondary)
                    MarkBadge(mark: start.mark)
                }
            }
            Text(texts.heading(activity: activity)).font(.title2.weight(.semibold))
            if let type = texts.typeLine(activity: activity) {
                Label { Text(type) } icon: { SportIcon(card.type) }
                    .font(.subheadline)
                    .foregroundStyle(.tint)
            }
            if let site = texts.diveSite(activity: activity) {
                if texts.opensDiveSite(activity: activity) {
                    Button { send(ActivityDetailEvent.OpenDiveSite.shared) } label: {
                        Label { Text(site) } icon: { Image(.location) }
                    }
                    .buttonStyle(.borderless)
                    .font(.subheadline)
                } else {
                    Label { Text(site) } icon: { Image(.location) }.font(.subheadline).foregroundStyle(.secondary)
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
            Button { send(ActivityDetailEvent.OpenActivity(uri: recordingOf)) } label: { Text(texts.openRecorded) }
                .buttonStyle(.borderless)
        } else if let recordings = activity.recordings, !recordings.isEmpty {
            Text(texts.recordings).font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(Array(recordings.enumerated()), id: \.offset) { _, recording in
                Button { send(ActivityDetailEvent.OpenActivity(uri: recording.uri)) } label: {
                    Text(texts.recordingName(recording: recording, activityStart: activity.record.startedAt))
                }
                .buttonStyle(.borderless)
            }
        }
    }

    /// A combined activity's members, each opening its page; or, on a member, the combined activity it's part of.
    @ViewBuilder private var members: some View {
        let texts = RecordingTexts.shared
        let activity = page.activity
        if let combinedInto = activity.combinedInto {
            Text(texts.memberOf).font(.subheadline)
            Button { send(ActivityDetailEvent.OpenActivity(uri: combinedInto)) } label: { Text(texts.openCombined) }
                .buttonStyle(.borderless)
        } else if let members = activity.members, !members.isEmpty {
            HStack {
                Text(texts.members).font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
                Spacer(minLength: 8)
                if CombinationTexts.shared.offersEdit(page: page) {
                    Button { editing = true } label: { Text(CombinationTexts.shared.edit) }
                        .buttonStyle(.borderless)
                        .font(.footnote)
                        .accessibilityLabel(Text(CombinationTexts.shared.editDescription))
                }
            }
            ForEach(Array(members.enumerated()), id: \.offset) { _, member in
                Button { send(ActivityDetailEvent.OpenActivity(uri: member.uri)) } label: { Text(texts.memberName(member: member)) }
                    .buttonStyle(.borderless)
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
                Button { send(ActivityDetailEvent.DontCombineSuggestion.shared) } label: { Text(texts.dontCombine) }
                    .buttonStyle(.borderless)
                    .disabled(suggestion.busy)
            }
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Color(.separator), lineWidth: 0.5))
        .padding(.top, 4)
    }
}

/// The like count or button, Show likes, why a like couldn't be given, and who liked it once shown.
private struct ActivityLikes: View {
    let page: ActivityDetailPage

    var body: some View {
        let texts = ActivityPageTexts.shared
        HStack {
            if page.isOwn {
                Text(texts.likeCount(activity: page.activity))
            } else {
                let liked = page.activity.viewer?.like != nil
                Button { send(ActivityDetailEvent.ToggleLike.shared) } label: {
                    Label { Text(texts.likeButton(activity: page.activity)) } icon: { Image(.like) }
                }
                .buttonStyle(.borderless)
                .foregroundStyle(liked ? Color("LikeColor") : Color.accentColor)
            }
            Spacer(minLength: 8)
            if texts.showsLikesToggle(page: page) {
                Button { send(ActivityDetailEvent.ToggleLikesList.shared) } label: {
                    Text(texts.likesToggle(shown: page.likesList != nil))
                }
                .buttonStyle(.borderless)
            }
        }
        if let error = page.likeError {
            ErrorMessage(error, layout: .row)
        }
        if let likers = page.likesList {
            let athletes = likers.athletes.elements(as: ProfileViewBasic.self)
            if likers.isLoading {
                AppProgress(small: true).frame(maxWidth: .infinity)
            } else if let error = likers.error {
                ErrorMessage(error, layout: .row)
            } else {
                ForEach(athletes, id: \.did.value) { athlete in
                    let name = AppPackage.getShownName(athlete)
                    AppItemRow(
                        Text(verbatim: name), lines: [Text(LabelText.shared.handle(handle: athlete.handle.value))],
                        opens: { send(ActivityDetailEvent.OpenProfile(did: athlete.did)) }
                    ) {
                        Avatar(url: athlete.avatar, name: name)
                    }
                }
            }
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
                Button { send(texts.groupEvent(own: own)) } label: { Text(texts.groupButton(own: own)) }
                    .disabled(own.busy)
                if let error = own.error { ErrorMessage(error, layout: .row) }
            }
            if page.isOwnCopy {
                Button { send(ActivityDetailEvent.LeaveCopyGroup.shared) } label: { Text(texts.leaveCopyGroup) }
                    .disabled(page.leavingCopyGroup)
                if let error = page.copyGroupError { ErrorMessage(error, layout: .row) }
            }
            if page.canAddPeople {
                Button { send(ActivityDetailEvent.AddPeople.shared) } label: { Text(texts.addPeople) }
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
    let open: () -> Void

    var body: some View {
        let texts = EffortTexts.shared
        let effort = row.effort
        Button(action: open) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: row.label)
                        ForEach(Array([texts.notRanked(effort: effort), texts.eBike(effort: effort)].compactMap { $0 }.enumerated()), id: \.offset) {
                            _, note in Text(note).font(.footnote).foregroundStyle(.secondary)
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

    #Preview("Activity, beyond loading") {
        SamplePages(Samples.activityDetailVariants) { state in NavigationStack { ActivityPage(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
