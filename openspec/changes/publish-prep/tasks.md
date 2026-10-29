# Tasks

## 1. Remove what reveals location or the local machine

- [x] 1.1 `git rm` the six PNGs in `openspec/changes/archive/2026-10-19-combine-suggestions/verify/`, and drop the Evidence column from that folder's `notes.md`, keeping its rows. Verify: `git ls-files openspec | grep -iE '\.(png|jpe?g|gif|webp)$'` prints nothing, and `notes.md` names no PNG.
- [x] 1.2 Replace the absolute paths in `openspec/changes/archive/2026-10-13-spaces-local-stack/tasks.md:31` ("the session plan") and `openspec/changes/archive/2026-10-21-adopt-dataresult/design.md:19` ("a local checkout, see `dataresult.dir` in the README"). Verify: `git grep -nE '/(Users|home)/[A-Za-z]'` prints nothing.

## 2. Public-safety check

- [x] 2.1 Write `scripts/check-public-safety.sh` (POSIX sh, executable, over `git ls-files`) with the three rules in design.md: activity files need a `LICENSE*` or `README*` beside them, no images under `openspec/`, no `/Users/<name>/` or `/home/<name>/` paths (the script excludes itself). Print `path[:line]: reason` per finding and exit 1, or exit 0 silently. Verify: it exits 0 with no output on the tree after group 1.
- [x] 2.2 Exercise each rule in a throwaway branch or with `git add -N`: stage a GPX in `build-logic/` (no note beside it), a PNG under `openspec/changes/`, and a markdown line with a home-folder path for a made-up user. Confirm each is reported and the exit code is 1, and that an untracked, ignored `build/x.gpx` is not. Then discard them. Verify: four observed outcomes match `dev/public-repository`'s scenarios.
- [x] 2.3 Add the command to the Commands block in `CLAUDE.md` (it is `AGENTS.md` through the symlink), next to `scripts/check-agent-sync.sh`. Verify: `scripts/check-agent-sync.sh` still passes.

## 3. License

- [x] 3.1 Add the standard Apache License 2.0 text as `LICENSE`, with a copyright line naming Scott Olcott and 2026 in the appendix boilerplate. Verify: the file matches the text at apache.org/licenses/LICENSE-2.0.txt apart from the filled-in boilerplate.

## 4. CI workflow

- [x] 4.1 Add `.github/workflows/ci.yml` as design.md describes: push and pull_request triggers; `permissions: contents: read, packages: read`; checkout; setup-java with Corretto 25; setup-gradle; `scripts/check-public-safety.sh`; `./gradlew check -x :androidApp:testDebugScreenshotTestDefaultTestSuite --continue`; and `GITHUB_ACTOR`/`GITHUB_TOKEN` passed in `env`. Verify: `actionlint` (if installed) or a YAML parse passes.
- [x] 4.2 Locally, confirm the exact command the workflow runs succeeds on macOS: `./gradlew check -x :androidApp:testDebugScreenshotTestDefaultTestSuite` (through the gradle-runner agent). Verify: a green verdict. A task name that doesn't resolve fails here rather than in CI.
- [x] 4.3 Note in the workflow, as a comment, why the screenshot suite is excluded (macOS layoutlib references). The Linux trial happens after the first push (see Workflow follow-up). Verify: the comment is present.

## 5. README

- [x] 5.1 Write the showcase top of `README.md`: a one-paragraph pitch; the CI badge (`actions/workflows/ci.yml/badge.svg` for `main`); a row of 3–4 screenshots linked from existing references (`FeedScreenshotsKt/FeedLoaded_400x1000_…`, `ActivityDetailScreenshotsKt/ActivityDetailLoaded_dark_…`, `SegmentLeaderboardScreenshotsKt/SegmentLeaderboardLoaded_400x1000_…`, `ProfileScreenshotsKt/ProfileLoaded_dark_…`); a feature list (recording, import/export of FIT/GPX/TCX and dive logs, segments with leaderboards and community names, per-detail visibility with ATProto spaces, gear, moderation); a Mermaid architecture diagram; tech highlights (KMP on four platforms, Compose Multiplatform, Circuit, Metro, Ktor, Room 3, hand-written XRPC/OAuth DPoP, Postgres AppView, screenshot tests, custom detekt rules). Verify: every image path in the README exists (`grep -o 'androidApp/[^)]*\.png' README.md | xargs ls`).
- [x] 5.2 Add a "How it was built" section: AI coding assistants under a spec-driven OpenSpec workflow, with links to `openspec/specs`, `openspec/changes/archive`, `.claude/` and `.agents/`. Verify: the links resolve to tracked paths.
- [x] 5.3 Add a License section naming Apache-2.0 and noting the third-party fixture licenses (`samples/dive-logs/`, `core/activityfile/src/jvmTest/resources/dives/`). Verify: the paths exist.
- [x] 5.4 Bring Layout and Known gaps up to date: add the missing top-level module directories (`server/segment-importer`, `app/fixtures`, `build-logic`, `config`, `samples`, `openspec`, and any other `settings.gradle.kts` include missing from the table), and remove or correct gaps the code has since closed (route maps now draw with MapLibre). Verify: each `include(...)` in `settings.gradle.kts` maps to a Layout row, and each remaining gap holds when checked against the code.
- [x] 5.5 Run `scripts/check-public-safety.sh` and `./gradlew format` (the README isn't formatted, but this catches stray edits). Verify: both pass with no changes.

## 6. Integration check

- [x] 6.1 Run `openspec validate publish-prep --strict` and the gradle-runner's `./gradlew check` once more on the finished tree. Verify: both pass.

## Workflow follow-up

- Before the first push, follow design.md's Migration Plan by hand. It rewrites history in a mirror clone (author email, the `openspec/*.png` files, home paths), scans it with gitleaks, and pushes to a private repository first.
- After the first green run on GitHub: try the screenshot suite on Linux by removing the `-x` in a branch. Keep it if it passes. Otherwise leave the exclusion and its comment.
- Switch the repository to public once CI is green and the README renders.
- Archive the change.
