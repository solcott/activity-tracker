import SwiftUI

/// Accepting an invite to someone's activity (feed/activity-invites), drawn from the states `AcceptInvitePresenter` publishes: who added
/// the athlete and the activity's title and summary, its route as the line preview, what accepting copies, the overlap warning, the copy's
/// title, its visibility and its categories' (`VisibilityFormSections`), a failure with Sign in again when that is what failed, and
/// Decline and Accept, all held while either runs. A gone invite says so. What it says is Kotlin's (`AcceptInviteTexts`,
/// `ActivityInvitesTexts`, `AcceptInviteState`), shared with the Compose page.
struct AcceptInviteView: View {
    let state: AcceptInviteState
    @State private var title: TextFieldModel
    @Environment(\.stats) private var stats

    init(state: AcceptInviteState) {
        self.state = state
        _title = State(initialValue: TextFieldModel(state.title))
    }

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadActivityInvite, retry: retry) {
            Form {
                RefreshStatusView(state: state.content, errorKind: .LoadActivityInvite, retry: retry).listStatusRow()
                if let invite = state.invite {
                    loaded(invite)
                } else {
                    Section { Text(AcceptInviteTexts.shared.gone) }
                }
            }
        }
        .pageTitle(Text(AcceptInviteTexts.shared.title))
    }

    @ViewBuilder private func loaded(_ invite: InviteView) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 2) {
                Text(ActivityInvitesTexts.shared.from(invite: invite)).font(.footnote).foregroundStyle(.secondary)
                Text(ActivityInvitesTexts.shared.title(invite: invite)).font(.headline)
                Text(ActivityInvitesTexts.shared.summary(invite: invite, units: stats.units)).font(.subheadline)
            }
            .accessibilityElement(children: .combine)
            if let map = state.routeMap {
                RouteSketch { size in map.sketch(width: size.width, height: size.height, pad: RouteMarkerStyle.sketchPad) }
                    .frame(height: AcceptInviteLayout.routeHeight)
                    .listRowInsets(EdgeInsets())
            }
        } footer: {
            VStack(alignment: .leading, spacing: 8) {
                Text(AcceptInviteTexts.shared.intro(invite: invite))
                if state.overlaps {
                    Text(AcceptInviteTexts.shared.overlap).foregroundStyle(.red)
                }
            }
        }
        Section {
            AppTextField(label: .fieldTitle, model: title, state: state.title, layout: .row)
                .disabled(state.busy)
        }
        VisibilityFormSections(
            fields: state.visibility, heading: Text(.activityVisibilityHeading),
            activityEnabled: !state.busy && state.visibility.canChoose, categoriesEnabled: !state.busy
        ) {
            Text(VisibilityTexts.shared.activityNote(fields: state.visibility))
        } detailsNote: {
            EmptyView()
        }
        if let error = state.error {
            Section {
                ErrorMessage(error, layout: .row)
                if state.offersSignInAgain {
                    AppSecondaryButton(.actionSignInAgain) { send(AcceptInviteEvent.SignInAgain.shared) }
                }
            }
        }
        Section {
            HStack(spacing: 8) {
                if state.canDecline {
                    AppSecondaryButton(AcceptInviteTexts.shared.decline) { send(AcceptInviteEvent.Decline.shared) }
                        .disabled(state.busy)
                }
                AppButton(AcceptInviteTexts.shared.accept(accepting: state.accepting)) { send(AcceptInviteEvent.Accept.shared) }
                    .disabled(state.busy)
            }
            .listStatusRow()
        }
    }

    private func retry() {
        send(AcceptInviteEvent.Retry.shared)
    }

    private func send(_ event: any AcceptInviteEvent) {
        state.eventSink(event)
    }
}

/// The route preview's height, as the shared page's line preview.
enum AcceptInviteLayout {
    static let routeHeight: CGFloat = 160
}

#if DEBUG
    #Preview("Accept invite") {
        SamplePages(Samples.acceptInvite) { state in NavigationStack { AcceptInviteView(state: state) } }
    }
#endif
