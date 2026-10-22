# Proposal

## Why

Agency datasets say where a sport is not allowed, but the importer only uses them to add trails. An OSM path through a wilderness area that OSM doesn't tag `bicycle=no` still gets `ride` candidates, even when the Forest Service's own data says bikes are restricted there all year. Once three athletes ride it, it is published as a segment and invites more riding where riding is closed. `segment-agency-import` accepted this and named it as a follow-up.

## What Changes

- **Agency closures.** Where an agency's data says a trail is closed to a sport group, that is a closure:
  - **Forest Service:** a use restricted all year and not managed or accepted in any season.
  - **BLM:** an allowed-mode code that leaves the group out (read literally, as in `segment-blm-import`), or access restricted to administrators, permitted users or no one.
  - **Park Service:** none, because its listed uses are free text and often "Unknown".
- **OSM candidates along a closure are left out.** An OSM candidate of a group that runs along an agency trail closed to that group, for at least 100 m, is dropped before deduplication. Crossings and nearby parallel trails don't count, nor does a stretch where another agency line along it is open to the group. Other groups' candidates on the same path are kept.
- **Reported.** `extract` prints how many OSM candidates each group lost to closures.
- **Already-published segments stay.** The next staging run drops the candidate. The importer never deletes a published segment, as now.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `segments/trail-candidates`: agency features can close trails to sport groups, and OSM candidates along those closures are left out.

## Impact

- **Importer:** `AgencyRules` gains per-dataset closure rules. `AgencyReader` keeps closed features' lines, which it now drops. A new pass between cutting and dedup drops OSM candidates along closures. `extract` reports the drops. There are new tests and fixture features.
- **No lexicon, AppView or client change.**
- **Docs:** the importer README.
- **Dependencies:** `segment-blm-import`, whose literal mode table this change's BLM closures read. Implement that change first.
