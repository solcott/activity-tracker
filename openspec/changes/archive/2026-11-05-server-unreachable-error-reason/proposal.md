## Why

When the AppView, the PDS or the whole stack is down, every screen says the athlete is offline ("Couldn't load your feed. You're offline."), even though the device has a working connection. The app can't tell the two apart: every request that gets no response (a refused connection, a DNS failure, a timeout, a failed `fetch` on the web) becomes `DataError.Network`, and that always reads as "You're offline." So the athlete is told to fix the wrong thing.

## What Changes

- The reason shown for a request that got no response changes from "You're offline." to "Couldn't reach the server." This wording is true whether the device is offline or the server is down.
- Rename the string resource `error_reason_offline` to `error_reason_unreachable`, and map `DataError.Network` to it in `errorReason()`.
- Specs that say an error names the device as offline now say it names the server as unreachable. Scenarios that start with the device offline keep that condition. Only what the error says changes.
- Screenshot references for failed states (fixtures default to `DataError.Network`) are regenerated.
- Out of scope: detecting whether the device is online (a connectivity monitor), telling a refused connection from a missing network by exception type, and changing `DataError` in the `dataresult` library. The export and download strings that already say "Check your connection and try again" stay as they are.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/error-messages`: the known reason for a request that got no response is that the server couldn't be reached, not that the device is offline. This includes sign-in. Adds a scenario for the server being down while the device is online.
- `ui/content-loading`: load, refresh, paging and action failures with no response say the server couldn't be reached.
- `auth/oauth-session`: a refresh that can't reach the authorization server fails saying the server couldn't be reached.
- `segments/segment-creation`: a route or duplicate check that fails with no response says the server couldn't be reached.
- `segments/segment-favorites`: starred segments that fail to load with no response say the server couldn't be reached.
- `segments/segment-browse`: a segment list that fails to load with no response says the server couldn't be reached.

## Impact

- `ui/resources/.../values/strings.xml`: string rename and new text. There are no other locales.
- `app/common/ui/.../ErrorText.kt` (`errorReason`) and `ErrorTextTest.kt`.
- `androidApp` screenshot references for every failed-state sample.
- No change to the AppView, lexicons, presenters, `toDataError()` or the outbox. The outbox still treats `DataError.Network` as transient.
