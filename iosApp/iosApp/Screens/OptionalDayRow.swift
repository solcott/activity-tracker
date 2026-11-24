import SwiftUI

/// A day the athlete may leave unset, as a grouped form's row (a birthdate, the day gear was got): `label`, then the day written out
/// (Kotlin's `shown`), which opens a calendar of past days in a popover, and Clear; or, while it's unset, a calendar button that opens
/// it. The calendar is the system's `DatePicker`; picking a day closes it.
struct OptionalDayRow: View {
    let label: LocalizedStringResource
    /// The day, written out; nil while it's unset.
    let shown: (any AppText)?
    let day: KotlinLocalDate?
    let chooseLabel: LocalizedStringResource
    let clearLabel: LocalizedStringResource
    let pick: (KotlinLocalDate) -> Void
    let clear: () -> Void

    @State private var picking = false
    @Environment(\.clock) private var clock
    @Environment(\.calendar) private var calendar

    var body: some View {
        HStack(spacing: 12) {
            Text(label).frame(maxWidth: .infinity, alignment: .leading)
            if let shown {
                Button {
                    picking = true
                } label: {
                    Text(shown)
                }
                .accessibilityHint(Text(chooseLabel))
                AppIconButton(.clear, label: clearLabel, action: clear).foregroundStyle(.secondary)
            } else {
                AppIconButton(.calendar, label: chooseLabel) { picking = true }
            }
        }
        // Each button takes only its own taps, not the whole row's.
        .buttonStyle(.borderless)
        .popover(isPresented: $picking) {
            DatePicker(
                selection: Binding(
                    get: { day.map { CalendarDay($0).date(in: calendar) } ?? clock.date },
                    set: { picked in
                        picking = false
                        pick(CalendarDay(picked, in: calendar).localDate)
                    }),
                in: ...clock.date, displayedComponents: .date
            ) {
                Text(label)
            }
            .datePickerStyle(.graphical)
            .padding()
            .frame(minWidth: 320)
            .presentationCompactAdaptation(.popover)
        }
    }
}
