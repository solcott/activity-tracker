import SwiftUI

/// An account in a list: their picture, the name they're shown by and their `@handle`, with any lines a list adds under the handle
/// (a block's note). A row that opens their profile and has no control of its own carries the disclosure mark; one with a control at
/// its trailing end (Remove, Approve) keeps it outside the row's own button, as `AppItemRow` lays them out.
struct AccountRow<Trailing: View>: View {
    private let account: ProfileViewBasic
    private let lines: [Text]
    private let opens: (() -> Void)?
    private let trailing: Trailing?

    /// A row with a control at its trailing end; tapping the rest of it opens what `opens` opens.
    init(
        _ account: ProfileViewBasic, lines: [Text] = [], opens: (() -> Void)? = nil, @ViewBuilder trailing: () -> Trailing
    ) {
        self.account = account
        self.lines = lines
        self.opens = opens
        self.trailing = trailing()
    }

    var body: some View {
        let name = AppPackage.getShownName(account)
        let title = Text(verbatim: name)
        let shownLines = [Text(LabelText.shared.handle(handle: account.handle.value))] + lines
        if let trailing {
            AppItemRow(title, lines: shownLines, opens: opens) {
                Avatar(url: account.avatar, name: name)
            } trailing: {
                trailing
            }
        } else {
            AppItemRow(title, lines: shownLines, opens: opens) {
                Avatar(url: account.avatar, name: name)
            }
        }
    }
}

extension AccountRow where Trailing == EmptyView {
    /// A row with no control of its own: it opens their profile, with the disclosure mark, or only shows them when `opens` is nil.
    init(_ account: ProfileViewBasic, lines: [Text] = [], opens: (() -> Void)? = nil) {
        self.account = account
        self.lines = lines
        self.opens = opens
        trailing = nil
    }
}

/// Who liked or reposted something, under its toggle: progress while they load, why they couldn't, `empty` when no one has (a list
/// that is only offered with likes passes none), or a row per athlete opening their profile.
struct LikersList: View {
    let list: LikesList
    var empty: Text?
    let open: (ProfileViewBasic) -> Void

    var body: some View {
        let athletes = list.athletes.elements(as: ProfileViewBasic.self)
        if list.isLoading {
            AppProgress(small: true).frame(maxWidth: .infinity)
        } else if let error = list.error {
            ErrorMessage(error, layout: .row)
        } else if athletes.isEmpty, let empty {
            empty.foregroundStyle(.secondary)
        } else {
            ForEach(athletes, id: \.did.value) { athlete in
                AccountRow(athlete, opens: { open(athlete) })
            }
        }
    }
}
