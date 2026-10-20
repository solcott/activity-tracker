# Proposal

## Why

Two `feed/activity-page-layout` requirements state several behaviours each in descriptions over 500 characters, so `openspec validate --strict` fails on the spec.

## What Changes

- "Metric card contents" keeps only what a chart card shows and in what order. Its rule that a chart's marked and spoken average match the headline, and its rule for drawing elevation behind charts by window width, become their own requirements.
- "All stats list" keeps the list and its named groups. What the Counts, Dive and Device groups hold becomes its own requirement.
- No behaviour changes: every rule and scenario is kept, word for word where it moves.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-page-layout`: two requirements are split into one behaviour each; nothing the page does changes.

## Impact

Specs only. No code, tests or data change.
