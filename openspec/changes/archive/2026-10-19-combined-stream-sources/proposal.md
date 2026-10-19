# Proposal

## Why

When two devices recorded the same ride, the athlete knows which one to trust for each kind of data:
- the chest strap's heart rate, not the watch's wrist sensor;
- the bike computer's barometric altitude;
- the watch's GPS when the bike computer lost signal under trees.

They may also want some data left out altogether, such as a power meter that kept dropping out.

`combine-activities` ranks each channel's sources by sample count and includes every channel any member recorded, which is often right but not always. This change lets the athlete choose a source or leave a kind out, and shows every viewer which device each chart came from.

It builds on `combine-activities`, and needs its `combined.sources` ranking and rebuild path.

## What Changes

- **Data sources section:** a combined activity's page gets a "Data sources" section for its athlete. It lists every kind of data any member recorded, included by default, and which member it comes from.
  - The kinds are: map and GPS, distance and speed, elevation, heart rate, power, cadence, temperature, running dynamics and laps.
  - Dives also have depth and dive data.
- **Choosing a member:** for a kind two or more members recorded, the athlete picks the member a kind comes from. That member becomes its top-ranked source.
  - The other members stay as fallbacks, in their current order, for moments the chosen one has no data.
  - Channels that belong together always move together: power with pedal and balance data, distance with speed.
- **Leaving a kind out:** the athlete can exclude any kind, recorded by one member or several. The combined activity is rebuilt without its channels, charts and figures. Including it again restores the default ranking for that kind.
- **Rebuilding:** saving a choice rebuilds the combined activity from the new ranking and writes it like any other change to it. Members aren't touched.
- **Chart labels:** each chart on a combined activity's page names the device it came from, or every device when it switches between them, for every viewer who may see that chart.
- **Lexicon description only:** `combined.sources` already holds the ranking. `#channelSource.members` now documents that an empty list means the channel is excluded. No new field and no Kotlin model change.

## Capabilities

### New Capabilities
- `feed/combined-sources`: choosing which member of a combined activity each kind of data comes from or leaving a kind out, and showing viewers where each chart came from.

### Modified Capabilities
<!-- None: feed/combined-activities' default ranking still applies until the athlete chooses or excludes. -->

## Impact

- **core/data:**
  - a fixed map of data kinds to `ChannelSource` channels;
  - `ActivityManager.setSources(combined, kind, choice)` (a member, exclude, or back to the default) rewrites `combined.sources` and rebuilds with `ActivityCombiner`;
  - `ActivityCombiner.rankings` treats a stored empty list as excluded instead of refilling it with the default;
  - the combiner reports, per channel, the time ranges each member supplied, for the chart labels.
- **Device names per range:** these come from the members' records, which the page already loads. The combined streams record stores nothing new.
- **Screens:**
  - `ActivityDetailScreen` State and Events (sources, choose a source, include or exclude a kind);
  - a sources section in `composeApp`, with an include switch and, for kinds two or more members recorded, a menu of members per kind;
  - device captions on the charts.
- **Lexicons:** the `#channelSource.members` description in `lexicons/app/activitytracker/feed/activity.json`.
- **AppView:** no change.
