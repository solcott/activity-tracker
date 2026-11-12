import SwiftUI

/// A place's community names in a list (segments/segment-names, dive-sites/site-names), for a dive site's page and a segment's alike:
/// Propose a name when the viewer may propose, the open proposals with who proposed each and their totals, votes for and against on
/// others' (shown chosen when the viewer gave one, disabled with why when this session can't vote), Edit and Delete on the viewer's own,
/// why the last vote or deletion failed, and the name history behind a disclosure. Every word, and what a tap sends, is Kotlin's
/// (`PlaceNameTexts`, `PlaceNameFields`), shared with the Compose section; the page shows the sheet and the delete confirmation with
/// `placeNames(_:)`. Nothing to show takes no space.
struct PlaceNamesSection: View {
    let fields: PlaceNameFields
    let proposals: [SegmentNameProposalView]
    let history: [SegmentNameAdoption]
    let canPropose: Bool
    let canVote: Bool

    @Environment(\.clock) private var clock

    var body: some View {
        let texts = PlaceNameTexts.shared
        if texts.shows(proposals: Int32(proposals.count), history: Int32(history.count), canPropose: canPropose, fields: fields) {
            Section {
                if canPropose {
                    AppSecondaryButton(texts.propose) { send(PlaceNameEvent.ProposeName.shared) }
                        .frame(maxWidth: .infinity)
                }
                ForEach(proposals, id: \.uri.value) { proposal in
                    row(proposal)
                }
                if let error = fields.error {
                    ErrorMessage(error, layout: .row)
                }
                if !history.isEmpty {
                    DisclosureGroup {
                        ForEach(Array(history.enumerated()), id: \.offset) { _, adoption in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(texts.historyEntry(adoption: adoption, zone: clock.kotlinZone))
                                if let by = texts.historyBy(adoption: adoption) {
                                    Text(by).font(.footnote).foregroundStyle(.secondary)
                                }
                            }
                            .accessibilityElement(children: .combine)
                        }
                    } label: {
                        Text(texts.history)
                    }
                }
            } header: {
                if !proposals.isEmpty {
                    Text(texts.proposals)
                }
            } footer: {
                if !proposals.isEmpty && canVote && !fields.canVoteHere {
                    Text(texts.votingUnavailable)
                }
            }
        }
    }

    private func row(_ proposal: SegmentNameProposalView) -> some View {
        let texts = PlaceNameTexts.shared
        return HStack(spacing: 4) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: proposal.name)
                Text(texts.proposedBy(proposal: proposal)).font(.footnote).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
            if fields.isOwn(proposal: proposal) {
                AppIconButton(.manualEntry, label: texts.edit) { send(PlaceNameEvent.EditProposal(proposal: proposal.uri)) }
                AppIconButton(.delete, label: texts.delete) { send(PlaceNameEvent.DeleteProposal(proposal: proposal.uri)) }
            } else if canVote {
                let up = texts.votedUp(proposal: proposal)
                let down = texts.votedDown(proposal: proposal)
                AppIconButton(.chevronUp, label: texts.voteUp(proposal: proposal), filled: up) {
                    send(texts.voteEvent(proposal: proposal, up: true))
                }
                .foregroundStyle(up ? Color.accentColor : Color.secondary)
                .accessibilityAddTraits(up ? .isSelected : [])
                .disabled(!fields.canVoteHere)
                .opacity(fields.canVoteHere ? 1 : 0.4)
                AppIconButton(.chevronDown, label: texts.voteDown(proposal: proposal), filled: down) {
                    send(texts.voteEvent(proposal: proposal, up: false))
                }
                .foregroundStyle(down ? Color.red : Color.secondary)
                .accessibilityAddTraits(down ? .isSelected : [])
                .disabled(!fields.canVoteHere)
                // Tinted controls keep their colour when disabled, so a vote this session can't send is dimmed as well.
                .opacity(fields.canVoteHere ? 1 : 0.4)
            }
        }
    }

    private func send(_ event: any PlaceNameEvent) {
        fields.eventSink(event)
    }
}
