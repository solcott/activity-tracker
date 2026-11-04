# Tasks

## 1. Lexicons and the Kotlin mirror

- [ ] 1.1 Add `feed/diveSite.json`: name, centre (lat/lon at 1e-7 as integers), `radius` (25 to 500 m), `source`, `license`, optional `attribution` and `derivedFrom`, and `createdAt`. Add it to `Nsid` and `Nsid.indexedCollections`, and mirror it as a `DiveSiteRecord`. Verify that an `AtJson` round-trip test passes and that `lexicon-auditor` reports no mismatch.
- [ ] 1.2 Add the views (`diveSiteView`, `diveSiteViewBasic` with status and current name) to `feed/defs.json`, an optional `diveSite` to `activityView`, and the queries `getDiveSite`, `searchDiveSites`, `checkDiveSite`, `getDiveSiteDives`, `getDiveSiteConditions`, `getDiveSiteRegulars` and `importer.checkDiveSites`. Mirror them in `Views.kt` / `Queries.kt`, and clamp the paginated ones with `Paging.clamp`. Verify that `lexicon-auditor` reports no mismatch.
- [ ] 1.3 Update the descriptions in `segmentNameProposal.json` and `segmentNameVote.json` to say the subject may be a segment or a dive site, and update their KDoc. Verify that `scripts/check-public-safety.sh` and `lexicon-auditor` pass.

## 2. AppView sites, visits and status

- [ ] 2.1 Confirm that a dive with only entry/exit positions gets a `feed.segmentTrack` from the client and passes `usableSegmentTracks()`. Where it doesn't, extend the rule for `dive` activities only. Verify with a core-data test (segment track written for a FIT dive with surface fixes) and an AppView test (one-position track usable for a dive, still unusable for a run).
- [ ] 2.2 Add the dive-site migration (`dive_sites`, `dive_site_cells`, `dive_site_visits`, `dive_site_creations`, and a `track` rematch for dive tracks) and its `Tables.kt` entries. Verify that `:server-appview:test` migrates on H2.
- [ ] 2.3 Add `DiveSiteIndex`: validation, the importer-exempt daily limit, the 50 m duplicate check and re-check, edits that enqueue a `site` job, and deletes that cascade to visits and names. Wire it into `Indexer`. Verify with indexer tests for every `site-records` scenario.
- [ ] 2.4 Extend the `track` job with visit matching (dive sports only, first position, nearest live site whose radius contains it, at most one visit, the narrowest of activity and Map tier, plus the dive's temperature, visibility, depth and Dive tier). Add a `site` job that matches a new or moved site against tracks in its cells, and keep combined members out. Recompute visits on `tiers` jobs. Verify with matcher tests for every `site-visits` scenario, including a dive entry inside two sites and matching turned off.
- [ ] 2.5 Add a site step to the status pass (promote at the threshold, archive after 180 days). Verify with tests: the third distinct diver promotes the site (an Only me dive counts), and an unvisited site is archived at 180 days.

## 3. AppView queries

- [ ] 3.1 Serve `getDiveSite`, `searchDiveSites` (active to all; proposed to the creator and divers), `checkDiveSite` and `importer.checkDiveSites` (importer only). Verify with route tests for the `site-records` and `site-import` scenarios.
- [ ] 3.2 Serve `getDiveSiteDives` and `activityView.diveSite`, filtered by visit tier and blocks. Verify with tests: a signed-out viewer sees only Public visits, and a Map-hidden dive shows no site to a follower.
- [ ] 3.3 Serve `getDiveSiteConditions` over at most 2,000 visible rows whose Dive tier the viewer is admitted to (median, extremes, per-month buckets, dive count). Verify with tests for the `site-conditions` scenarios, including the Dive category hidden.
- [ ] 3.4 Serve `getDiveSiteRegulars` through the Mayors counting path over visits (opt-outs, exclusions, windows, scope, the viewer's entry, ties). Verify with tests for the `site-regulars` scenarios.
- [ ] 3.5 Generalize `mayPropose`, the voter check, `SegmentNaming` and the name views over a `NamedPlace` (segment: efforts; site: visits), and accept proposals naming an indexed site. Verify with tests: the existing segment-names suite passes unchanged, and the `site-names` scenarios pass.

## 4. Segment importer

- [ ] 4.1 Add an `OsmData` pass that keeps named nodes, and way or relation centroids, tagged `sport=scuba_diving` / `sport=free_diving`, excluding shops, amenities, clubs and buildings. Add an `extract-sites` command that writes a versioned sites file with stable ids from the element type and id. Verify with importer tests: a wreck node is kept, a dive shop is excluded, and a re-extract keeps the ids.
- [ ] 4.2 Add `publish-sites`: page through `importer.checkDiveSites`, write `feed.diveSite` under each id only when it duplicates nothing and isn't already written, and never edit or delete a published site. Add `--sites` to `scripts/segment-import.sh` and document it in `README.md` and `CLAUDE.md`'s command list. Verify with importer tests (a duplicate is skipped, a second run writes nothing) and `scripts/check-agent-sync.sh`.

## 5. Clients

- [ ] 5.1 Move the naming UI and screen (`SegmentNamesUi`, `ProposeSegmentNameScreen` and its presenter state) into `:app-common-api` / `:app-common-ui` / `:app-common-presenter`, keyed by a place URI, and keep segments using it. Verify that the existing segment naming tests and screenshots pass unchanged, and that `checkModuleBoundaries` passes.
- [ ] 5.2 Scaffold `feature/divesite` with the `new-screen` skill: `DiveSiteScreen` (map and radius, names, conditions in the viewer's units, top regular, a paged dives list) and `DiveSiteRegularsScreen`, with repositories over the new queries. Verify with presenter tests and UI tests from `:app-fixtures` samples, and add them to `ContentLoadingConformanceTest`.
- [ ] 5.3 Add `CreateDiveSiteScreen`: offered on one's own dive with an entry and no live site, refused inside privacy zones or the hidden start, with a name prefilled from `dive.site`, a 25 to 500 m radius (100 m default), the public warning, `checkDiveSite`, and an outbox write naming the source dive only when it and Map are Public. Verify with presenter tests for every `site-creation` scenario, including a double tap writing once.
- [ ] 5.4 On the dive's page (`feature/detail`), show the visited site's current name and status, opening the site, or the dive's own site text when no visit is visible. Add the create-site action. Verify with UI tests for both cases.
- [ ] 5.5 Add strings to `:ui-resources` and screenshot files for every new screen in `androidApp`, then rerecord the references. Verify that `:androidApp:testDebugScreenshotTestDefaultTestSuite` passes after reviewing the images, and that `ScreenshotCoverageTest` passes.

## 6. Integration

- [ ] 6.1 Run `./gradlew check` and verify that it passes.
- [ ] 6.2 End to end on the local stack: seed three athletes with matching on and dives at one place, publish a site from one dive, run `publish-sites` on a small extract, and let the matcher run. Verify that the site becomes active and that each dive links it. Also verify that conditions and regulars show only what each viewer may see, and that a proposal from a diver with 5 dated visits is accepted.

## Workflow follow-up

- Archive the change once it is implemented and reviewed (`/opsx:archive`).
