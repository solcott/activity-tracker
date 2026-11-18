import SwiftUI

/// The athlete's gear collections, each ticked while the item is in it, under the Collections header: the gear page's, where a tap
/// adds or removes it at once, and the gear form's, where it is saved with the form. What a tap sends is the screen's.
struct GearCollectionsSection: View {
    let choices: [GearCollectionChoice]
    let toggle: (GearCollectionChoice) -> Void

    var body: some View {
        Section {
            ForEach(choices, id: \.rkey) { choice in
                AppCheckRow(checked: choice.member) { toggle(choice) } label: {
                    Text(verbatim: choice.name).foregroundStyle(.primary)
                }
            }
        } header: {
            Text(.gearCollections)
        }
    }
}
