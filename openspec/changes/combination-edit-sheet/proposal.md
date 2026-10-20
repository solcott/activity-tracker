# Proposal

## Why

On the owner's page of a combined activity, the header lists every data kind with a switch, and adds a large "Comes from" dropdown under each kind that two or more members recorded. Each member also gets a remove button. On a ride with around ten kinds, these controls fill the side pane and push the page's real content far down. Yet they are only needed while the athlete is changing the combination, and every chart already names the device its data came from.

## What Changes

- The header of a combined activity shows "Combined from" with its members as links, plus an **Edit** button for the owner. The remove buttons and the inline data sources section are gone from the page.
- Edit opens a sheet that holds all the combination editing:
  - the members, each with its remove action (still confirmed);
  - the data sources, one compact line per kind: its label, the member it comes from (a menu when two or more members recorded it), and its include switch.
- Choosing, excluding and removing behave exactly as they do now. Only where the controls live changes.
- Followers and other viewers still see the member list and the chart captions, but no Edit button.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/combined-sources`: the owner's data sources move from an inline page section to a sheet opened from an Edit control on the page, alongside removing members.

## Impact

- `composeApp` detail UI: `ActivityDetailUi.kt` (`Header`), `RecordingsUi.kt` (`Members`), `DataSourcesUi.kt` (compact rows), and a new sheet composable that uses `AppBottomSheet`.
- `composeResources/values/strings.xml`: Edit label and sheet title.
- No presenter, state, event, lexicon or AppView changes. `ActivityDetailState.Loaded.dataSources` and `manage` are reused as is.
