import SwiftUI

/// The feed's filter bar drawn natively (feed/feed-filter), from the sub-screen's state the presenter host runs (`SubScreenView`): a chip
/// for types and a menu chip for dates, which wrap onto a second line rather than scroll, and Clear while either narrows the list. Its
/// words and marks are Kotlin's (`FeedFilterTexts`, `TypePickerState.selectionOf`), as the shared bar's are. The type picker is a plain
/// sheet of the quick types and each category's types; the shared picker's search and collapsed sports are the native feed's to add.
///
/// The native feed draws it as its list's first row, its scope chip leading (ios-native-feed).
struct FeedFilterBar: View {
    let state: FeedFilterState
    /// A chip of the page's own before the bar's, on the same wrapping line: the feed's scope.
    var leading: AnyView? = nil

    var body: some View {
        let filter = state.filter
        FlowRow {
            if let leading {
                leading
            }
            ChipButton(filled: filter.types.size > 0) {
                state.eventSink(FeedFilterEvent.OpenTypes.shared)
            } label: {
                Text(FeedFilterTexts.shared.types(filter: filter))
            }
            DateChip(state: state)
            if filter.isNarrowed {
                AppTextButton(.feedFilterClear) { state.eventSink(FeedFilterEvent.Clear.shared) }
            }
        }
        .sheet(isPresented: shown(state.typePicker != nil, dismiss: FeedFilterEvent.DismissTypes.shared)) {
            if let picker = state.typePicker {
                TypePickerSheet(picker: picker, eventSink: state.eventSink)
            }
        }
        .sheet(isPresented: shown(state.customRange != nil, dismiss: FeedFilterEvent.DismissCustomRange.shared)) {
            if let range = state.customRange {
                CustomRangeSheet(range: range, eventSink: state.eventSink)
            }
        }
    }

    /// A sheet shown while the state holds it, which sends `dismiss` when the athlete swipes it away.
    private func shown(_ isShown: Bool, dismiss: any FeedFilterEvent) -> Binding<Bool> {
        Binding(get: { isShown }, set: { if !$0 && isShown { state.eventSink(dismiss) } })
    }
}

/// The date chip: a menu of any time, the presets and Custom, the chosen one checked (`FeedFilterTexts.chosenRange`), naming the range.
private struct DateChip: View {
    let state: FeedFilterState

    var body: some View {
        let range = state.filter.range
        let choices = FeedFilterTexts.shared.rangeChoices()
        let chosen = Int(FeedFilterTexts.shared.chosenRange(range: range))
        Menu {
            Picker(selection: Binding(get: { chosen }, set: { pick($0, choices) })) {
                ForEach(choices.indices, id: \.self) { index in
                    Text(FeedFilterTexts.shared.range(range: choices[index].range)).tag(index)
                }
                Text(.feedFilterCustom).tag(choices.count)
            } label: {
                Text(FeedFilterTexts.shared.range(range: range))
            }
            .pickerStyle(.inline)
        } label: {
            HStack(spacing: 4) {
                Text(FeedFilterTexts.shared.range(range: range)).lineLimit(1)
                Image(systemName: "chevron.down").font(.caption.weight(.semibold)).accessibilityHidden(true)
            }
            .chipLabel(filled: range != nil)
        }
        .chip(filled: range != nil)
    }

    private func pick(_ index: Int, _ choices: [DateRangeChoice]) {
        if index < choices.count {
            state.eventSink(FeedFilterEvent.SetRange(range: choices[index].range))
        } else {
            state.eventSink(FeedFilterEvent.OpenCustomRange.shared)
        }
    }
}

/// A capsule button, filled while its choice narrows the list, as the shared filter chip is selected.
private struct ChipButton<Label: View>: View {
    let filled: Bool
    let action: () -> Void
    @ViewBuilder let label: () -> Label

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                label().lineLimit(1)
                Image(systemName: "chevron.down").font(.caption.weight(.semibold)).accessibilityHidden(true)
            }
            .chipLabel(filled: filled)
        }
        .chip(filled: filled)
    }
}

extension View {
    fileprivate func chipLabel(filled: Bool) -> some View {
        font(.subheadline.weight(.medium))
            .foregroundStyle(filled ? AnyShapeStyle(Color(.onAccent)) : AnyShapeStyle(.tint))
    }

    @ViewBuilder fileprivate func chip(filled: Bool) -> some View {
        if filled {
            buttonStyle(.borderedProminent).buttonBorderShape(.capsule).controlSize(.small).fixedSize()
        } else {
            buttonStyle(.bordered).buttonBorderShape(.capsule).controlSize(.small).fixedSize()
        }
    }
}

/// The type picker: a search field, the quick types, then each category with its types, each marked by how much of it the draft holds.
/// A sport with sub-sports under it (`SportTypeRow.nested`) discloses them, collapsed each time the sheet opens. While something is
/// typed it lists every match (`TypePickerState.groups`, the shared rule), expanded, with no quick types and no category marks. Apply
/// changes the filter; Cancel leaves it.
struct TypePickerSheet: View {
    let picker: TypePickerState
    let eventSink: (any FeedFilterEvent) -> Void
    /// The search, which only the sheet uses, so it starts empty each time the sheet opens.
    @State private var query: String
    @State private var expanded: Set<String>

    /// `query` starts the search typed and `expanded` the sports disclosed (by their token), for a preview or snapshot.
    init(
        picker: TypePickerState, query: String = "", expanded: Set<String> = [], eventSink: @escaping (any FeedFilterEvent) -> Void
    ) {
        self.picker = picker
        self.eventSink = eventSink
        _query = State(initialValue: query)
        _expanded = State(initialValue: expanded)
    }

    var body: some View {
        let searching = SportTypeSearch.shared.searching(query: query)
        let groups = picker.groups(query: query, source: CatalogText.shared).elements(as: SportTypeGroup.self)
        NavigationStack {
            List {
                if !searching {
                    Section {
                        // Picked or not, as the shared quick chips are: a part-picked sport shows its part in its category below.
                        ForEach(picker.quickTypes.elements(as: KotlinActivityType.self), id: \.key) { type in
                            let mark: FeedFilterSelection = picker.selectionOf(type: type) == .All ? .All : .None
                            row(Text(LabelText.shared.activityType(type: type)), type: type, mark: mark) {
                                eventSink(FeedFilterEvent.ToggleType(type: type))
                            }
                        }
                    } header: {
                        Text(.sportPickerQuick)
                    }
                }
                if groups.isEmpty {
                    Text(.sportPickerNoMatches).foregroundStyle(.secondary)
                }
                ForEach(groups, id: \.category) { group in
                    Section {
                        ForEach(group.rows.elements(as: SportTypeRow.self), id: \.type.key) { sport in
                            let nested = sport.nested.elements(as: KotlinActivityType.self)
                            if nested.isEmpty {
                                typeRow(sport.type)
                            } else {
                                // A search shows every match, so none hides behind a collapsed sport.
                                DisclosureGroup(isExpanded: searching ? .constant(true) : isExpanded(sport.type.sport)) {
                                    ForEach(nested, id: \.key) { typeRow($0) }
                                } label: {
                                    typeRow(sport.type)
                                }
                            }
                        }
                    } header: {
                        if searching {
                            // A category would pick types the search hides, so its header is plain.
                            Text(group.label)
                        } else {
                            Button { eventSink(FeedFilterEvent.ToggleCategory(category: group.category)) } label: {
                                HStack {
                                    Text(group.label)
                                    Spacer()
                                    SelectionMark(selection: picker.selectionOf(category: group.category))
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: Text(.sportPickerSearch))
            .navigationTitle(Text(.feedFilterTypesTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.actionCancel) { eventSink(FeedFilterEvent.DismissTypes.shared) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(.feedFilterApply) { eventSink(FeedFilterEvent.ApplyTypes.shared) }
                }
            }
        }
    }

    private func isExpanded(_ sport: String) -> Binding<Bool> {
        Binding(
            get: { expanded.contains(sport) },
            set: { open in if open { expanded.insert(sport) } else { expanded.remove(sport) } })
    }

    /// A type's row, marked by how much of it the draft holds: a sport with only some of its sub-sports picked shows the part mark.
    private func typeRow(_ type: KotlinActivityType) -> some View {
        row(Text(LabelText.shared.activityType(type: type)), type: type, mark: picker.selectionOf(type: type)) {
            eventSink(FeedFilterEvent.ToggleType(type: type))
        }
    }

    private func row(_ label: Text, type: KotlinActivityType, mark: FeedFilterSelection, toggle: @escaping () -> Void) -> some View {
        Button(action: toggle) {
            HStack(spacing: 12) {
                SportIcon(type).foregroundStyle(.tint)
                label.foregroundStyle(.primary)
                Spacer()
                SelectionMark(selection: mark)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(mark == .All ? .isSelected : [])
    }
}

/// How much of a type or category is picked: a filled circle with a check, with a dash for part, or an empty circle.
private struct SelectionMark: View {
    let selection: FeedFilterSelection

    var body: some View {
        Image(systemName: symbol).foregroundStyle(selection == .None ? AnyShapeStyle(.tertiary) : AnyShapeStyle(.tint))
            .accessibilityHidden(true)
    }

    private var symbol: String {
        switch selection {
        case .All: "checkmark.circle.fill"
        case .Partial: "minus.circle.fill"
        default: "circle"
        }
    }
}

/// A custom range, a day at a time: its first day, then its last (the presenter sorts them). Neither can be after today, and the last
/// can't be before the first.
struct CustomRangeSheet: View {
    let range: CustomRangeState
    let eventSink: (any FeedFilterEvent) -> Void
    @Environment(\.calendar) private var calendar
    @Environment(\.clock) private var clock
    @State private var picked = Date()

    var body: some View {
        let start = range.start.map { CalendarDay($0).date(in: calendar) }
        NavigationStack {
            DatePicker(selection: $picked, in: (start ?? .distantPast)...clock.date, displayedComponents: .date) {
                Text(.feedFilterCustom)
            }
            .datePickerStyle(.graphical)
            .padding()
            .navigationTitle(Text(.feedFilterCustom))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.actionCancel) { eventSink(FeedFilterEvent.DismissCustomRange.shared) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(.actionDone) {
                        eventSink(FeedFilterEvent.PickRangeDate(date: CalendarDay(picked, in: calendar).localDate))
                    }
                }
            }
        }
        // The second pick starts from the first day, so the picker opens where the range does.
        .onChange(of: range.start == nil, initial: true) { if let start { picked = start } }
        .presentationDetents([.medium, .large])
    }
}

#if DEBUG
    #Preview("Filter bar") {
        SamplePages(Samples.feedFilter) { FeedFilterBar(state: $0).padding() }
    }

    #Preview("Type picker") {
        if let picker = Samples.feedFilter["picking"]?.typePicker {
            TypePickerSheet(picker: picker) { _ in }
        }
    }

    #Preview("Type picker, searching") {
        if let picker = Samples.feedFilter["picking"]?.typePicker {
            TypePickerSheet(picker: picker, query: "tra") { _ in }
        }
    }
#endif
