# Design

## Context

`PrivacyZones` (`core/data/.../activity/PrivacyZones.kt`) holds four pieces of mutable state: `last` (the zones last read or saved), `batch` (the read that `withReadOnce` takes), `stale` (routes published without a fresh read) and a `Mutex`. Today:

- `withReadOnce` assigns `batch` from the read it takes under the mutex, and clears it in `finally` outside the mutex. `forPublish` reads `batch` with no lock, and if it's set returns it to every caller, inside the import or not, marked `fresh` as of the batch's read.
- `write` (save or add) updates `last` but leaves `batch` alone.
- `published`, `takeStale` and `hasStale` touch `stale` with no lock, and are called from `ActivityRepository`, `ActivityManager` and `RouteTrimmer` on whatever dispatcher those run on.
- `published` keeps a route as stale only when `with.fresh` is false. `SegmentTracks.put` uses the same flag to decide whether the segment tracks need a sweep.

When zones are saved, `RouteTrimmer.sweep` (run by `repairRoutesOnSave`) re-trims every activity with a track, queued ones included. So an activity queued before a save is covered. What isn't covered is an activity trimmed by the old zones and queued after that sweep has run, and that is exactly what a batch still running produces.

## Goals / Non-Goals

**Goals:**
- A batch's read is visible only to the coroutines that run the batch.
- A save or newer read made during a batch replaces its read for the publishes still to come.
- A publish whose trim was replaced while it ran counts as stale.
- No unlocked access to `batch`, `last` or `stale`.

**Non-Goals:**
- Noticing zones saved on another device during an import. The batch reads once by design, and the other device's save sweeps only the activities that device can see as queued. That's unchanged here, and it's the same gap any publish has between its read and its write.
- Changing `RouteTrimmer`'s sweep or `SegmentTrackSweeper`.
- P8, the sparse-sample chord through a zone.

## Decisions

**Scope the batch with a coroutine-context element, not a field.** `withReadOnce` takes its read under the mutex and runs `block` in `withContext(BatchRead(trim))`. `forPublish` looks up `currentCoroutineContext()[BatchRead]`. Coroutines launched inside the block inherit it, so an import that fans out still shares the one read, and nothing outside the block can see it. Nested or concurrent batches each get their own read instead of overwriting each other's. With no shared field left, the unlocked `finally` goes away.
- *Alternative:* keep the field but key it by a batch id under the mutex. That still needs each publish to know which batch it belongs to, which is exactly what the context element provides.
- *Alternative:* count active batches and turn batching off while any non-batch publish runs. That's more complex and still races.

**Inside a batch, prefer a newer `last`.** Under the mutex, `forPublish` in a batch returns `PublishTrim(last, fresh = true)` when `last` is a `Known` that differs from the batch's trim, and the batch's trim otherwise. `last` changes only on a successful read or a save, so a difference always means newer zones. A save from this session holds exactly what the PDS now has, so counting it as fresh is right. This is also what makes a batch whose read failed pick up a later successful read.
- *Alternative:* update every active batch's element from `write`. That needs a registry of live batches, and the comparison with `last` gives the same result with no registry.

**`published` decides freshness last and returns it.** Under the mutex, a publish counts as fresh only if `with.fresh && with.trim == last`. Otherwise its route (two or more samples) goes into `stale`, trimmed by `with.trim`. It returns the `PublishTrim` the rest of the publish should use, with `fresh` cleared when its trim was replaced. `ActivityRepository.queue` and `ActivityManager`'s combine pass that returned value to `SegmentTracks.put`, so a superseded segment track sets its stale flag and gets swept. Because `published` runs after the activity record is queued, every save falls either before the check, which makes the route stale, or after it, when the record is already queued and the save's sweep re-trims it.

**Guard `last` and `stale` with a second, in-memory-only lock.** `mutex` stays as it is: it serializes reads and saves and is held across their PDS calls. A new `state` mutex guards `last` and `stale`. It's held only for in-memory work and never across a PDS call. `remember` (which sets `last`), `published`, `takeStale`, `hasStale`, a failed read's fallback to `last`, and `forPublish` inside a batch take only `state`. Reads and saves take `state` inside `mutex`, never the other way round, so the two can't deadlock. `published`, `takeStale` and `hasStale` become `suspend`, and `hasStale` changes from a property to `suspend fun hasStale()`. Every caller is already in a suspend function (`ActivityRepository.publish`, `queue` and `sync`, `ActivityManager`, `RouteTrimmer.trimStale`).
- *Alternative, tried first:* guard everything with `mutex`. Then `hasStale()`, which the feed's `sync()` calls, waits behind a re-trim's slow zones read, and the feed doesn't load until that read ends (`FeedPresenterTest.theFeedLoadsWhileTheRouteReTrimIsStillRunning`). Every batched publish would also wait on any read in flight.
- *Alternative:* a lock-free `AtomicReference` holding an immutable map, which would keep the signatures non-suspending. That's a third concurrency idiom in one class, and `published` needs `last` and `stale` consistent under one lock anyway.

## Risks / Trade-offs

- [A save landing after `published` but before `SegmentTracks.put` queues the segment track] → that track carries the old `zonesRevision`. `SegmentTrackSweeper` has no marker, so its next run (a later save, or `repairRoutes` next session) rewrites it. Segment tracks are never served, only matched, so the exposure is limited to matching against the old zones until then.
- [More lock traffic: each batched publish now takes `state` twice, once in `forPublish` and once in `published`] → `state` is never held across I/O, so both sections only compare and assign in memory. A logbook of hundreds of dives adds microseconds.
- [A later edit that holds `state` across a PDS call, or takes `mutex` inside it] → the KDoc on `state` says neither is allowed, and the order is `mutex` then `state`.
- [The signatures of `published`, `takeStale` and `hasStale` change] → all callers are in `core/data` and are updated in the same change.
