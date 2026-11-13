import SwiftUI

/// An athlete's profile (graph/follow-requests, graph/blocks, graph/mutes), drawn from the states `ProfilePresenter` publishes: the header
/// (avatar, handle, the labels' notices, bio, the details the viewer may see, the counts opening the follow lists), one row of actions (the
/// athlete's own entries, Follow, Unblock or the Bluesky note), and their activities as native cards, loaded on their own below. Settings
/// sits in the bar on the athlete's own profile, and a menu with Mute and Block on anyone else's. Every word and condition is Kotlin's
/// (`ProfileTexts`, `ProfileState`), shared with the Compose page.
///
/// The Activities and Posts choice sits under the actions, and Posts lists native post cards (`ProfilePosts`, ios-native-post-card), with
/// the quote sheet. The menu's Report opens the native report sheet (`reporting(_:)`, ios-native-report-sheet). Named `ProfilePage`
/// because `ProfileView` is the lexicon's.
struct ProfilePage: View {
    let state: ProfileState
    @Environment(\.clock) private var clock
    @Environment(\.stats) private var stats
    @State private var confirmingBlock = false

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadProfile, retry: retry) {
            if let profile = state.profile {
                list(profile)
            }
        }
        .pageTitle(Text(ProfileTexts.shared.title(profile: state.profile))) { barActions }
        .alert(
            Text(ProfileTexts.shared.blockConfirmTitle(name: blockName)),
            isPresented: $confirmingBlock
        ) {
            Button(role: .destructive) { send(ProfileEvent.ToggleBlock.shared) } label: { Text(ProfileTexts.shared.block(blocking: false)) }
            Button(.actionCancel, role: .cancel) {}
        } message: {
            Text(ProfileTexts.shared.blockConfirmMessage)
        }
        .quoteSheet(state.quote)
        .reporting(state.reporting)
    }

    private func list(_ profile: ProfileView) -> some View {
        List {
            // The activities show their own refresh below; the header's progress shows here only when they don't.
            RefreshStatusView(state: state.content, errorKind: .LoadProfile, showsProgress: state.blocking, retry: retry)
                .listStatusRow()
            if let error = state.error {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
            Section {
                ProfileHeader(state: state, profile: profile, send: send)
            }
            actions
            ProfileTabPicker(state: state, send: send)
            if state.tab == .Posts {
                ProfilePosts(state: state, send: send)
            } else {
                activities
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: Actions

    @ViewBuilder private var actions: some View {
        switch state.actions {
        case .Own:
            Section {
                ForEach(Array(ProfileTexts.shared.ownEntries(state: state).enumerated()), id: \.offset) { _, entry in
                    AppListRow(Text(entry.label)) { send(entry.event) }
                }
            }
        case .BlockedOnBluesky:
            Text(ProfileTexts.shared.blockedOnBluesky)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .listStatusRow()
        case .Unblock:
            // A blocked athlete's follows don't count: Unblock is the one thing to do here.
            AppSecondaryButton(ProfileTexts.shared.block(blocking: true)) { send(ProfileEvent.ToggleBlock.shared) }
                .frame(maxWidth: .infinity)
                .listStatusRow()
        case .Follow:
            followButton.frame(maxWidth: .infinity).listStatusRow()
        }
    }

    /// Follow, Following or Requested, filled only while the viewer has no follow; busy, with a spinner, while one is written.
    private var followButton: some View {
        let label = ProfileTexts.shared.follow(label: state.followLabel)
        let toggle = { send(ProfileEvent.ToggleFollow.shared) }
        return HStack(spacing: 8) {
            if state.followPending {
                AppProgress(small: true)
            }
            if state.followPrimary {
                AppButton(label, action: toggle)
            } else {
                AppSecondaryButton(label, action: toggle)
            }
        }
        .disabled(state.followPending)
    }

    @ViewBuilder private var barActions: some View {
        if state.isSelf {
            AppIconButton(.settings, label: .profileSettings) { send(ProfileEvent.OpenSettings.shared) }
        } else if state.profile != nil {
            Menu {
                Button { send(ProfileEvent.ToggleMute.shared) } label: { Text(ProfileTexts.shared.mute(muting: state.muting)) }
                if state.offersBlock {
                    if state.blocking {
                        Button { send(ProfileEvent.ToggleBlock.shared) } label: { Text(ProfileTexts.shared.block(blocking: true)) }
                    } else {
                        // Blocking asks first, saying they won't be told; unblocking doesn't.
                        Button(role: .destructive) { confirmingBlock = true } label: { Text(ProfileTexts.shared.block(blocking: false)) }
                    }
                }
                // Kept on a profile the viewer blocks, which has nothing else to report from (moderation/reports).
                if let report = state.reportEvent(authorName: AppStrings.string(blockName)) {
                    Button { state.reporting.eventSink(report) } label: { Text(ReportTexts.shared.menuEntry) }
                }
            } label: {
                Image(.more).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityLabel(Text(ProfileTexts.shared.more))
        }
    }

    /// The athlete as the confirmation names them: their name, or their handle.
    private var blockName: any AppText {
        state.profile.map { ProfileTexts.shared.name(profile: $0) } ?? ProfileTexts.shared.title(profile: nil)
    }

    // MARK: Activities

    @ViewBuilder private var activities: some View {
        if state.activitiesHidden {
            // Reached by link only: the athlete is left out of lists, so their activities wait behind a warning.
            Section {
                HiddenNotice { state.revealed.eventSink(state.hiddenKey) }
            }
        } else if state.showsActivities {
            RefreshStatusView(state: state.activities, errorKind: .LoadActivities, retry: retry).listStatusRow()
            ForEach(state.activityList.elements(as: ActivityView.self), id: \.uri.value) { activity in
                Section { card(activity) }
            }
            if state.activitiesEmpty {
                Text(ProfileTexts.shared.activitiesEmpty)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .listStatusRow()
            }
        }
    }

    private func card(_ activity: ActivityView) -> some View {
        let uri = activity.uri.value
        return ActivityCardView(
            content: ActivityCardContent.Companion.shared.of(activity: activity, viewerDid: state.viewerDid),
            authorClickable: false,
            revealed: state.revealed.contains(key: uri),
            send: { send(ProfileEvent.Activity(event: $0)) },
            reveal: { state.revealed.eventSink(uri) }
        )
    }

    private func retry() {
        send(ProfileEvent.Retry.shared)
    }

    private func send(_ event: any ProfileEvent) {
        state.eventSink(event)
    }
}

/// A profile's header: the avatar beside the handle, the labels' notices, the bio, the details' lines and the three counts, the
/// followers and following opening their lists.
struct ProfileHeader: View {
    let state: ProfileState
    let profile: ProfileView
    let send: (any ProfileEvent) -> Void
    @Environment(\.clock) private var clock
    @Environment(\.stats) private var stats

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                avatar
                Text(LabelText.shared.handle(handle: profile.handle.value)).font(.subheadline)
            }
            notices
            if let bio = state.bio {
                Text(verbatim: bio)
            }
            if let details = profile.details {
                detailLines(details)
            }
            counts
        }
        .padding(.vertical, 4)
    }

    /// The avatar, blurred under its label until tapped, which reveals it as the shared avatar does.
    @ViewBuilder private var avatar: some View {
        let picture = Avatar(url: profile.avatar, name: AppStrings.string(ProfileTexts.shared.title(profile: profile)), size: 72)
        if let blur = state.avatarBlur, profile.avatar != nil {
            Button { state.revealed.eventSink(profile.did.value) } label: {
                picture.blur(radius: 12).clipShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(ModerationTexts.shared.blur(reason: blur)))
        } else {
            picture
        }
    }

    @ViewBuilder private var notices: some View {
        let notices = state.notices()
        if !notices.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(notices.enumerated()), id: \.offset) { _, notice in
                    Text(notice.text)
                        .font(.caption)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .foregroundStyle(notice.isError ? Color.red : Color.primary)
                        .background(
                            notice.isError ? Color.red.opacity(0.15) : Color(.secondarySystemFill),
                            in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
            }
        }
    }

    @ViewBuilder private func detailLines(_ details: ProfileDetails) -> some View {
        let country = details.location?.country.flatMap { CountryNames.name(of: $0) }
        let location = ProfileTexts.shared.location(details: details, countryName: country)
        let facts = ProfileTexts.shared.facts(details: details, now: clock.now, units: stats.units)
        if location != nil || facts != nil {
            VStack(alignment: .leading, spacing: 2) {
                if let location { Text(location) }
                if let facts { Text(facts) }
            }
            .font(.subheadline)
        }
    }

    private var counts: some View {
        HStack(spacing: 16) {
            Text(ProfileTexts.shared.activities(count: profile.activitiesCount)).foregroundStyle(.secondary)
            Button { send(ProfileEvent.OpenFollowers.shared) } label: { Text(ProfileTexts.shared.followers(count: profile.followersCount)) }
            Button { send(ProfileEvent.OpenFollowing.shared) } label: { Text(ProfileTexts.shared.following(count: profile.followsCount)) }
        }
        .font(.footnote)
        // Borderless, so each count takes only its own tap in the list row.
        .buttonStyle(.borderless)
    }
}

/// A hidden athlete's activities, collapsed behind the shared warning until the viewer chooses to see them.
struct HiddenNotice: View {
    let show: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(.labelHiddenTitle).font(.headline)
            Text(.labelHiddenMessage).font(.subheadline)
            AppSecondaryButton(.labelHiddenShow, small: true, action: show)
        }
        .padding(.vertical, 4)
    }
}

#if DEBUG
    #Preview("Profile") {
        SamplePages(Samples.profile) { state in NavigationStack { ProfilePage(state: state) } }
            .environment(\.clock, Samples.clock)
    }

    #Preview("Follow, block, own and labels") {
        SamplePages(Samples.profileVariants) { state in NavigationStack { ProfilePage(state: state) } }
            .environment(\.clock, Samples.clock)
    }
#endif
