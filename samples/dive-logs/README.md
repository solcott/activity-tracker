# Sample dive logs

Files for trying the import screen by hand (Record → Import) and for manual testing of the UDDF and Subsurface readers in `core/activityfile`.

| File | Format | Notes |
|---|---|---|
| `subsurface/test-uddf-export-events.uddf` | UDDF 3.2 | Tiny, a few events |
| `subsurface/test42.uddf` | UDDF 3.2 | Mid-size logbook, several dives |
| `subsurface/test-apd-inspiration.uddf` | UDDF 3.2 | Large, closed-circuit rebreather dive |
| `subsurface/TwoTimesTwo.ssrf` | Subsurface | Tiny |
| `subsurface/abitofeverything.ssrf` | Subsurface | Many dives with sites, tanks, gases and events |

## Source and license

All five files come from the [Subsurface](https://github.com/subsurface/subsurface) repository (`dives/`, fetched from `master` on 2026-10-09). Subsurface is licensed under GPL-2.0 (see its README; the full text is in `subsurface/LICENSE-GPL-2.0.txt`).

These files are sample data kept only for manual testing. They are not part of the app and are not shipped in any build, so the project's own license is unaffected. If you redistribute them, keep this notice and the license text with them.
