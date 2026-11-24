import SwiftUI

/// One activity as a card (ui/ios-design-system), for any native page that lists activities, drawn from Kotlin's choices for it
/// (`ActivityCardContent`, which the shared `ActivityCard` asks too): the author line (avatar, name, the badge of a non-public tier, the
/// start on the activity's own clock, whom it was done with), the labels' notices, the sport's icon and title, a dive's site, two lines
/// of description, the figures, the owner's locks, the route and photos, and Like with the counts.
///
/// It is a list row. Tapping it opens the activity, which is also its VoiceOver action; the author line (when the page isn't the
/// athlete's own), Like and the photos are buttons of their own, which take their own taps. What a tap does is the host's: the card sends
/// the shared `ActivityListEvent`s and asks the host to reveal its photos.
struct ActivityCardView: View {
    let content: ActivityCardContent
    /// False where the page is the athlete's own, as on their profile or their import.
    var authorClickable = true
    /// The viewer revealed this card's blurred photos on this page.
    var revealed = false
    /// False where the page offers no Like on its cards, as the shared page passes none (a dive site's dives).
    var likeable = true
    let send: (any ActivityListEvent) -> Void
    var reveal: () -> Void = {}

    @Environment(\.stats) private var stats
    @Environment(\.clock) private var clock

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            author
            notices
            title
            if let site = content.diveSite() {
                Label {
                    Text(verbatim: site)
                } icon: {
                    Image(.location)
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
            if let about = content.descriptionText() {
                Text(verbatim: about).font(.subheadline).lineLimit(2)
            }
            CardStats(stats: content.stats(units: stats.units))
            locks
            MediaStrip(content: content, revealed: revealed, reveal: reveal) { index in
                send(ActivityListEvent.OpenMedia(activity: content.activity, index: index))
            }
            likes
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture { send(ActivityListEvent.Open(activity: content.activity)) }
        .accessibilityElement(children: .contain)
        .accessibilityAction { send(ActivityListEvent.Open(activity: content.activity)) }
    }

    @ViewBuilder private var author: some View {
        let line = CardAuthorLine(avatar: content.activity.author.avatar, name: content.author(), blurred: content.avatarBlur != nil) {
            if let badge = content.badge() {
                Text(badge)
                    .font(.caption2)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color(.secondarySystemFill), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                    .accessibilityLabel(content.badgeDescription().map { Text($0) } ?? Text(badge))
            }
        } lines: {
            clock.text(content.start(now: clock.now, zone: clock.kotlinZone))
            if let together = content.together() {
                Text(together)
            }
        }
        if authorClickable {
            // Plain, so the line keeps its colours rather than the accent's, and only it takes the tap, not the whole row.
            Button {
                send(ActivityListEvent.OpenAuthor(activity: content.activity))
            } label: {
                line
            }
            .buttonStyle(.plain)
        } else {
            line
        }
    }

    @ViewBuilder private var notices: some View {
        let notices = content.notices()
        if !notices.isEmpty {
            // One under another, each wrapping: a takedown's notice is a sentence, longer than a phone is wide.
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(notices.enumerated()), id: \.offset) { _, notice in
                    NoticeChip(notice: notice)
                }
            }
        }
    }

    private var title: some View {
        HStack(spacing: 8) {
            if let type = content.typeDescription() {
                // An untitled card already reads its type as its title, so only a titled one names the icon.
                SportIcon(content.type).accessibilityHidden(false).accessibilityLabel(Text(type))
            } else {
                SportIcon(content.type)
            }
            Text(content.title()).font(.headline)
        }
    }

    @ViewBuilder private var locks: some View {
        let locks = content.locks()
        if !locks.isEmpty {
            FlowRow(spacing: 12, lineSpacing: 4) {
                ForEach(Array(locks.enumerated()), id: \.offset) { _, lock in
                    HStack(spacing: 4) {
                        Image(.lock)
                        Text(lock.text)
                    }
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(lock.spoken))
                }
            }
        }
    }

    private var likes: some View {
        HStack(spacing: 0) {
            if likeable && content.offersLike {
                AppIconButton(.like, label: .likeContentDescription, filled: content.liked) {
                    send(ActivityListEvent.ToggleLike(activity: content.activity))
                }
                .foregroundStyle(content.liked ? Color("LikeColor") : Color.accentColor)
            }
            Text(content.counts()).font(.footnote)
        }
    }
}

/// A card's figures, a label over each value, spread across the card as the shared row spreads them; one under another when they don't
/// fit side by side (a large text size).
struct CardStats: View {
    let stats: [LabeledText]

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .top, spacing: 0) {
                ForEach(Array(stats.enumerated()), id: \.offset) { index, stat in
                    if index > 0 { Spacer(minLength: 12) }
                    figure(stat)
                }
            }
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(stats.enumerated()), id: \.offset) { _, stat in figure(stat) }
            }
        }
    }

    private func figure(_ stat: LabeledText) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(stat.label).font(.caption).foregroundStyle(.secondary)
            Text(stat.value).font(.headline).monospacedDigit()
        }
        .accessibilityElement(children: .combine)
    }
}

#if DEBUG
    #Preview("Activity cards") {
        List {
            ForEach(Samples.activityCards.sorted { $0.key < $1.key }, id: \.key) { name, activity in
                Section(name) {
                    ActivityCardView(content: ActivityCardContent.Companion.shared.of(activity: activity, viewerDid: Samples.cardViewer)) {
                        _ in
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .environment(\.clock, Samples.clock)
    }
#endif

extension ActivityCardView {
    /// The card for `activity` as `viewerDid` sees it, its photos revealed by and revealing through the page's `revealed`.
    init(
        activity: ActivityView, viewerDid: String, revealed: Revealed, authorClickable: Bool = true, likeable: Bool = true,
        send: @escaping (any ActivityListEvent) -> Void
    ) {
        let uri = activity.uri.value
        self.init(
            content: ActivityCardContent.Companion.shared.of(activity: activity, viewerDid: viewerDid),
            authorClickable: authorClickable,
            revealed: revealed.contains(key: uri),
            likeable: likeable,
            send: send,
            reveal: { revealed.eventSink(uri) }
        )
    }
}

/// A card's author line (an activity's, a post's, an import's): the avatar (blurred while a label hides it), the name with what the card
/// adds beside it (`badge`: a non-public tier's), and the card's `lines` under it in small secondary text (when, with whom, for whom).
struct CardAuthorLine<Badge: View, Lines: View>: View {
    let avatar: String?
    let name: any AppText
    var blurred = false
    @ViewBuilder let badge: () -> Badge
    @ViewBuilder let lines: () -> Lines

    var body: some View {
        HStack(spacing: 8) {
            Avatar(url: avatar, name: AppStrings.string(name), size: 32).blur(radius: blurred ? 6 : 0)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(name).font(.subheadline.weight(.semibold))
                    badge()
                }
                lines().font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}

extension CardAuthorLine where Badge == EmptyView {
    /// An author line with nothing beside the name.
    init(avatar: String?, name: any AppText, blurred: Bool = false, @ViewBuilder lines: @escaping () -> Lines) {
        self.init(avatar: avatar, name: name, blurred: blurred, badge: { EmptyView() }, lines: lines)
    }
}
