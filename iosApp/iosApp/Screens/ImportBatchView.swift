import SwiftUI

/// One batch import's activities (feed/import-cards), drawn from the states `ImportBatchPresenter` publishes, a page at a time: each
/// activity as a native card in a section of its own, the first of each month headed by it, newest started first, with a failed like at
/// the top. Its title, its months and every card's text are Kotlin's (`ImportBatchTexts`, `ActivityCardContent`), shared with the
/// Compose page. Pushed over the shared feed from an import card; the author line opens nothing, since the whole page is theirs.
struct ImportBatchView: View {
    let state: ImportBatchState
    @Environment(\.clock) private var clock

    var body: some View {
        PagedList(
            state.activities, of: ActivityView.self, errorKind: .LoadImportBatch, id: { $0.uri.value },
            sections: sections, retry: { send(ImportBatchEvent.Retry.shared) }, loadMore: { send(ImportBatchEvent.LoadMore.shared) }
        ) {
            // Why the last like failed, over the activities, which stay.
            if let error = state.error {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
        } row: { activity in
            card(activity)
        } empty: {
            Text(.importBatchEmpty).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
        }
        .pageTitle(Text(ImportBatchTexts.shared.title(state: state)))
    }

    /// A section per card, as the shared page spaces its cards apart, the month's heading over its first.
    private func sections(_ activities: [ActivityView]) -> [PagedSection<ActivityView>] {
        ImportBatchTexts.shared.months(activities: activities, zone: clock.kotlinZone).flatMap { month in
            month.activities.elements(as: ActivityView.self).enumerated().map { index, activity in
                PagedSection(id: activity.uri.value, header: index == 0 ? clock.string(month.heading) : nil, items: [activity])
            }
        }
    }

    private func card(_ activity: ActivityView) -> some View {
        let uri = activity.uri.value
        return ActivityCardView(
            content: ActivityCardContent.Companion.shared.of(activity: activity, viewerDid: state.viewerDid),
            authorClickable: false,
            revealed: state.revealed.contains(key: uri),
            send: { send(ImportBatchEvent.Activity(event: $0)) },
            reveal: { state.revealed.eventSink(uri) }
        )
    }

    private func send(_ event: any ImportBatchEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Import batch") {
        SamplePages(Samples.importBatch) { state in NavigationStack { ImportBatchView(state: state) } }
            .environment(\.clock, Samples.clock)
    }

    #Preview("Three months and a failed like") {
        SamplePages(Samples.importBatchVariants) { state in NavigationStack { ImportBatchView(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
