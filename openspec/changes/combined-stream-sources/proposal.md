# Proposal

## Why

When two devices recorded the same ride, the athlete knows which one to trust for each kind of data:
- the chest strap's heart rate, not the watch's wrist sensor;
- the bike computer's barometric altitude;
- the watch's GPS when the bike computer lost signal under trees.

`combine-activities` ranks each channel's sources by sample count, which is often right but not always. This change lets the athlete choose, and shows every viewer which device each chart came from.

It builds on `combine-activities`, and needs its `combined.sources` ranking and rebuild path.

## What Changes

- **Data sources section:** a combined activity's page gets a "Data sources" section for its athlete. It lists each kind of data that more than one member recorded, and which member it comes from.
  - The kinds are: map and GPS, distance and speed, elevation, heart rate, power, cadence, temperature, running dynamics and laps.
  - Dives also have depth and dive data.
- **Choosing a member:** the athlete picks the member a kind comes from. That member becomes its top-ranked source.
  - The other members stay as fallbacks, in their current order, for moments the chosen one has no data.
  - Channels that belong together always move together: power with pedal and balance data, distance with speed.
- **Rebuilding:** saving a choice rebuilds the combined activity from the new ranking and writes it like any other change to it. Members aren't touched.
- **Chart labels:** each chart on a combined activity's page names the device it came from, or every device when it switches between them, for every viewer who may see that chart.
- **No lexicon change:** `combined.sources` already holds the ranking.

## Capabilities

### New Capabilities
- `feed/combined-sources`: choosing which member of a combined activity each kind of data comes from, and showing viewers where each chart came from.

### Modified Capabilities
<!-- None: feed/combined-activities' default ranking still applies until the athlete chooses. -->

## Impact

- **core/data:**
  - a fixed map of data kinds to `ChannelSource` channels;
  - `ActivityManager.setSources(combined, kind, member)` rewrites `combined.sources` and rebuilds with `ActivityCombiner`;
  - the combiner reports, per channel, the time ranges each member supplied, for the chart labels.
- **Device names per range:** these come from the members' records, which the page already loads. The combined streams record stores nothing new.
- **Screens:**
  - `ActivityDetailScreen` State and Events (sources, choose a source);
  - a sources section in `composeApp`, using `AppSegmentedControl` or a menu per kind;
  - device captions on the charts.
- **AppView:** no change.
