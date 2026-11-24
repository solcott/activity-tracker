import SwiftUI

/// Activity types by category (`SportTypeGroups`), one section each, for a sheet's `List`: a sport with sub-sports under it
/// (`SportTypeRow.nested`) discloses them, collapsed until opened, and while `searching` every one is open, so no match hides behind a
/// collapsed sport. Each page draws its own rows and headers: `row` gets a type and, for a collapsed sport's own row, the sub-sports it
/// hides (empty otherwise).
struct SportTypeGroupsList<Header: View, Row: View>: View {
    let groups: [SportTypeGroup]
    var searching = false
    /// The sports disclosed, by their token.
    @Binding var expanded: Set<String>
    @ViewBuilder let header: (SportTypeGroup) -> Header
    @ViewBuilder let row: (KotlinActivityType, _ collapsed: [KotlinActivityType]) -> Row

    var body: some View {
        ForEach(groups, id: \.category) { group in
            Section {
                ForEach(group.rows.elements(as: SportTypeRow.self), id: \.type.key) { sport in
                    let nested = sport.nested.elements(as: KotlinActivityType.self)
                    if nested.isEmpty {
                        row(sport.type, [])
                    } else {
                        let open = searching || expanded.contains(sport.type.sport)
                        DisclosureGroup(isExpanded: searching ? .constant(true) : isExpanded(sport.type.sport)) {
                            ForEach(nested, id: \.key) { row($0, []) }
                        } label: {
                            row(sport.type, open ? [] : nested)
                        }
                    }
                }
            } header: {
                header(group)
            }
        }
    }

    private func isExpanded(_ sport: String) -> Binding<Bool> {
        Binding(
            get: { expanded.contains(sport) },
            set: { open in if open { expanded.insert(sport) } else { expanded.remove(sport) } })
    }
}
