# Backlog

Planned work that isn't ready to start yet. Add new items at the bottom. Delete an item when it ships, because git history keeps the record.

## Suggest athletes from the user's Bluesky follows

Follows in this app (`app.activitytracker.graph.follow`) are separate from Bluesky follows (`app.bsky.graph.follow`). Both are records in the same repo on the user's PDS, but the AppView indexes only `Nsid.indexedCollections`, so a new user starts with an empty network. Suggest the athletes the user already follows on Bluesky who also use this app, and let them follow those athletes here with one tap.

- **Suggest, don't merge.** Following someone from a suggestion writes our own follow record through `ProfileRepository.follow`. Never write `app.bsky.*` records, and keep this app's follow graph separate from Bluesky's.
- **Source of Bluesky follows.** Prefer having the client list the user's own `app.bsky.graph.follow` records from their PDS with `com.atproto.repo.listRecords`, so the AppView never touches Bluesky data. The descriptor is hand-written in `core/lexicon/.../atproto/Repo.kt`, because client code can't use an atproto SDK.
- **AppView query.** For example `app.activitytracker.graph.getSuggestedFollows`, or a batch `actor.getProfiles`. It takes the candidate DIDs, caps how many it accepts, keeps the ones the AppView knows, and excludes accounts the viewer already follows here.
- **Open questions.** Where suggestions appear: the empty feed, the user's own profile, or a screen of their own. Whether to filter to "has posted an activity" or "has an `actor.profile`".
- **Privacy.** Bluesky follows are already public. Store nothing new beyond the follows the user chooses to create.
