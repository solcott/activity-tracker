import SwiftUI

/// Every value the athlete's thresholds have had (ui/settings-layout), drawn from the states `ThresholdHistoryPresenter` publishes: the
/// defaults, then each sport with history, as a group that expands to each threshold's value now, its change, its step chart and its
/// values (each removable), or collapses to a tile per threshold with its sparkline. What it says and charts is Kotlin's
/// (`ThresholdHistoryTexts`, the state's getters), shared with the Compose page.
///
/// It is a settings page opened from Training zones: titled in the stack's bar alone, and in its own header with a way back beside
/// Settings' list.
struct ThresholdHistoryView: View {
    let state: ThresholdHistoryState

    var body: some View {
        let groups = state.groups?.elements(as: ThresholdHistoryGroup.self) ?? []
        ContentStateView(state.content, errorKind: .LoadProfile, retry: { send(ThresholdHistoryEvent.Retry.shared) }) {
            List {
                RefreshStatusView(state: state.content, errorKind: .LoadProfile) { send(ThresholdHistoryEvent.Retry.shared) }
                    .listStatusRow()
                if let error = state.error {
                    ErrorMessage(error, layout: .row).listStatusRow()
                }
                if groups.isEmpty {
                    Text(.zonesHistoryEmpty).foregroundStyle(.secondary)
                } else {
                    Section {
                    } header: {
                        Text(.zonesHistoryHelp).font(.subheadline.weight(.regular)).textCase(nil)
                    }
                    ForEach(groups, id: \.key) { group in
                        GroupSection(group: group, state: state)
                    }
                }
            }
        }
        .pageTitle(Text(.zonesHistory), back: { send(ThresholdHistoryEvent.Back.shared) }) {
            if !groups.isEmpty {
                Button { send(ThresholdHistoryEvent.ToggleAll.shared) } label: {
                    Image(state.anyExpanded ? AppIcon.collapseAll : AppIcon.expandAll)
                }
                .accessibilityLabel(Text(ThresholdHistoryTexts.shared.toggleAll(state: state)))
            }
        }
    }

    private func send(_ event: any ThresholdHistoryEvent) {
        state.eventSink(event)
    }
}

/// The defaults or one sport: its header, which expands or collapses it, then each threshold in full, or a tile for each.
private struct GroupSection: View {
    let group: ThresholdHistoryGroup
    let state: ThresholdHistoryState

    var body: some View {
        let texts = ThresholdHistoryTexts.shared
        let thresholds = group.thresholds.elements(as: ThresholdHistorySection.self)
        Section {
            Button { state.eventSink(ThresholdHistoryEvent.ToggleGroup(sport: group.sport)) } label: {
                HStack(spacing: 12) {
                    Image(systemName: "chevron.right")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.tint)
                        .rotationEffect(.degrees(group.expanded ? 90 : 0))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(texts.heading(group: group)).font(.headline).foregroundStyle(.primary)
                        Text(texts.help(group: group)).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 8)
                    Text(texts.summary(group: group)).font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.trailing)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(Text(texts.expansion(group: group)))
            if group.expanded {
                ForEach(thresholds, id: \.threshold) { section in
                    ThresholdRows(section: section, state: state)
                }
            } else {
                Overview(thresholds: thresholds)
            }
        }
    }
}

/// One threshold in full: its value now and how it moved, its chart with its first date and today under it, its values newest first,
/// and Show all or Show fewer.
private struct ThresholdRows: View {
    let section: ThresholdHistorySection
    let state: ThresholdHistoryState

    var body: some View {
        let texts = ThresholdHistoryTexts.shared
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(texts.label(threshold: section.threshold)).font(.subheadline.weight(.semibold))
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text(texts.current(section: section)).font(.title3.weight(.semibold))
                ChangeLine(section: section).font(.caption)
            }
        }
        .accessibilityElement(children: .combine)
        if let chart = texts.chart(section: section, detailed: true), let placed = section.chart {
            VStack(spacing: 4) {
                LineChartView(chart: chart, height: 150)
                HStack {
                    Text(texts.chartStart(chart: placed))
                    Spacer(minLength: 8)
                    if let undated = texts.undated(section: section) {
                        Text(undated).multilineTextAlignment(.center)
                        Spacer(minLength: 8)
                    }
                    Text(.zonesHistoryToday)
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
            }
        }
        ForEach(Array(section.shownRows.enumerated()), id: \.offset) { _, row in
            ValueRow(section: section, row: row, state: state)
        }
        if section.canShowAll {
            Button { state.eventSink(ThresholdHistoryEvent.ToggleShowAll(threshold: section.threshold, sport: section.sport)) } label: {
                Text(texts.showAll(section: section))
            }
        }
    }
}

/// A value with its date and source, marked when current, and its remove menu, or progress while it is being removed.
private struct ValueRow: View {
    let section: ThresholdHistorySection
    let row: ThresholdHistoryRow
    let state: ThresholdHistoryState

    var body: some View {
        let texts = ThresholdHistoryTexts.shared
        HStack(spacing: 12) {
            Text(texts.value(section: section, value: row.entry.value)).font(.body.weight(.medium))
                .frame(minWidth: 84, alignment: .leading)
            VStack(alignment: .leading, spacing: 2) {
                Text(texts.date(row: row)).font(.subheadline)
                Text(texts.source(row: row)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 8)
            if row.current {
                Text(.zonesHistoryCurrent)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color(.onAccent))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(Color.accentColor, in: Capsule())
            }
            if state.isRemoving(row: row) {
                AppProgress(small: true).frame(width: 28)
            } else {
                Menu {
                    Button(role: .destructive) { state.eventSink(ThresholdHistoryEvent.Remove(entry: row.entry)) } label: {
                        Label { Text(.zonesHistoryRemove) } icon: { Image(AppIcon.delete) }
                    }
                } label: {
                    Image(AppIcon.delete).frame(width: 28, height: 28).contentShape(Rectangle())
                }
                // Muted as the shared page's button is; a menu's label otherwise takes the accent.
                .tint(Color(.secondaryLabel))
                .disabled(!state.canRemove)
                .accessibilityLabel(Text(texts.removeLabel(section: section, row: row)))
            }
        }
    }
}

/// A collapsed group: a tile per threshold with its value, its sparkline when it has one, and how it moved.
private struct Overview: View {
    let thresholds: [ThresholdHistorySection]

    var body: some View {
        let texts = ThresholdHistoryTexts.shared
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 8)], alignment: .leading, spacing: 8) {
            ForEach(thresholds, id: \.threshold) { section in
                VStack(alignment: .leading, spacing: 2) {
                    Text(texts.label(threshold: section.threshold)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    Text(texts.current(section: section)).font(.headline)
                    if let chart = texts.chart(section: section, detailed: false) {
                        LineChartView(chart: chart, height: 26, interactive: false)
                    }
                    ChangeLine(section: section).font(.caption2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.vertical, 4)
    }
}

/// How a threshold moved, in the accent colour, or "Only value" in the secondary one; nothing when there's nothing to compare.
private struct ChangeLine: View {
    let section: ThresholdHistorySection

    var body: some View {
        if let change = ThresholdHistoryTexts.shared.change(section: section) {
            Text(change)
                .foregroundStyle(ThresholdHistoryTexts.shared.changed(section: section) ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                .lineLimit(1)
        }
    }
}

extension ThresholdHistoryGroup {
    /// What tells one group from another: the defaults, or a sport.
    fileprivate var key: String { sport ?? "" }
}

#if DEBUG
    #Preview("Threshold history") {
        SamplePages(Samples.thresholdHistory) { state in NavigationStack { ThresholdHistoryView(state: state) } }
    }

    #Preview("Threshold history, beyond the shared samples") {
        SamplePages(Samples.thresholdHistoryVariants) { state in NavigationStack { ThresholdHistoryView(state: state) } }
    }
#endif
