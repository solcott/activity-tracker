import SwiftUI

/// A gear item's page (gear/gear-usage, gear/gear-components, gear/gear-sensors), drawn from the states `GearPresenter` publishes: its
/// photo and details, the owner's collections ticked in place, its components with the owner's Replace and Unmount and Mount a
/// component, what was mounted before and what it was mounted on, its linked devices and profiles with Unlink, its totals with any
/// starting usage, its breakdowns by activity type and by year, then the activities counted, newest first, page by page, as native
/// cards. Edit is in the bar on the owner's page. Every line and condition is Kotlin's (`GearPageTexts`, `LinkedSensorTexts`, getters on
/// `GearState`), shared with the Compose page. The mount sheet and the unmount calendar present over it (`GearItemSheets.swift`).
///
/// The view is `GearItemView` because `GearView` is the lexicon class it shows.
struct GearItemView: View {
    let state: GearState
    @Environment(\.stats) private var stats
    @Environment(\.clock) private var clock

    var body: some View {
        content
            .pageTitle(Text(verbatim: state.title)) {
                if state.showsEdit {
                    AppTextButton(.gearPageEdit) { send(GearEvent.Edit.shared) }
                }
            }
            .sheet(
                isPresented: Binding(get: { state.picker != nil }, set: { if !$0 { send(GearEvent.DismissMount.shared) } })
            ) {
                if let picker = state.picker {
                    MountSheet(picker: picker, send: send)
                }
            }
            .sheet(
                isPresented: Binding(get: { state.unmounting != nil }, set: { if !$0 { send(GearEvent.DismissUnmount.shared) } })
            ) {
                if let firstDay = state.unmountFirstDay(zone: clock.kotlinZone) {
                    UnmountSheet(
                        firstDay: firstDay, initialDay: state.unmountInitialDay(now: clock.now, zone: clock.kotlinZone) ?? firstDay,
                        send: send)
                }
            }
    }

    @ViewBuilder private var content: some View {
        if state.notFound {
            // An item the viewer can't see says so, with nothing to retry.
            Text(.gearNotFound)
                .foregroundStyle(.secondary)
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else {
            // The item is read from `page`, typed, never from the content's erased value.
            ContentStateView(state.content, errorKind: .LoadGearItem, retry: retry) {
                if let page = state.page {
                    loaded(page)
                }
            }
        }
    }

    private func loaded(_ page: GearPage) -> some View {
        PagedList(
            state.activities, of: ActivityView.self, errorKind: .LoadGearActivities, id: { $0.uri.value }, keepsTop: true,
            sections: { activities in activities.map { PagedSection(id: $0.uri.value, items: [$0]) } }, retry: retry,
            loadMore: { send(GearEvent.LoadMore.shared) }
        ) {
            RefreshStatusView(state: state.content, errorKind: .LoadGearItem, retry: retry).listStatusRow()
            Section { GearHeader(page: page) }
            if state.showsCollections {
                collections
            }
            if state.showsComponents {
                components(page)
            }
            mounts(page)
            linkedSensors
            linkedProfiles
            totals(page)
            breakdown(.gearPageBySport, GearPageTexts.shared.bySport(page: page, units: stats.units))
            breakdown(.gearPageByYear, GearPageTexts.shared.byYear(page: page, units: stats.units))
            // The activities' heading, with why the last action failed (a like, a collection change, an unlink) under it.
            Section {
                if let error = state.activitiesError {
                    ErrorMessage(error, layout: .row).listStatusRow()
                }
            } header: {
                Text(.gearPageActivities)
            }
        } row: { activity in
            card(activity)
        } empty: {
            Text(.gearPageNoActivities).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
        }
    }

    /// The athlete's collections, each ticked when the item is in it; a tap adds or removes it at once.
    private var collections: some View {
        Section {
            ForEach(state.collections.elements(as: GearCollectionChoice.self), id: \.rkey) { choice in
                Button { send(GearEvent.ToggleCollection(rkey: choice.rkey)) } label: {
                    HStack {
                        Text(verbatim: choice.name).foregroundStyle(.primary)
                        Spacer()
                        if choice.member {
                            Image(systemName: "checkmark").foregroundStyle(.tint).accessibilityHidden(true)
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(choice.member ? .isSelected : [])
            }
        } header: {
            Text(.gearCollections)
        }
    }

    /// What is mounted on the item now, a failed mount change, Mount a component, and what was mounted before.
    @ViewBuilder private func components(_ page: GearPage) -> some View {
        Section {
            ForEach(page.components.elements(as: GearComponent.self), id: \.gear.uri.value) { component in
                AppItemRow(
                    Text(verbatim: component.gear.name),
                    lines: [
                        Text(GearPageTexts.shared.componentLine(component: component, units: stats.units)),
                        Text(GearPageTexts.shared.componentDates(component: component, zone: clock.kotlinZone)),
                    ],
                    opens: { send(GearEvent.OpenGear(uri: component.gear.uri.value)) }
                ) {
                    EmptyView()
                } trailing: {
                    if state.managesComponents {
                        componentMenu(component)
                    }
                }
            }
            if let error = state.componentsError {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
            if state.managesComponents {
                Button(.gearComponentsMount) { send(GearEvent.Mount.shared) }
                    .disabled(!state.mountEnabled)
            }
        } header: {
            Text(.gearComponents)
        }
        let previous = page.previous.elements(as: GearComponent.self)
        if !previous.isEmpty {
            Section {
                ForEach(Array(previous.enumerated()), id: \.offset) { _, component in
                    gearRow(
                        component.gear,
                        lines: [
                            Text(GearPageTexts.shared.componentLine(component: component, units: stats.units)),
                            Text(GearPageTexts.shared.componentDates(component: component, zone: clock.kotlinZone)),
                        ])
                }
            } header: {
                Text(.gearComponentsPrevious)
            }
        }
    }

    /// What the item itself was mounted on, oldest first.
    @ViewBuilder private func mounts(_ page: GearPage) -> some View {
        let mounts = page.mounts.elements(as: GearMountView.self)
        if !mounts.isEmpty {
            Section {
                ForEach(Array(mounts.enumerated()), id: \.offset) { _, mount in
                    gearRow(
                        mount.parent,
                        lines: [
                            Text(GearPageTexts.shared.kindLine(gear: mount.parent)),
                            Text(GearPageTexts.shared.mountDates(mount: mount, zone: clock.kotlinZone)),
                        ])
                }
            } header: {
                Text(.gearMountedOn)
            }
        }
    }

    /// Another item, opened by a tap.
    private func gearRow(_ gear: LexiconGearView, lines: [Text]) -> some View {
        AppItemRow(Text(verbatim: gear.name), lines: lines, opens: { send(GearEvent.OpenGear(uri: gear.uri.value)) }) {
            EmptyView()
        }
    }

    /// Replace and Unmount for a component mounted now, waiting while a mount change saves.
    private func componentMenu(_ component: GearComponent) -> some View {
        let uri = component.gear.uri.value
        return Menu {
            Button(.gearComponentReplace) { send(GearEvent.Replace(uri: uri)) }
            Button(.gearComponentUnmount) { send(GearEvent.Unmount(uri: uri)) }
        } label: {
            Image(.more).imageScale(.large).frame(minWidth: 44, minHeight: 44)
        }
        .accessibilityLabel(Text(GearPageTexts.shared.componentActions(component: component)))
        .disabled(!state.mountEnabled)
    }

    /// The devices linked to the item, each with Unlink; only on the owner's page.
    @ViewBuilder private var linkedSensors: some View {
        let sensors = state.linkedSensors.elements(as: LinkedSensor.self)
        if !sensors.isEmpty {
            Section {
                ForEach(sensors, id: \.id) { sensor in
                    AppItemRow(
                        Text(LinkedSensorTexts.shared.name(sensor: sensor)),
                        lines: LinkedSensorTexts.shared.detail(sensor: sensor).map { [Text($0)] } ?? []
                    ) {
                        EmptyView()
                    } trailing: {
                        AppSecondaryButton(.gearUnlink, small: true) { send(GearEvent.UnlinkSensor(id: sensor.id)) }
                            .accessibilityLabel(Text(LinkedSensorTexts.shared.unlink(sensor: sensor)))
                    }
                }
            } header: {
                Text(.gearLinkedSensors)
            }
        }
    }

    /// The activity profiles linked to the item, each with Unlink; only on the owner's page.
    @ViewBuilder private var linkedProfiles: some View {
        let profiles = state.linkedProfiles.elements(as: String.self)
        if !profiles.isEmpty {
            Section {
                ForEach(profiles, id: \.self) { name in
                    AppItemRow(Text(verbatim: name)) {
                        EmptyView()
                    } trailing: {
                        AppSecondaryButton(.gearUnlink, small: true) { send(GearEvent.UnlinkProfile(name: name)) }
                            .accessibilityLabel(Text(.gearUnlinkNamed(name)))
                    }
                }
            } header: {
                Text(.gearLinkedProfiles)
            }
        }
    }

    /// Every total that applies to the item's type, and the starting usage they include.
    private func totals(_ page: GearPage) -> some View {
        Section {
            figures(GearPageTexts.shared.figures(page: page, units: stats.units))
        } header: {
            Text(.gearPageTotals)
        } footer: {
            if let starting = GearPageTexts.shared.starting(starting: page.usage.starting, units: stats.units) {
                Text(starting)
            }
        }
    }

    /// A breakdown under its heading; none when Kotlin gives no rows (an item of one activity type, or of one year).
    @ViewBuilder private func breakdown(_ title: LocalizedStringResource, _ rows: [GearFigure]) -> some View {
        if !rows.isEmpty {
            Section {
                figures(rows)
            } header: {
                Text(title)
            }
        }
    }

    private func figures(_ figures: [GearFigure]) -> some View {
        ForEach(Array(figures.enumerated()), id: \.offset) { _, figure in
            LabeledContent { Text(figure.value) } label: { Text(figure.label) }
        }
    }

    private func card(_ activity: ActivityView) -> some View {
        let uri = activity.uri.value
        return ActivityCardView(
            content: ActivityCardContent.Companion.shared.of(activity: activity, viewerDid: state.viewerDid),
            authorClickable: state.authorClickable,
            revealed: state.revealed.contains(key: uri),
            send: { send(GearEvent.Activity(event: $0)) },
            reveal: { state.revealed.eventSink(uri) }
        )
    }

    private func retry() {
        send(GearEvent.Retry.shared)
    }

    private func send(_ event: any GearEvent) {
        state.eventSink(event)
    }
}

/// The item's photo beside its name, kind, brand and model, and the owner's visibility and Retired notes.
private struct GearHeader: View {
    let page: GearPage

    var body: some View {
        HStack(spacing: 16) {
            Thumbnail(url: page.gear.photo, size: 88)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: page.gear.name).font(.title3.weight(.semibold))
                Text(GearPageTexts.shared.kindLine(gear: page.gear)).font(.subheadline)
                if let maker = GearPageTexts.shared.maker(gear: page.gear) {
                    Text(maker).font(.subheadline).foregroundStyle(.secondary)
                }
                if let notes = GearPageTexts.shared.notes(page: page) {
                    Text(notes).font(.footnote).foregroundStyle(.tint)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

#if DEBUG
    #Preview("Gear item") {
        SamplePages(Samples.gear) { state in NavigationStack { GearItemView(state: state) } }
            .environment(\.clock, Samples.clock)
    }

    #Preview("Gear item, beyond loading") {
        SamplePages(Samples.gearVariants) { state in NavigationStack { GearItemView(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
