# combine-suggestions: end-to-end checks (3.1)

Run on 2026-10-19 on the local stack (`scripts/dev.sh`), signed in as alice.test in the desktop app (`:desktopApp:hotRun`), driven
through the compose-hot-reload MCP server. Activities were seeded straight into alice's repo with `putRecord`: public, today, with
hand-made rkeys. The desktop file picker can't be driven from the MCP server, so the import path was not exercised end to end. The
same prompt was reached through a manual entry instead, and the import paths are covered by the presenter tests
(`ImportActivityPresenterTest`, `ImportLogbookPresenterTest`).

| Check | Result |
|---|---|
| Save opens the activity with a prompt | Manual 5-min "Cooldown jog" right after a seeded "Evening run": prompt "Continue after a break?", Combine and Not now |
| Not now leaves a banner | Banner with Combine and Don't combine on the same page |
| Combine | Opens the combined activity, titled "Evening run" (the earlier one), combined from both |
| MTB runs 25 min apart, second starts 400 m higher | Banner "Shuttle or lift between runs?" on run 1 (cue from the polyline and streams altitude) |
| Don't combine | Banner gone from run 1, and run 2's page shows none |
| Ski morning (15:00–17:45) and afternoon (19:00) | Banner "Continue after a break?" on the afternoon; Combine opens the combined "Morning ski" day |
| Two dives an hour apart | Never suggested to each other. Dive 2's page shows no banner |

Seed-data side effect: Dive 1 (16:00–16:45) falls inside the seeded morning ski, so Dive 1 and the ski day suggest each other as
"Same activity on another device?". Per the spec (overlap suggestions ignore sport, as import's duplicate check does), that's
expected.

Not checked here: the record-finish path (desktop has no GPS) and a logbook's result list. Both are covered by presenter tests only.
