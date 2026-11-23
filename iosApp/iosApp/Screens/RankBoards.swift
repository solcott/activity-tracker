import SwiftUI

// The pieces of a ranked board (segments' leaderboards and Mayors, dive sites' Regulars) the three native boards share, as the shared
// `RankBoards.kt` is. What each choice offers and says, and which ranks are accented, is Kotlin's (`BoardChoice`, `RankBoardTexts`,
// `SegmentBoardTexts`, `DiveSiteRegularsTexts`, the states' getters).

/// A board's choices over its entries: the segmented one, when there is one, then the menus in a row that wraps, together in one of
/// the list's status rows, so they read as part of the page and take one row's padding.
struct BoardFilters<Segments: View, Menus: View>: View {
    private let segments: Segments
    private let menus: Menus

    init(@ViewBuilder segments: () -> Segments, @ViewBuilder menus: () -> Menus) {
        self.segments = segments()
        self.menus = menus()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if Segments.self != EmptyView.self {
                segments
            }
            // Wrapping onto a second line when they don't fit, as the shared filters' FlowRow does.
            FlowRow { menus }
        }
        .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
        .listStatusRow()
    }
}

/// A board's choice as a segmented control of its options (All time, This year, This month), sending the option chosen.
struct BoardSegments: View {
    private let choice: BoardChoice
    private let send: (String?) -> Void

    init(_ choice: BoardChoice, send: @escaping (String?) -> Void) {
        self.choice = choice
        self.send = send
    }

    var body: some View {
        let options = choice.options.elements(as: BoardOption.self)
        Picker(
            selection: Binding(
                get: { options.firstIndex(where: choice.isChosen(option:)) ?? 0 },
                set: { send(options[$0].value) })
        ) {
            ForEach(options.indices, id: \.self) { index in
                Text(options[index].label).tag(index)
            }
        } label: {
            Text(choice.label)
        }
        .pickerStyle(.segmented)
        .labelsHidden()
    }
}

/// A segment board's choices as menus in the caller's row (`BoardFilterFields`, shared by the leaderboard and the Mayors board): the date
/// range, the scope, the type while there are several to choose, then the age group, weight class (in `units`) and gender, each its own
/// chip naming its choice, each choice sent to the fields' own sink.
struct SegmentBoardMenus: View {
    let fields: BoardFilterFields
    let units: UnitSystem

    var body: some View {
        BoardMenu(fields.periodChoice) { if let period = $0 { fields.eventSink(BoardFilterEvent.PeriodChanged(period: period)) } }
        BoardMenu(fields.scopeChoice) { if let scope = $0 { fields.eventSink(BoardFilterEvent.ScopeChanged(scope: scope)) } }
        if let type = fields.typeChoice {
            BoardMenu(type) { if let board = $0 { fields.eventSink(BoardFilterEvent.BoardChanged(board: board)) } }
        }
        BoardMenu(fields.ageGroupChoice) { fields.eventSink(BoardFilterEvent.AgeGroupChanged(ageGroup: $0)) }
        BoardMenu(fields.weightClassChoice(units: units)) { fields.eventSink(BoardFilterEvent.WeightClassChanged(weightClass: $0)) }
        BoardMenu(fields.genderChoice) { fields.eventSink(BoardFilterEvent.GenderChanged(gender: $0)) }
    }
}

extension View {
    /// Pins a board's `chips` in one row under the navigation bar (segments/segment-leaderboards), scrolling sideways when they don't fit
    /// and never wrapping, with the list starting right under them. The list scrolls under the row as it does under the system's bar:
    /// with the scroll edge effect on iOS 26, and over the bar material with a hairline before it.
    func boardChips(@ViewBuilder _ chips: () -> some View) -> some View {
        let row = ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) { chips() }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
        }
        .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        return contentMargins(.top, 4, for: .scrollContent).chipBar(row)
    }

    @ViewBuilder fileprivate func chipBar(_ row: some View) -> some View {
        if #available(iOS 26.0, *) {
            safeAreaBar(edge: .top, spacing: 0) { row }
        } else {
            safeAreaInset(edge: .top, spacing: 0) {
                row
                    .background { Rectangle().fill(.bar).ignoresSafeArea(edges: .top) }
                    .overlay(alignment: .bottom) { Divider() }
            }
        }
    }
}

/// A board's choice as a filter menu of its options, filled while it narrows the board, or its label alone when there is only one
/// option (a segment with one type).
struct BoardMenu: View {
    private let choice: BoardChoice
    private let send: (String?) -> Void

    init(_ choice: BoardChoice, send: @escaping (String?) -> Void) {
        self.choice = choice
        self.send = send
    }

    var body: some View {
        let options = choice.options.elements(as: BoardOption.self)
        if choice.offersMenu {
            AppMenuChip(
                nil, filled: choice.narrows,
                selection: Binding(
                    get: { options.firstIndex(where: choice.isChosen(option:)) ?? 0 },
                    set: { send(options[$0].value) }),
                options: Array(options.indices), optionLabel: { AppStrings.string(options[$0].label) })
        } else {
            Text(choice.label).font(.subheadline.weight(.medium)).padding(.vertical, 6)
        }
    }
}

/// One athlete's place on a board: the rank (accented on the podium or for the leader, and a crown for a `crowned` Mayor), their picture and name, a line under it (the
/// day of the effort, or the day the count was reached), a `note` under that when there is one (the viewer's gap to first), and the
/// value (a time or a count) in figures of equal width, kept whole while the name wraps, opening what the entry opens. The viewer's
/// `own` row has a tinted background in a list and a heavier name, and says "You" to VoiceOver.
struct RankRow: View {
    let rank: any AppText
    let accented: Bool
    let athlete: ProfileViewBasic
    let detail: any AppText
    let value: String
    var own = false
    var note: (any AppText)?
    var crowned = false
    let opens: () -> Void

    @ScaledMetric(relativeTo: .headline) private var rankWidth: CGFloat = 26
    @ScaledMetric(relativeTo: .headline) private var crownSize: CGFloat = 26

    var body: some View {
        let name = AppPackage.getShownName(athlete)
        AppItemRow(
            // The day in the footnote's size, as the shared row's small line; it wraps beside a long count rather than shrinking.
            Text(verbatim: name).fontWeight(own ? .semibold : nil), lines: [Text(detail).font(.footnote)] + noteLine,
            summary: Text(verbatim: value).font(.headline.monospacedDigit()).foregroundStyle(.primary), opens: opens
        ) {
            HStack(spacing: 10) {
                // The list insets a separator to a row's first text, which a centred rank moves and the crown lacks, so every
                // row's separator starts at the rank column instead.
                if crowned {
                    crown
                } else {
                    Text(rank)
                        .font(.headline.monospacedDigit())
                        .foregroundStyle(accented ? Color(.like) : Color.primary)
                        // Centred, so every rank and the Mayor's crown share one centre line.
                        .frame(minWidth: rankWidth, alignment: .center)
                        // A long rank ("140") widens its column rather than wrapping.
                        .fixedSize()
                        .alignmentGuide(.listRowSeparatorLeading) { $0[.leading] }
                }
                // The shared row's size, leaving the day line room beside a count.
                Avatar(url: athlete.avatar, name: name, size: 36)
            }
        }
        .accessibilityValue(own ? Text(.rankBoardYou) : Text(verbatim: ""))
        .listRowBackground(own ? Color.accentColor.opacity(0.12) : nil)
    }

    /// The Mayor's mark in the rank's place (segments/segment-mayors): a crown in the accent on its tint, said as "Mayor".
    private var crown: some View {
        Image(AppIcon.crown)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(Color(.like))
            .frame(width: crownSize, height: crownSize)
            .background(Color(.like).opacity(0.16), in: Circle())
            .frame(minWidth: rankWidth, alignment: .center)
            .alignmentGuide(.listRowSeparatorLeading) { $0[.leading] }
            .accessibilityLabel(Text(.mayorsMayor))
    }

    private var noteLine: [Text] {
        note.map { [Text($0).font(.footnote)] } ?? []
    }
}

/// The viewer's own place under "Your place", in a bar fixed to the bottom of a board while they aren't among the entries loaded: the
/// system's bar material with a hairline above it, which the list scrolls under and insets to.
struct YourPlace<Row: View>: View {
    private let row: Row

    init(@ViewBuilder row: () -> Row) {
        self.row = row()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(.leaderboardYourPlace).font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
                .accessibilityAddTraits(.isHeader)
            row
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.bar)
        .overlay(alignment: .top) { Divider() }
    }
}

extension View {
    /// This board with the viewer's own place, when `own` is set, pinned under it in a `YourPlace` bar.
    func pinnedPlace<Row: View>(_ own: Bool, @ViewBuilder row: @escaping () -> Row) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            if own {
                YourPlace(row: row)
            }
        }
    }
}

/// A board's leader in brief, on the page it belongs to (a segment's Mayor, a dive site's top Regular): the board's title, the leader's
/// picture, name and count, or that there is none yet, and the viewer's own count; a tap opens the board. Its words are Kotlin's.
struct LeaderCardView: View {
    let title: any AppText
    let leader: ProfileViewBasic?
    let value: (any AppText)?
    /// What it says while the board has no leader; nil for none at all, a board that hasn't loaded.
    let none: (any AppText)?
    let own: (any AppText)?
    /// Puts the value under the name, for a card that shares its row with another.
    var stacked = false
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(.tint)
                    if let leader {
                        let name = AppPackage.getShownName(leader)
                        HStack(spacing: 12) {
                            Avatar(url: leader.avatar, name: name, size: 36)
                            if stacked {
                                VStack(alignment: .leading, spacing: 0) {
                                    if let value {
                                        Text(value).font(.headline)
                                    }
                                    Text(verbatim: name).font(.subheadline).lineLimit(1)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            } else {
                                Text(verbatim: name).frame(maxWidth: .infinity, alignment: .leading)
                                if let value {
                                    Text(value).fontWeight(.medium)
                                }
                            }
                        }
                    } else if let none {
                        Text(none).foregroundStyle(.secondary)
                    }
                    if let own {
                        Text(own).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
            .foregroundStyle(.primary)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}
