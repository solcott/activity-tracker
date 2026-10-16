# Design

## Context

See proposal.md for why, and `specs/feed/activity-export/spec.md` for what a UDDF export holds. What exists:

- **Export pipeline.** `ActivityExporter` (`core/data`) checks the outbox, reads the full track and the streams fresh, joins them with `ExportPoints.join` into an `ExportActivity` (SI units), and turns it into bytes per `ExportFormat` (`GpxWriter`/`TcxWriter` strings, `FitWriter` bytes). `ActivityExporter.formats(track, hasStreams)` decides what's offered; the detail presenter calls it through `exportFormats` (`Export.kt`) and `ExportActivityUi.kt` labels each format.
- **What `ExportActivity` already has for a dive.** Since `export-fit-files` it carries the record's `dive` (`ActivityDive`, lexicon units), `gasSwitches` and, per point, depth, temperature, heart rate, ppO₂, CNS, NDL, next stop depth and time, and `tankPressuresBar` in `dive.tanks` order. It doesn't carry the record's `device`.
- **UDDF reading.** `UddfParser` (common, internal) walks `profiledata/repetitiongroup/dive`, resolves `link`/`switchmix`/`tankpressure` refs to `gasdefinitions/mix`, `divesite/site`, `divecomputer` and `tankdata`, reads Kelvin, Pascal and fractions, and reads `datetime` as a zone-less local time in the import's zone. Its gases are switched mixes, then tank mixes, then linked mixes.
- **Merges.** `ActivityMerge` fills the record's `dive` gaps from the other dive (`DiveSummary.fillGaps`) and takes the stream's `gasSwitches` from whichever side has them, so a merged dive can list gases in its record that differ from those its switches name. `FitWriter` already handles this by writing the record's gases and then any a switch names that the record lacks.

## Goals / Non-Goals

**Goals:**
- A `UddfWriter` in common code beside `GpxWriter`/`TcxWriter`, built with `buildString`, `escapeXml` and `toXmlDecimal`.
- A file Subsurface, MacDive and our own import read as the dive it came from; our own import is the test oracle.
- No lexicon, AppView or platform hand-off change.

**Non-Goals:**
- Several dives per file, or exporting a whole logbook.
- Fields the app doesn't store: the diver's name and certification, buddies, equipment beyond the dive computer, gradient factors (UDDF has no standard element for them), tank material or working pressure.
- Changing `UddfParser` to read things it ignores today (it reads `datetime`, mixes, tanks, waypoints and the summary elements we write).

## Decisions

### Local time in the device's current zone
UDDF's `datetime` has no zone. The writer formats `startedAt` in the zone `ActivityExporter` already passes to `FitWriter` and `fileName` (`TimeZone.currentSystemDefault()`), as `yyyy-MM-ddTHH:mm:ss`. An athlete who exports where they dove gets the dive's own clock; one who exports at home after travel gets home time, which logbooks show and let them correct, and which our import's zone picker already handles on re-import.

Alternative: store the dive's zone (from the import's `localStart`/zone picker, or the track's position) on the record and write that. It is more correct for travel, but it's a lexicon change on a public record and does nothing for dives already stored; it belongs in its own change if travel exports turn out to matter.

### File layout
In UDDF 3.2's element order, within `<uddf version="3.2.3" xmlns="http://www.streit.cc/uddf/3.2/">`:

- `generator`: name `activity-tracker`, type `logbook`, `datetime` of the export.
- `diver/owner id="owner"/equipment/divecomputer id="dc"`: `name` and `model` both the record's `device`, which stores the device as one string (the parser takes `model ?: name`, so it reads back as stored). Omitted with no device.
- `divesite/site id="site"`: `name` from `dive.site`, `geography/latitude` and `longitude` from the first point with a position. Omitted when there's neither.
- `gasdefinitions/mix id="mix-O-H"`: one per distinct gas in `dive.gases` order, then each gas a switch names that isn't among them; `name` (`Air`, `EAN50`, `18/45`), `o2` and `he` as fractions. Ids derive from the percents, so a switch finds its mix without a lookup table.
- `profiledata/repetitiongroup id="rg"/dive id="dive"`:
  - `informationbeforedive`: `link`s to site and computer, `datetime`, `divenumber`, `airtemperature` (K), `altitude`, and `surfaceintervalbeforedive/passedtime` when stored.
  - `tankdata id="tankN"` per `dive.tanks` entry: `link` to its gas's mix, `tankvolume` (mL → m³), `tankpressurebegin`/`tankpressureend` (mbar → Pa). Then a gas-only `tankdata` (just the `link`) for each mix no stored tank holds, numbered after them (see below).
  - `samples/waypoint` per second with a depth (the first point of each second, as FIT does, since the join can attach one sample to two track points): `depth` and `divetime`, and where present `temperature` (K), `heartrate`, `calculatedpo2` (Pa), `cns`, `nodecotime`, `decostop kind="mandatory" decodepth duration` (only in deco), `tankpressure ref="tankN"` (Pa) per non-null reading, and `switchmix ref` at the waypoint nearest each switch's second (at or after it). Children go in the schema's order, not the order listed here. The first waypoint also carries `divemode type="closedcircuit"` for a closed-circuit dive.
  - `informationafterdive`: `greatestdepth`, `averagedepth`, `diveduration` (the record's elapsed time), `lowesttemperature`, `visibility`, `highestpo2`, `noflighttime`, `desaturationtime`.

The element order within each parent follows the UDDF 3.2 schema, so a validating reader accepts the file; our parser matches local names and doesn't care.

### Gases with no stored switches
A dive from before `store-dive-gas-switches` lists gases "in order of first use" but no switch times. The writer switches to the first at the first waypoint and defines the rest without switching to them, mirroring FIT's rule: inventing switch times would put wrong deco gas changes in a logbook. Tanks still link their gases, so a tank on a later gas brings that gas back on re-import.

### A gas-only tank per gas no stored tank holds
Importing the first exports into Subsurface (task 3.2) showed it makes cylinders only from `tankdata` and drops a `switchmix` to a mix no cylinder holds: a CCR dive with two gases and no stored tanks came in with no gases, no switches. So each mix no stored tank links gets a `tankdata` with only its `link`. The schema requires `tankpressurebegin`, which these leave out: a made-up pressure would read back as a real one, in our import and in logbooks. `UddfParser` already skips a tank with no pressure, volume or readings, so a round trip gains no tanks.

Subsurface still shows neither the gas each switch goes to nor closed-circuit mode. Its `uddf.xslt` reads a namespaced file's switch target as `@u:ref` (attributes have no namespace, so it never matches) and looks up mixes and `divemode` by unprefixed paths, which a file in the UDDF 3.2 namespace never has. Running its XSLT on our export without the namespace gives the right switches and CCR mode. We keep the namespace, as UDDF requires and other logbook software may check, and leave this to a Subsurface fix rather than write a non-conforming file for one reader.

### `ExportActivity` gains `device`
Add `device: String? = null` from `record.device`. Everything else UDDF needs (`dive`, `gasSwitches`, `isDive`, per-point dive channels, `startedAt`, `elapsed`) is already there. `UddfWriter.write(activity, zone, createdAt = Clock.System.now())` takes the zone as `FitWriter` does.

### Offering UDDF
`formats(track, hasStreams, isDive)` adds `ExportFormat.UDDF("uddf", "application/xml")`, last in enum order, exactly when it adds TCX and `isDive`. The presenter's `exportFormats` passes `record.sport == Sport.DIVE`, and the `remember` key gains it. `ExportActivityUi` gets an `export_format_uddf` string. `export` already rejects a format `formats` doesn't allow, so a non-dive can't be exported as UDDF even if asked.

`application/xml` rather than a made-up `application/uddf+xml`: share targets and browsers know it, and the `.uddf` extension is what logbook apps key on.

## Risks / Trade-offs

- [A logbook rejects the file (schema order, a missing element it requires)] → a jvmTest checks element order against UDDF 3.2 for each parent we write; a manual task imports one deco dive and one older dive into Subsurface before archiving.
- [Exported in another zone than the dive's] → accepted, as for FIT's local timestamp; the spec says which zone, and storing the dive's zone is a separate lexicon change.
- [Waypoint per second makes long dives large] → a 90-minute dive is 5,400 waypoints of a few hundred bytes, under 2 MB; built on `Dispatchers.Default` like the other formats.
- [Older dives lose later gases on re-import] → specified; their tanks keep any tank-linked gas.
