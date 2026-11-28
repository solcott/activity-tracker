import SwiftUI

/// Every report on a segment (segments/trail-reports), drawn from the states `TrailReportsPresenter` publishes and pushed over the
/// segment page: "Last 14 days", then "Older" with its reports muted, each with its author, age, tags, note and photos (a photo opens the
/// viewer), and a menu offering Report and Block (which asks first) on someone else's report or Delete on the viewer's own; the next page
/// loads at the end. Report opens the native report sheet (`reporting(_:)`). Every word is Kotlin's (`TrailReportRow`, `TrailReportTexts`).
struct TrailReportsView: View {
    let state: TrailReportsState
    /// The report whose author Block asks about.
    @State private var blocking: TrailReportRow?

    var body: some View {
        PagedList(
            state.reports, of: TrailReportRow.self, errorKind: .LoadTrailReports, id: { $0.uri },
            sections: sections, retry: retry, loadMore: { send(TrailReportsEvent.LoadMore.shared) }
        ) {
            if let error = state.error {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
        } row: { row in
            TrailReportRowView(row: row, offersReport: state.reporting.available, send: send) { blocking = row }
        } empty: {
            EmptyMessage(.trailConditionsNone)
        }
        .pageTitle(Text(verbatim: state.name), subtitle: Text(.trailConditionsTitle))
        .cardSpacing()
        .alert(
            Text(.blockConfirmTitle(blocking?.author ?? "")), isPresented: .shown(blocking != nil) { blocking = nil },
            presenting: blocking
        ) { row in
            Button(role: .destructive) {
                send(TrailReportsEvent.Block(uri: row.uri))
            } label: {
                Text(.actionBlock)
            }
            Button(.actionCancel, role: .cancel) {}
        } message: { _ in
            Text(ReportTexts.shared.blockConfirmMessage)
        }
        .reporting(state.reporting)
    }

    /// The rows under their headings: a section starts at each row Kotlin heads (`TrailReportsState.heading`).
    private func sections(_ rows: [TrailReportRow]) -> [PagedSection<TrailReportRow>] {
        var sections: [PagedSection<TrailReportRow>] = []
        for (index, row) in rows.enumerated() {
            if let heading = state.heading(index: Int32(index)) {
                sections.append(PagedSection(id: row.uri, header: AppStrings.string(heading), items: [row]))
            } else if let last = sections.popLast() {
                sections.append(PagedSection(id: last.id, header: last.header, items: last.items + [row]))
            } else {
                sections.append(PagedSection(id: row.uri, header: nil, items: [row]))
            }
        }
        return sections
    }

    private func retry() {
        send(TrailReportsEvent.Retry.shared)
    }

    private func send(_ event: any TrailReportsEvent) {
        state.eventSink(event)
    }
}

/// One report: its author's picture and name, its age and the menu on one line, then its tags, note and photos; an older one muted.
private struct TrailReportRowView: View {
    let row: TrailReportRow
    /// Whether Report is offered: a labeler is set up (`Reporting.available`).
    let offersReport: Bool
    let send: (any TrailReportsEvent) -> Void
    let block: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Avatar(url: row.avatar, name: row.author, size: 32)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: row.author).font(.subheadline.weight(.semibold))
                    Text(row.age).font(.footnote).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                menu
            }
            Group {
                let tags = row.shownTags
                if !tags.isEmpty {
                    ConditionTags(tags: tags)
                }
                if let note = row.note {
                    Text(verbatim: note)
                }
            }
            .opacity(row.current ? 1 : 0.6)
            let photos = row.shownPhotos
            if !photos.isEmpty {
                TrailPhotoStrip(photos: photos, size: 72) { send(TrailReportsEvent.OpenPhoto(uri: row.uri, position: Int32($0 + 1))) }
                    .opacity(row.current ? 1 : 0.6)
            }
        }
        .padding(.vertical, 4)
    }

    private var menu: some View {
        Menu {
            if row.own {
                Button(role: .destructive) {
                    send(TrailReportsEvent.Delete(uri: row.uri))
                } label: {
                    Text(.trailReportDelete)
                }
            } else {
                if offersReport {
                    Button {
                        send(TrailReportsEvent.Report(uri: row.uri))
                    } label: {
                        Text(.actionReport)
                    }
                }
                Button(role: .destructive, action: block) {
                    Text(.actionBlock)
                }
            }
        } label: {
            AppIconLabel(.more).foregroundStyle(.secondary)
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(Text(MediaGalleryTexts.shared.more))
    }
}

/// Trail photos full screen on black (segments/trail-reports), drawn from the states `TrailPhotosPresenter` publishes: swiped a photo
/// at a time through the list they were opened from, Close, "1 of 16" and a menu with Report and Block (which asks first) on someone
/// else's photo or Remove from trail on the viewer's own; under the photo its author, when and with which tags, its alt text, See
/// report and See segment. A labelled photo is covered by its reason until the viewer shows it.
struct TrailPhotosView: View {
    let state: TrailPhotosState
    @State private var confirmingBlock = false
    /// The labelled photos the viewer has chosen to see, by index.
    @State private var revealed: Set<Int> = []

    var body: some View {
        let pages = state.pages
        let index = min(Int(state.index), max(pages.count - 1, 0))
        ZStack {
            Color.black.ignoresSafeArea()
            ContentStateView(state.content, errorKind: .LoadTrailReports, retry: retry) {
                if !pages.isEmpty {
                    TabView(selection: Binding(get: { index }, set: { send(TrailPhotosEvent.Show(index: Int32($0))) })) {
                        ForEach(Array(pages.enumerated()), id: \.offset) { offset, page in
                            photo(page, index: offset).tag(offset)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .ignoresSafeArea()
                }
            }
            VStack(spacing: 8) {
                bar(pages.indices.contains(index) ? pages[index] : nil)
                Spacer()
                RefreshStatusView(state: state.content, errorKind: .LoadTrailReports, retry: retry)
                if let error = state.error { ErrorMessage(error, layout: .row) }
                if pages.indices.contains(index) {
                    caption(pages[index])
                }
            }
            .padding(12)
        }
        .environment(\.colorScheme, .dark)
        .toolbar(.hidden, for: .navigationBar)
        .accessibilityAction(.escape) { send(TrailPhotosEvent.Back.shared) }
        .alert(Text(.blockConfirmTitle(pages.indices.contains(index) ? pages[index].author : "")), isPresented: $confirmingBlock) {
            Button(role: .destructive) {
                send(TrailPhotosEvent.Block.shared)
            } label: {
                Text(.actionBlock)
            }
            Button(.actionCancel, role: .cancel) {}
        } message: {
            Text(ReportTexts.shared.blockConfirmMessage)
        }
        .reporting(state.reporting)
    }

    /// Close, and the position and the photo's menu at the other end.
    private func bar(_ page: TrailPhotoPage?) -> some View {
        GalleryChrome(counter: state.position, showsMenu: page != nil, close: { send(TrailPhotosEvent.Back.shared) }) {
            if let page {
                if page.own {
                    Button(role: .destructive) {
                        send(TrailPhotosEvent.Remove.shared)
                    } label: {
                        Text(.trailPhotoRemove)
                    }
                } else {
                    if state.reporting.available {
                        Button {
                            send(TrailPhotosEvent.Report.shared)
                        } label: {
                            Text(.actionReport)
                        }
                    }
                    Button(role: .destructive) {
                        confirmingBlock = true
                    } label: {
                        Text(.actionBlock)
                    }
                }
            }
        }
    }

    /// Who took it and when, with which tags; its alt text; and See report and See segment.
    private func caption(_ page: TrailPhotoPage) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: page.author).font(.headline)
            Text(page.caption).font(.subheadline).foregroundStyle(.white.opacity(0.8))
            if let alt = page.alt, !alt.isEmpty {
                Text(verbatim: alt).font(.footnote).foregroundStyle(.white.opacity(0.7)).lineLimit(4)
            }
            HStack(spacing: 20) {
                Button {
                    send(TrailPhotosEvent.OpenReport.shared)
                } label: {
                    Text(.trailPhotoSeeReport).font(.subheadline.weight(.semibold))
                }
                Button {
                    send(TrailPhotosEvent.OpenSegment.shared)
                } label: {
                    Text(.trailPhotoSeeSegment).font(.subheadline.weight(.semibold))
                }
            }
            .buttonStyle(.borderless)
            .padding(.top, 4)
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(.black.opacity(0.55), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    /// A page: the photo fitted to the screen, or its label's cover with Show.
    @ViewBuilder private func photo(_ page: TrailPhotoPage, index: Int) -> some View {
        if let blur = page.blur, !revealed.contains(index) {
            CoveredPhoto(reason: Text(blur), show: Text(.trailPhotoHidden), style: .screen) {
                revealed.insert(index)
            } picture: {
                RemoteImage(page.url, fits: true) { Color.clear }
            }
        } else {
            RemoteImage(page.url, fits: true) { Color.clear }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityElement()
                .accessibilityLabel(page.alt.map { Text(verbatim: $0) } ?? Text(.trailPhotosTitle))
                .accessibilityAddTraits(.isImage)
        }
    }

    private func retry() {
        send(TrailPhotosEvent.Retry.shared)
    }

    private func send(_ event: any TrailPhotosEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Trail reports") {
        SamplePages(Samples.trailReports) { state in NavigationStack { TrailReportsView(state: state) } }
    }

    #Preview("Trail photos") {
        NavigationStack { TrailPhotosView(state: Samples.trailPhotos) }
    }
#endif
