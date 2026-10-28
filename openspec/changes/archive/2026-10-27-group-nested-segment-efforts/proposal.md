# Proposal

## Why

Imported segments overlap heavily: a whole trail, its stretches between junctions, and parent trails built from sections all match the same stretch of an activity. The activity page lists them flat in start order, so "Rattler Trail (eastbound)" (15:08) and its two stretches (8:54 + 6:14) read as three unrelated rows, and a long list of related segments is hard to scan.

## What Changes

- The activity page's segments card groups efforts whose time spans on the activity overlap, with a divider between groups.
- Within a group, an effort whose span lies inside another's is nested under it, indented with tree guide lines; overlapping efforts that don't contain one another stay side by side.
- Each row in a group of more than one shows a thin bar marking where its span falls within the group's span.
- A nested effort's name drops the trail name it shares with its parent (for example "Rattler Trail: A to B" under "Rattler Trail (eastbound)" shows "A to B"). The segment screen keeps the full name.
- Grouping is derived on the client from each effort's start offset and elapsed time; no lexicon, AppView or importer change.
- An activity whose efforts don't overlap looks as it does today.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-efforts`: the activity page groups overlapping efforts, nests an effort under the one containing it, shows each grouped effort's span, and shortens nested names (new requirements beside "The app shows efforts on the activity page", which is unchanged).

## Impact

- `:app-screens`: `ActivityDetailPage.segments` changes from a list of `EffortView` to grouped rows; new grouping model and pure grouping function.
- `:app-presenters`: `rememberSegments` groups the loaded efforts.
- `composeApp`: `ActivitySegmentsUi.kt` draws groups, indentation, guides and span bars; `EffortRow` is reused.
- `:app-fixtures`, presenter tests, content-loading conformance and `:androidApp` screenshot references.
