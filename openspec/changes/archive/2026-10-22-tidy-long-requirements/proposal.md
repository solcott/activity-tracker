# Proposal

## Why

`openspec validate --specs --strict` fails on `ui/content-loading`, `feed/comments` and `segments/trail-candidates`. Six requirement descriptions are over 500 characters.

- **The four in `ui/content-loading`** each state one behavior, but spell out the same screen lists: "the activity and profile screens (activity page, route map, …)", the form screens, and since `adopt-library-paging` the six paged lists.
  - Every change that adds a screen has to edit up to eight copies of a list, and each copy pushes a requirement further over the limit.
  - "Action failures leave loaded content in place" also lists every action one by one.
- **The one in `feed/comments`** ("The detail screen loads comments as the viewer scrolls") states several behaviors in one requirement.
- **The one in `segments/trail-candidates`** ("Candidate ids are stable") also says what an id's features and ends are for each source.

No behavior changes.

## What Changes

- **`ui/content-loading`:**
  - The Purpose names three screen groups once: the activity and profile screens, the form screens and the paged lists, each with the screens it holds.
  - Every requirement that spelled out a group refers to it by name instead, the four flagged ones and the six others that list the same groups.
  - "Action failures leave loaded content in place" names the main kinds of action and says any other action on those screens behaves the same, instead of listing every action. Its scenarios are unchanged.
- **`feed/comments`:** "The detail screen loads comments as the viewer scrolls" is replaced by three requirements that keep its text and every scenario. A MODIFIED block can't drop scenarios, so it's removed and re-added under a new name:
  - "The detail screen lists comments a page at a time": the threads, and the next page as the viewer scrolls.
  - "A failed page of comments keeps what's listed": a failed page keeps what's listed and is tried again alone.
  - "Comments outlast a reload and coming back": a reload keeps the threads until it answers, and coming back shows them at once.
- **`segments/trail-candidates`:** "Candidate ids are stable" drops its sentence on OSM and agency features and ends and keeps every scenario. That sentence becomes "What an id's features and ends are, by source", with two scenarios that restate what the text already implies: an OSM way split, and an agency feature given a new id.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `ui/content-loading`: the same requirements, referring to screen groups named in the Purpose.
- `feed/comments`: the detail screen's paging requirement, split into three.
- `segments/trail-candidates`: "Candidate ids are stable", split into two.

## Impact

- **Specs only:** `openspec/specs/ui/content-loading/spec.md`, `openspec/specs/feed/comments/spec.md` and `openspec/specs/segments/trail-candidates/spec.md`. No code, tests or rules docs change.
- The Purpose edit goes straight into `ui/content-loading`'s main spec, since a delta can't carry an existing capability's Purpose. It lands with this change's apply, before the sync, and both happen before archiving.
