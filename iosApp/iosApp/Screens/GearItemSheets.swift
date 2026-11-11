import SwiftUI

/// Choosing one of the athlete's items to mount on the page's item from today, or to replace a component with (gear/gear-components):
/// each by its name and kind, or that none can be mounted; New item adds one first. Titled by Kotlin (`GearPageTexts.pickerTitle`), as
/// the shared sheet is, with Cancel and New item in its own bar.
struct MountSheet: View {
    let picker: GearMountPicker
    let send: (any GearEvent) -> Void

    var body: some View {
        NavigationStack {
            list
                .navigationTitle(Text(GearPageTexts.shared.pickerTitle(picker: picker)))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(.actionCancel) { send(GearEvent.DismissMount.shared) }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(.gearMountNew) { send(GearEvent.MountNew.shared) }
                    }
                }
        }
    }

    @ViewBuilder private var list: some View {
        let candidates = picker.candidates.elements(as: GearMountCandidate.self)
        if candidates.isEmpty {
            Text(.gearMountNone)
                .foregroundStyle(.secondary)
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else {
            List {
                ForEach(candidates, id: \.rkey) { candidate in
                    AppItemRow(
                        Text(verbatim: candidate.name), lines: [Text(GearPageTexts.shared.candidateLine(candidate: candidate))],
                        opens: { send(GearEvent.ChooseMount(rkey: candidate.rkey)) }
                    ) {
                        EmptyView()
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }
}

/// The day a component's mount ends (gear/gear-components): a calendar from `firstDay`, the day after it went on, showing `initialDay`
/// (today, or the first day when that is later), both Kotlin's (`GearState.unmountFirstDay`, `unmountInitialDay`). The pick is kept here
/// until Unmount sends it, as the shared dialog confirms; Cancel sends nothing but the dismissal.
struct UnmountSheet: View {
    let firstDay: KotlinLocalDate
    let initialDay: KotlinLocalDate
    let send: (any GearEvent) -> Void
    /// The day chosen so far; `initialDay` until the athlete picks one.
    @State private var picked: CalendarDay?
    @Environment(\.calendar) private var calendar

    private var selection: Binding<Date> {
        Binding(
            get: { (picked ?? CalendarDay(initialDay)).date(in: calendar) },
            set: { picked = CalendarDay($0, in: calendar) })
    }

    var body: some View {
        NavigationStack {
            DatePicker(selection: selection, in: CalendarDay(firstDay).date(in: calendar)..., displayedComponents: .date) {
                Text(.gearComponentUnmount)
            }
            .datePickerStyle(.graphical)
            .padding()
            .frame(maxHeight: .infinity, alignment: .top)
            .navigationTitle(Text(.gearComponentUnmount))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.actionCancel) { send(GearEvent.DismissUnmount.shared) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(.gearComponentUnmount) {
                        send(GearEvent.ConfirmUnmount(date: (picked ?? CalendarDay(initialDay)).localDate))
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

#if DEBUG
    #Preview("Mount sheet") {
        SamplePages(Samples.gearVariants.filter { $0.value.picker != nil }) { state in
            MountSheet(picker: state.picker!, send: { _ in })
        }
    }

    #Preview("Unmount date") {
        UnmountSheet(
            firstDay: KotlinLocalDate(year: 2026, month: 7, day: 2), initialDay: KotlinLocalDate(year: 2026, month: 11, day: 11),
            send: { _ in })
    }
#endif
