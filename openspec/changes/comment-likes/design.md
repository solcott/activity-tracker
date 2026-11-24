# Design

## Context

Likes are `feed.like` records naming a subject by strong ref. The AppView already stores a like on any subject (`likes.subject_uri` is free text) and only joins activities and posts when serving, so likes on comments are stored today but never served. A comment's tier is derived: the narrowest of its subject's visibility, its audience and its thread root's audience (`commentTier`).

## Goals / Non-Goals

Goals: like and unlike a comment; a count and the viewer's own like on every listed comment; privacy no wider than the comment's.

Non-goals: a list of who liked a comment (`getLikes` stays activities and posts), likes on the page's preview rows, notifications.

## Decisions

- **Served from `commentViews`.** Its callers already hold the subject and the viewer, so the count is one grouped query over `likes ⋈ admittedComments(subject)` for the page's comments, and `viewer.like` reuses `viewerLikes`.
- **Who counts.** A giver counts while they may see the comment: `subject.visibleTo(giver)`, the comment's audience and the root's audience admitting the giver against the subject's author (the per-row `visibleTo` column form), the giver not in a block with the commenter, and `accountShownTo(viewer, giver)`. Distinct givers, as for activities.
- **Placement.** The client places a like on a comment by the comment's effective tier: the narrower of the subject's visibility and the comment's `audience`. Public goes to the repo, anything else to the interactions space, as for activities.
- **Optimistic state.** `LocalLikes` overrides are keyed by subject URI already; `rememberComments` lays them over the listed comments, moving `likeCount` and `viewer.like`, and cached feed counts are skipped for comment subjects.
- **No migration.** The table and indexes already fit; the indexer's self-like check already covers comments.

## Risks / Trade-offs

- Likes on comments written before this change start counting once served; none are expected, since no client wrote them.
- The count query adds one grouped select per page of comments.
