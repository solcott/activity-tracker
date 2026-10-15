# Tasks

## 1. Client data: held photos and media views

- [x] 1.1 Add `HeldPhotos` in `core/data/.../activity/ActivityPhotos.kt`: `NewPhoto`s by blob CID in a `MutableStateFlow`, with `hold`, `get`, `drop`, `url(cid)` (`heldphoto://blob/{cid}`) and `SCHEME`. Add `photoCid(url)` (the URL's last path segment, query ignored). `FeedRepository` owns one (`heldPhotos`). `SessionClients.upload(photo, held)` holds what it uploads; `publish`, `mergeInto` and `ActivityManager.edit` pass `feed.heldPhotos`. Verify with the 1.3 tests.
- [x] 1.2 `mediaViews(record, served, visibility, held, ownBlobUrl)`: served view by `photoCid` (dropping held bytes), else held URL, else `ownBlobUrl` only for Public, else the held URL. `LocalRewrites` takes the holder and passes the view's visibility. `FeedRepository` drops held bytes for photos in every view it reads from the AppView. Verify with the 1.3 tests.
- [x] 1.3 jvm tests in `core/data`: `mediaViews` picks served (signed or not) → held → `ownBlobUrl` (Public only) → held URL (non-public); `photoCid` on public, signed and held URLs; editing an activity with a new photo shows it from a held URL and holds its bytes, and the AppView serving it drops them. Verify with `./gradlew :core-data:jvmTest`.

## 2. Client UI

- [x] 2.1 `existingMedia` in `DetailsForm` matches views through `photoCid`. Presenter test: editing a Followers activity whose AppView URLs are signed keeps both photos in the form. Verify with `./gradlew :app-presenters:jvmTest`.
- [x] 2.2 `composeApp`: a Coil `Fetcher.Factory` for `HeldPhotos.SCHEME` that answers from the current session's holder (failing without a request when the bytes aren't held), registered with `setSingletonImageLoaderFactory` in `App`; `SessionGraph` exposes `feedRepository`, and `App` points the fetcher at its `heldPhotos` for the session's composition. Verify it compiles for every target in `./gradlew check`.

## 3. Verification

- [x] 3.1 `./gradlew check` passes, and `openspec validate show-own-space-photos-before-indexing` passes.
- [x] 3.2 Run the desktop app (or local stack), edit a Followers activity to add a photo, and confirm it shows before the AppView indexes the edit, with no `sync.getBlob` request for it.
