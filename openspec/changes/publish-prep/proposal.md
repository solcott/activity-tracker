# Proposal

## Why

The repository is going public on GitHub as a portfolio piece. An audit of the tree and its whole history found no personal GPS files or real secrets, but it did find a verification screenshot showing a real neighbourhood map near home, two absolute paths into the maintainer's home folder, no license, no CI, and a README that opens with setup steps rather than what the project is. A visitor (often a recruiter, reading for a minute) should see what was built and that it is tested, and nothing in the repo should point at where the maintainer lives. Future verification screenshots and test fixtures need the same guard, so the rule should be checked, not remembered.

## What Changes

- Remove the six verification screenshots in `openspec/changes/archive/2026-10-19-combine-suggestions/verify/` (one shows a real map near home), and drop their column from that change's `verify/notes.md`.
- Replace the two absolute home-folder paths in archived OpenSpec changes with neutral wording.
- Add an Apache-2.0 `LICENSE`.
- Rewrite the top of `README.md` as a showcase: what the app does, a few screenshots taken from the committed screenshot-test references, an architecture diagram, tech highlights, a CI badge, and a note on the AI-assisted, spec-driven workflow (`openspec/`, `.claude/`, `.agents/`). Setup instructions stay, below. Correct stale "Known gaps" and the Layout table.
- Add a GitHub Actions workflow that runs the Linux-runnable checks on every push and pull request, reading `kmp-dataresult` from GitHub Packages with the workflow's own token.
- Add `scripts/check-public-safety.sh`, run by CI, which fails when a tracked file could reveal personal data: an activity file (FIT, GPX, TCX, KML, and their `.gz`) without a license or source note beside it, an absolute home-folder path, or a screenshot under `openspec/` (verification evidence stays local).

- Stop describing the app as modelled on other products ("Strava-style", "as Garmin draws it", "Strava's brackets") in the README, AGENTS.md, code comments, lexicon descriptions, live specs and archived changes. Mentions that state interoperability stay: file formats, exports other services import, Garmin's FIT SDKs and fields, threshold sources. The `2026-10-20-garmin-power-curve` archive becomes `2026-10-20-clock-time-power-curve`.

Out of scope, done by hand outside the repo before the first push (described in design.md): rewriting history to drop the screenshots and replace the author email with the GitHub no-reply address, running gitleaks over the rewritten history, and the GitHub account and repository settings.

## Capabilities

### New Capabilities

- `dev/continuous-integration`: what runs automatically on pushes and pull requests, and what it needs to resolve dependencies.
- `dev/public-repository`: what a public checkout must and must not contain: license, showcase README, and no file that reveals the maintainer's location or local machine, enforced by the safety check.

### Modified Capabilities

None.

## Impact

- New files: `LICENSE`, `.github/workflows/ci.yml`, `scripts/check-public-safety.sh`.
- Edited: `README.md`, `CLAUDE.md` (commands list gains the safety check), `openspec/changes/archive/2026-10-19-combine-suggestions/verify/notes.md`, `openspec/changes/archive/2026-10-13-spaces-local-stack/tasks.md`, `openspec/changes/archive/2026-10-21-adopt-dataresult/design.md`.
- Deleted: the six PNGs in `openspec/changes/archive/2026-10-19-combine-suggestions/verify/`.
- No app, AppView, lexicon or build-logic code changes. Future OpenSpec verification steps keep their screenshots out of the repo.
