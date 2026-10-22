# Design

## Context

- **Importer** (`server/segment-importer`): a JVM CLI with a hand-parsed `extract` command. It writes a candidates file: a header line `{region, extractTimestamp, importerVersion}`, then one `CandidateEntry` per line. Its only dependencies are `core-lexicon`, `core-location` and osmpbf. Candidate ids are 26 lowercase base32 characters, so they are valid record keys and match the AppView's `[a-z0-9-]{1,64}` id rule.
- **AppView**: `importer.putSegmentCandidates`, `finishSegmentCandidates` and `getRiddenSegmentCandidates` accept only `SEGMENT_IMPORTER_DID`, with service auth (`SegmentCandidateViews`). The creation limit is enforced in two places: `upsertSegment`, using `segmentsCreatedSince`, and `checkSegment`'s `remaining`. Neither knows about the importer today.
- **`core/atproto`**: `XrpcClient` takes a pluggable `RequestAuthenticator`. `ServiceAuthAuthenticator` mints AppView tokens from an authenticated PDS client. There is no app-password session: clients use OAuth, and the AppView's `OzoneClient` hand-rolls `createSession`.
- **`feed.segment`** declares `"key": "tid"`. Nothing in the AppView checks segment rkeys.

## Goals / Non-Goals

**Goals:**
- Re-running any step is safe: `stage` replaces a region, and `publish` only adds what's missing.
- The importer uses only public endpoints and its own account. The AppView gains no write path.

**Non-Goals:**
- A production deployment, account or scheduler. The run script is what a scheduler would call.
- Updating or retiring published segments when OSM changes. See the decision on that below.
- Agency data (`segment-agency-import`).

## Decisions

**The rkey is the candidate id, and `feed.segment`'s key becomes `any`.** A published segment's URI then identifies its candidate, with no new record field. That URI is the link `getRiddenSegmentCandidates` uses to leave out published candidates, and it makes writes idempotent. Changing `tid` to `any` only loosens the lexicon: existing TID-keyed records stay valid, and clients keep minting TIDs.
- *Alternative:* derive a TID from a hash of the id. Rejected: it fakes a timestamp in a key that readers may sort by.
- *Alternative:* add a `candidate` field to `feed.segment`. Rejected: it's a public field only one account would use, and the AppView would need a column and an index for it.

**Write with `createRecord` and an explicit rkey, after a `getRecord` check.** `getRecord` skips candidates the repo already holds. `createRecord` refuses an rkey that exists, which covers a second `publish` racing the first. `putRecord` would silently rewrite a published segment, and rewriting a line wipes its efforts. Records are written with `validate: false`, as everywhere else, because PDSes don't know our lexicons.

**The AppView decides what's left to publish.** `getRiddenSegmentCandidates` adds a `NOT EXISTS` on `segments.uri = 'at://<importer>/<feed.segment>/' || id`. The `segments` primary key makes this a lookup. A published segment counts whatever its status, so an archived one is never re-published. Until the AppView indexes a new segment its candidate stays listed, and `getRecord` covers that window.

**`checkSegment` before writing, with the AppView's own test as the backstop.** The importer asks `feed.checkSegment` as itself, so a candidate that duplicates a live segment is skipped and reported, not published and then marked `duplicate`. If a segment appears between the check and the write, indexing still marks the new one `duplicate`, and it gets no efforts.

**The importer is exempt from the creation limit.** `Indexer` and `SegmentViews` take the configured importer DID. `upsertSegment` skips the limit for that author, and `checkSegment` reports `DAILY_SEGMENT_LIMIT` as its `remaining`. Pacing under 10 a day would leave a first Vermont import unfinished for months.
- *Alternative:* a per-account configurable limit. More config than one trusted account needs.

**Published segments are immutable.** A geometry fix keeps the candidate's id, but `publish` never rewrites, so the segment keeps its line and its leaderboards. A rerouted trail gets a new id: if its line duplicates the old segment it stays unpublished, and otherwise it's published alongside it. A trail removed from OSM leaves its segment in place. Its candidate is unstaged by `finishSegmentCandidates`, and an unridden segment is archived after 180 days by the existing status pass.
- *Alternative:* re-publish changed lines. Rejected: it resets efforts, and an OSM edit war would churn leaderboards.

**Session and service auth.** An importer-local `PasswordSession` `RequestAuthenticator` does the following:
- calls `createSession` with the account's identifier and app password;
- sends the access JWT as `Bearer`;
- on a 401, refreshes with `refreshSession`, or signs in again if the refresh fails.

The PDS client is an `XrpcClient` with that authenticator. The AppView client is an `XrpcClient` with `ServiceAuthAuthenticator(pds, appViewDid)`, which mints one token per NSID, including `feed.checkSegment`. It stays in the importer because nothing else needs password sign-in, and client code must keep using OAuth.

**Settings come from the environment.**
- `SEGMENT_IMPORTER_IDENTIFIER` (handle or DID)
- `SEGMENT_IMPORTER_PASSWORD`
- `SEGMENT_IMPORTER_PDS_URL`
- `APPVIEW_URL`, default `http://localhost:8081`
- `APPVIEW_DID`, default `did:web:localhost%3A8081`

The password never goes on the command line, so it stays out of shell history and `ps`. Like the AppView's, the importer's Gradle `run` task fills unset ones from `docker/.env`. The AppView's own `SEGMENT_IMPORTER_DID` must name the same account.

**`stage` details.**
- The revision is the run's start time, `yyyymmddThhmmssZ` in UTC.
- The region comes from the file header, and a line with another region is refused.
- Batches go out sequentially. Rejected ids are printed but don't fail the run.
- Any failed call stops before `finishSegmentCandidates`, so a partial run never removes a region's candidates. A later complete run replaces them.

**`publish` details.**
- Pages through `getRiddenSegmentCandidates` with limit 500.
- For each candidate it runs check, then `getRecord`, then `createRecord`.
- It stops on the first transport or rate-limit error and prints a summary: published, duplicates, already present. Re-running resumes where it stopped.

**Ops.**
- `docker/scripts/create-account.sh importer` creates `importer.test`.
- `scripts/segment-import.sh <region> <file.osm.pbf>` runs `extract`, `stage` and `publish` in order.
- The local-stack skill covers putting the account's DID in `SEGMENT_IMPORTER_DID`, setting its credentials, fetching Vermont from Geofabrik (`north-america/us/vermont-latest.osm.pbf`) and running the script.
- Vermont is first because it's small, trail-dense and the region the tests already use.

## Risks / Trade-offs

- [PDS rate limits on `createRecord` cap a first big region's publish rate] → `publish` stops on 429 and resumes on re-run, so a scheduled daily run converges.
- [The service account's credentials can publish segments under the app's name] → Use an app password, revocable on its own, kept in the operator's environment and never in the repo.
- [Immutable segments drift from OSM after geometry fixes] → They stay within the 5 m tolerance the candidate was cut to until a big reroute, which yields a new candidate. Revisit if drift shows up in practice.
- [Loosening `key` to `any` lets any client write non-TID segment keys] → The AppView never relied on TID keys for segments, and keys carry no meaning except the importer's.

## Migration Plan

- No migration. The AppView changes are a query filter and a limit exemption.
- Deploy order: the AppView with `SEGMENT_IMPORTER_DID` set first, then run the importer.
- Rollback: stop running `publish`. Published segments stay valid ordinary segments.
