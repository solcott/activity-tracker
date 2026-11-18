# Proposal

## Why

Little Cottonwood Creek (us-utah) shows as two trail pages with nearly the same segments, and both break at Wasatch Resort. Two things in the importer cause this:

- **A gap.** OSM routes the trail over two roads through the resort: Granite Cliffs Road (86 m), then Power Plant Lane (133 m). Both carry the trail's ref `1001` (`ref=NF 87672;1001`). Shared-tread bridging only follows one way of up to 200 m, so the trail splits into a 3,344 m piece and a 1,008 m piece, and no whole-trail segment covers it.
- **Two pages.** The Forest Service's trail data has the same trail as "Little Cottonwood Creek". Paths are built per source, so the region gets an OSM path and an agency path for one trail. Each segment lies on both, so the two pages list the same rows.

## What Changes

- **Bridge across several roads.** A trail SHALL join another of its name across a chain of connected ways that are open to its group, when the chain is at most 200 m long. Today the chain must be a single way.
- **Bridge further on roads that carry the trail's number.** When every way in the chain lists one of the trail's own refs (a `ref` value, split at `;`), and the ways of the trail on both sides carry that ref, the chain may be longer: up to 1 km by default. Extract SHALL take the limit as an option (`--ref-bridge-m <meters>`), so operators can tune it. The import script SHALL pass it through.
- **One path per trail across sources.** When a Forest Service (or other agency) trail path mostly follows an OSM trail path, extract SHALL write only the OSM path. That path keeps OSM's name, line, source and licence, and is open to the sport groups of both. Segments from both sources then lie on it.
  - A re-import drops an already published agency path through the existing refresh rule: a path that no region's file lists any more is deleted.
- **Unchanged:** candidates and their ids. An agency candidate still comes from its own line, and published segments never change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/trail-candidates`: "Named ways join into trails" bridges over a chain of ways, and further over ways carrying the trail's ref. "Closed and unsuitable ways are left out" covers chains too. A new requirement, "Extract takes the ref bridge limit", adds the `--ref-bridge-m` option.
- `segments/path-import`: "Extract writes the region's trails as paths" writes one path when an agency trail and an OSM trail follow the same line.

## Impact

- **Importer code** (`server/segment-importer`):
  - `TrailBuilder.kt`: the bridge walk over several ways, the ref rule, and the limit passed in;
  - `TrailTags.kt`: reading a way's refs;
  - `Paths.kt` and `Extract.kt`: merging an agency path into an OSM path;
  - `Main.kt`: the new option;
  - plus their tests and the golden candidate and path files.
- **`scripts/segment-import.sh`:** passes the new limit through, read from an environment variable.
- **`server/segment-importer/README.md`:** the joining notes.
- **No changes** to the lexicon, the AppView, or the clients.
- **Seeing it locally:** re-import us-utah. Published segments never change, so the new whole-trail segment for Little Cottonwood Creek appears alongside the old pieces. The agency path is deleted by `publish-paths`.
