# Proposal

## Why

The owner's activity page offers Leave group on every activity once grouping is on and its segment track is stored, even an activity done alone that lists no partner. A button to leave a group that doesn't exist confuses the owner.

## What Changes

- Leave group is offered only while the owner's view of the activity lists at least one partner.
- Rejoin group is still offered whenever the activity has left its group, partners or not, so the owner can always undo a leave.
- Leaving is for paired activities only: an owner can no longer leave in advance on an activity that isn't paired yet. Turning grouping Off stays the way to stay out of every pair.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `feed/group-activities`: leaving needs the activity to be paired, and the owner's page offers Leave group only when the activity lists a partner.

## Impact

- `feature/detail` presenter: the owner's group state is offered only with partners or once left. Both the Compose page and the iOS page already hide the button when that state is absent.
- Presenter tests in `ActivityDetailGroupTest`. No lexicon, AppView or storage change.
