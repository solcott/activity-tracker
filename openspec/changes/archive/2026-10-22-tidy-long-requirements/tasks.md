# Tasks

## 1. Screen groups

- [x] 1.1 Name the activity and profile screens, the form screens and the paged lists, with the screens each holds, in the Purpose of `openspec/specs/ui/content-loading/spec.md`. Use the lists the requirements spell out today. Verify: the Purpose lists every screen the ten modified requirements stop naming.

## 2. Sync and check

- [x] 2.1 Sync the deltas into `ui/content-loading`, `feed/comments` and `segments/trail-candidates` and archive the change. Verify: `openspec validate --specs --strict` passes for every spec, and each of the seven scenarios that left "The detail screen loads comments as the viewer scrolls" appears exactly once under the three requirements that replace it.
