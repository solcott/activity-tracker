# Design

## Context

See proposal.md (Why) and the two specs for the requirements. Constraints that shape the approach:

- The build needs JDK 25 (Amazon, provisioned through Gradle toolchains via foojay; `gradle/gradle-daemon-jvm.properties`), the Android SDK, and `io.github.solcott` artifacts from GitHub Packages (`settings.gradle.kts`), which ask for a token even for public reads. Credentials come from `gpr.user`/`gpr.key` or `GITHUB_ACTOR`/`GITHUB_TOKEN`.
- `./gradlew check` covers every target, Android lint, ktfmt, detekt, dependency sorting, Compose stability, and the Android screenshot suite, whose references were rendered by layoutlib on macOS.
- `gradle.properties` asks for a 6 GB Gradle heap plus a 4 GB Kotlin daemon. GitHub's standard Linux runner for public repositories has 4 vCPUs and 16 GB.
- History rewriting cannot happen inside a commit. Removing the screenshots from HEAD alone leaves them in history, so the in-repo deletion and a one-time history rewrite are two halves of one fix.

## Goals / Non-Goals

**Goals:**
- A green CI run on `main` that a visitor can trust, without secrets set up by hand.
- A safety check simple enough to read in one sitting, so contributors and agents see what it rejects.

**Non-Goals:**
- iOS or wasm builds in CI (macOS runners cost more and wasm tests are disabled anyway).
- Release builds, signing or publishing app artifacts.
- Moving `kmp-dataresult` to Maven Central.
- Changing app behaviour or fixtures. The Corner Canyon trail names in `ActivityDetailSamples` and the power-only `edge840-mtb-power.csv` stay: they reveal no address.

## Decisions

**Apache-2.0 license.** It matches `kmp-dataresult`, includes a patent grant, and is common for Kotlin and Android projects. MIT is shorter but has no patent clause. Third-party fixtures keep their own MIT and GPL-2.0 notices beside them, as they do today. Subsurface's GPL samples stay acceptable because they ship in no build (`samples/dive-logs/README.md`).

**CI runs `./gradlew check` on ubuntu-latest, without the screenshot suite.** On Linux, Kotlin/Native skips the Apple targets, so `check` runs the JVM, Android and server tests plus every static check. One command keeps CI and `CLAUDE.md` in step: a new module joins CI by joining `check`. The screenshot suite (`:androidApp:testDebugScreenshotTestDefaultTestSuite`) is excluded with `-x`. Its references are macOS layoutlib renders, and font rasterising on Linux will likely differ. One task tries it on Linux. If it passes, the exclusion goes. If it fails, it stays, with a comment in the workflow pointing to the macOS references.
- *Alternative:* a hand-picked list of `jvmTest` tasks. It is faster, but it drifts from `check` as modules are added.
- *Alternative:* a macOS runner for the full `check`. It is slower to queue, and it would run iOS tests nobody asked CI for.

**Workflow shape.** One job: `actions/checkout`, `actions/setup-java` (Corretto 25, used by Gradle itself), `gradle/actions/setup-gradle` (wrapper validation plus cache), the safety script, then the Gradle command. Permissions are `contents: read, packages: read`. `GITHUB_ACTOR` and `GITHUB_TOKEN` are passed to the build environment, which `settings.gradle.kts` already reads. Pin actions by major tag. Gradle's configuration cache and build cache stay on as they are.

**Safety check is a POSIX shell script over `git ls-files`.** It lives with the other repo scripts (`scripts/check-agent-sync.sh` is the precedent) and has no dependencies beyond git, grep and find. Rules, matching `dev/public-repository`:
1. Activity-format files (`fit fit.gz gpx gpx.gz tcx tcx.gz kml kmz uddf ssrf`, case-insensitive) must share a directory with a `LICENSE*` or `README*` file.
2. No `png jpg jpeg gif webp heic mp4 mov` under `openspec/`.
3. No line matching `/Users/<name>/` or `/home/<name>/` in tracked text files. Matching only real-looking names keeps placeholders like `/path/to/...` legal. The script excludes itself, since it has to spell out the patterns.

It prints one `path[:line]: reason` per finding and exits 1, or exits 0 silently. Gradle does not wrap it: it needs no JVM, and CI runs it first so it fails fast.
- *Alternative:* gitleaks in CI. It covers secrets, not location or path leaks, and the one-time history scan below already covers secrets. It can be added later.

**README screenshots come from committed screenshot references.** The README points at existing files under `androidApp/src/screenshotTestDefaultDebug/reference/` (feed, activity detail, a segment leaderboard, a profile, each in light or dark). Nothing new is captured, so the images are fixture data by construction, and they update whenever the references do. The trade-off: reference file names carry a hash, so a renamed reference breaks its README link. The tasks check the links once, and an update to a reference that the README uses has to update the link too.
- *Alternative:* fresh device captures. They look nicer, but each one risks showing a real map or account, which is exactly what this change removes.

**The architecture diagram is a Mermaid block in the README.** GitHub renders Mermaid natively. It stays in text, so it diffs and needs no image file. It shows clients → PDS (`putRecord`) → relay/Jetstream → AppView (Postgres) → clients (XRPC), with spaces sync as a side path.

**Archived OpenSpec edits are kept minimal.** In `verify/notes.md` the Evidence column is dropped and the table text stays. The two absolute paths become "the session plan" and "a local checkout (see `dataresult.dir` in README)". Archived changes are otherwise left as they were written.

## Risks / Trade-offs

- [`GITHUB_TOKEN` may be refused for Maven reads of another repository's package] → GitHub documents read access to public packages with the workflow token. If the first run fails, either grant the activity-tracker repository access in the package's settings on GitHub (no secret involved), or fall back to a `GPR_TOKEN` secret and note the deviation from the spec in the task.
- [The runner's memory is tight for a 6 GB Gradle heap plus a 4 GB Kotlin daemon plus Android lint] → if it runs out of memory, override `org.gradle.jvmargs` for CI only with `-Dorg.gradle.jvmargs=...` in the workflow, or disable parallel execution there. `gradle.properties` stays unchanged.
- [`check` takes long on a cold cache] → setup-gradle caches between runs. If it still runs over about 30 minutes, split lint into a parallel job.
- [The safety check's path rule false-positives on a legitimate example] → write examples with placeholder names (a real-looking user name after `/Users/` is still matched, so use `/path/to/...` or `~/...`).
- [The screenshots stay in history if the manual rewrite is skipped] → the migration plan makes the rewrite a precondition of the first push.

## Migration Plan

Done by hand, outside the repo, after this change is committed and before the first push to GitHub:

1. Get the GitHub no-reply address (Settings → Emails, "Keep my email addresses private"). Turn on "Block command line pushes that expose my email".
2. `brew install git-filter-repo gitleaks`.
3. `git clone --mirror` the local repository into a scratch directory. Never rewrite the working repository or its worktrees.
4. In the mirror, run `git filter-repo --mailmap mailmap.txt --replace-text replacements.txt --path-glob 'openspec/*.png' --invert-paths`. filter-repo's glob `*` also matches `/`, so this removes every PNG ever committed under `openspec/` (only the six) and keeps `verify/notes.md` and the screenshot-test references under `androidApp/`. mailmap.txt maps the old author email to the no-reply address. replacements.txt rewrites the home-folder prefix (`~` expanded) so older commits lose it too.
5. Verify in the mirror. `gitleaks detect --log-opts="--all"` is clean. `git log --all --format='%ae' | sort -u` shows only the no-reply address. `git log --all --name-only | grep -iE '\.png$' | grep openspec` is empty.
6. Create the GitHub repository as **private**, push the mirror, and turn on secret scanning and push protection. Check that the CI run is green and the README renders, then switch the repository to public.
7. Locally, set `git config user.email` to the no-reply address and re-clone (or reset `main` to the pushed history). Then recreate worktrees: old hashes no longer match.

Rollback: before step 6 the original repository is untouched. After the push, delete the GitHub repository and push again.
