import SwiftUI

// The pieces of a ranked board (segments' leaderboards and Mayors, dive sites' Regulars) the three native boards share, as the shared
// `RankBoards.kt` is. What each choice offers and says, and which ranks are accented, is Kotlin's (`BoardChoice`, `RankBoardTexts`,
// `SegmentBoardTexts`, `DiveSiteRegularsTexts`, the states' getters).

/// A board's choices over its entries: the segmented ones in a row of their own, then the menus in a row that wraps. Each is a list's
/// status row, so it reads as part of the page.
struct BoardFilters<Segments: View, Menus: View>: View {
    private let segments: Segments
    private let menus: Menus

    init(@ViewBuilder segments: () -> Segments, @ViewBuilder menus: () -> Menus) {
        self.segments = segments()
        self.menus = menus()
    }

    var body: some View {
        Group {
            VStack(spacing: 8) { segments }.listStatusRow()
            // Wrapping onto a second line when they don't fit, as the shared filters' FlowRow does.
            FlowRow { menus }.listStatusRow()
        }
    }
}

/// A board's choice as a segmented control of its options (Everyone, Following), sending the option chosen.
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

/// One athlete's place on a board: the rank (accented on the podium or for the leader), their picture and name, a line under it (the
/// day of the effort, or the day the count was reached) and the value (a time or a count), opening what the entry opens.
struct RankRow: View {
    let rank: any AppText
    let accented: Bool
    let athlete: ProfileViewBasic
    let detail: any AppText
    let value: String
    let opens: () -> Void

    @ScaledMetric(relativeTo: .headline) private var rankWidth: CGFloat = 26

    var body: some View {
        let name = AppPackage.getShownName(athlete)
        AppItemRow(
            // The day in the footnote's size, as the shared row's small line; it wraps beside a long count rather than shrinking.
            Text(verbatim: name), lines: [Text(detail).font(.footnote)],
            summary: Text(verbatim: value).foregroundStyle(.primary).fontWeight(.medium).monospacedDigit(), opens: opens
        ) {
            HStack(spacing: 10) {
                Text(rank)
                    .font(.headline.monospacedDigit())
                    .foregroundStyle(accented ? Color(.like) : Color.primary)
                    .frame(minWidth: rankWidth, alignment: .leading)
                    // A long rank ("140") widens its column rather than wrapping.
                    .fixedSize()
                // The shared row's size, leaving the day line room beside a count.
                Avatar(url: athlete.avatar, name: name, size: 36)
            }
        }
    }
}

/// The viewer's own place under "Your place", below a board's entries, when they aren't among those loaded.
struct YourPlace<Row: View>: View {
    private let row: Row

    init(@ViewBuilder row: () -> Row) {
        self.row = row()
    }

    var body: some View {
        Section {
            row
        } header: {
            Text(.leaderboardYourPlace)
        }
    }
}
