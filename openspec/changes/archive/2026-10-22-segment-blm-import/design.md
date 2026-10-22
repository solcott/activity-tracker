# Design

## Context

`segment-agency-import` built the agency path this change reuses whole. `AgencyRules` is an enum with one entry per dataset kind. Each entry gives a feature's key (`featureKey`), its name (`nameFrom`, title-casing all-caps names) and its groups (`groupsOf`), reading properties whose keys are lower-cased. `AgencyReader` snaps lines, `TrailBuilder`, `CandidateCutter` and `CandidateDedup` treat agency ways alike, and `Main.parseExtract` takes any kind `AgencyRules.ofKind` knows. Candidate source, licence and attribution come from the entry, so a third entry is a third dataset.

BLM's data, as checked on 2026-10-22 against its national services:

- **The layer.** `BLM_Natl_GTLF_Public_Managed_Trails` (layer 2 of that ArcGIS feature service) holds 19,520 polylines, all of asset class "Transportation System - Trail", mostly LineStrings with a few MultiLineStrings. The other national GTLF layers split the same trails by mode or hold roads.
- **Names.** `ROUTE_PRMRY_NM` holds the name. 4,939 features have none, and some are in capitals ("SLICKROCK TRAIL").
- **Keys.** `Original_GlobalID` is set and unique on every feature. `GlobalID` belongs to the hosted copy.
- **Allowed modes.** `PLAN_ALLOW_MODE_TRNSPRT` holds the allowed-mode code, whose domain names who may use each code. About 3,300 features are `UNK` or empty, and some values carry trailing spaces (`TECH_VEH_SHARED `). `PLAN_MODE_TRNSPRT` holds the coarser planned mode, in mixed case ("Non-Motorized").
- **Access.** `PLAN_ACCESS_RSTRCT` holds who may use the route. BLM's national GTLF service defines `All` as "No one allowed to access", `Admin Only` as government use only, `Authorized/Permitted User Only` as permitted users, and `None` as no restriction.
- **OHV designation.** `PLAN_OHV_ROUTE_DSGNTN` ("Closed" on 5,937 trails) concerns off-highway vehicles only, not hikers or cyclists.

## Goals / Non-Goals

**Goals:**
- BLM trails become candidates through the existing agency path, with one new `AgencyRules` entry and no change to joining, cutting, ids, dedup, staging or publishing.

**Non-Goals:**
- BLM's road layers, and trails outside the public managed-trails layer.
- Seasonal or time-of-day limits (`PLAN_SEASON_RSTRCT_CODE`, `OHV_ROUTE_DSGNTN_LIM`): like the Forest Service's seasons, a trail open part of the year still qualifies.
- Treating OHV closures as closures for other users.
- Agency closures dropping OSM candidates (`segment-agency-closures`).

## Decisions

**Input is the feature service, read in place.** As for the Park Service, the README gives one `ogr2ogr -f GeoJSONSeq … "<layer>/query?where=1%3D1&outFields=*&geometry=<box>&geometryType=esriGeometryEnvelope&inSR=4326&outSR=4326&f=json"` with the same `-spat` box. GDAL pages through the query, so no national download is needed. *Alternative:* BLM's geodatabase downloads per state. Rejected for the docs: the national service covers every state the same way and was checked here.

**The key is `Original_GlobalID`.** It is unique across the national layer and is the source geodatabase's id, so it survives BLM republishing the hosted copy. The feature key is `blm:<Original_GlobalID>`, plus the part index for a MultiLineString's later parts as for the others. *Alternative:* `FAMS_ID`, which is empty on 14,819 features.

**Allowed modes map by a fixed table, read literally.** Codes are compared trimmed and case-insensitively:

| Code | Groups |
|---|---|
| `NON_MOTO_SHARED`, `BIKE_HIK_ONLY`, `ALL_MOTO_VEH`, `MTC_ATV_SHARED`, `MTC_ATV_UTV_SHARED`, `TECH_VEH_SHARED` | `ride`, `run`, `hike` |
| `BIKE_ONLY` | `ride` |
| `HIK_ONLY`, `EQU_HIK_ONLY` | `run`, `hike` |
| `SNOW_NON_MOTO_SHARED`, `SNOW_NON_MECH_SHARED`, `SNOW_MOTO_SHARED` | `ski` |
| `EQU_ONLY`, `STRT_LGL_VEH`, `MTC_ONLY`, `MTC_ATV_ONLY`, `MTC_ATV_UTV_ONLY`, `TECH_MTC_ONLY`, `TECH_HI_CLEAR_VEH_ONLY`, `SNOW_MOTO_ONLY`, `MTC_SHARED` | none |
| `UNK`, empty, or any other value | by `PLAN_MODE_TRNSPRT`: Non-Motorized gives `ride`, `run`, `hike`; Non-Mechanized gives `run`, `hike`; Motorized or empty gives none |

Each code's domain description names its users. `MTC_SHARED` ("Motorcycle Shared") names no others, so it gives none. `SNOW_NON_MOTO_SHARED` lists fat-tire bikes, but a snow trail is not a summer ride, so it gives only `ski`. The motorized `_ONLY` codes give nothing, as decided with the user. This is literal even where BLM's coding looks wrong: Slickrock Trail is coded `MTC_ONLY`, so its candidates come from OSM. *Alternative:* BLM's planned-mode definition, under which a motorized route admits every non-motorized user too. Rejected by the user in favour of the codes as written.

**Restricted access excludes.** `All`, `Admin Only` and `Authorized/Permitted User Only` (trimmed, case-insensitive) make a feature give no groups. `None`, `Unknown` and empty don't.

**Credit.** Attribution "Bureau of Land Management", `license` `CC0-1.0` (US federal data is public domain), `source` `agency`.

## Risks / Trade-offs

- **[BLM miscodes a popular trail as motorized-only, as on Slickrock]** → OSM still gives its candidates. The closures change must take this into account before it lets such codes drop OSM candidates.
- **[`Original_GlobalID` changes if BLM rebuilds its source data]** → Every BLM candidate would get a new id. Published segments stay, and new duplicates of them are caught by `publish`'s `checkSegment`, as for any rerouted trail.
- **[A quarter of BLM trails have no name]** → They give no candidates, as unnamed OSM ways don't.

## Migration Plan

Re-extract a region with `--agency blm=<file>` and stage it. Nothing else changes, and nothing needs migrating.
