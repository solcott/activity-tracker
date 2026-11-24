import Foundation

/// SwiftUI's move of a list's row, as the collection form's `Move` takes it (`EditGearCollectionEvent.Move`).
enum MemberMove {
    /// The move of the row at `source` to before the row at `destination` in the old order (`onMove`'s offsets), as Kotlin's `from` and
    /// `to`, both indexes into the list, `to` the row's index once moved. Nil when the row lands where it was.
    static func of(source: IndexSet, destination: Int) -> (from: Int, to: Int)? {
        guard source.count == 1, let from = source.first else { return nil }
        let to = destination > from ? destination - 1 : destination
        return to == from ? nil : (from, to)
    }
}

/// The items ticked in the collection form's Add items, in the order ticked: Add adds them in that order, as the shared sheet does.
struct TickedItems: Equatable {
    private(set) var rkeys: [String] = []

    func isTicked(_ rkey: String) -> Bool { rkeys.contains(rkey) }

    /// Ticks `rkey` last, or unticks it; ticking it again puts it last.
    mutating func toggle(_ rkey: String) {
        if let index = rkeys.firstIndex(of: rkey) {
            rkeys.remove(at: index)
        } else {
            rkeys.append(rkey)
        }
    }
}
