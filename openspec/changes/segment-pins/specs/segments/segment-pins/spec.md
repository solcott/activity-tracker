## ADDED Requirements

### Requirement: Operators pin a segment's status or name
The AppView SHALL let an operator pin a segment's status, its name, or both. Each pin SHALL record the segment's URI, the pinned value, the DID of the operator who pinned it, when, and an optional note of at most 500 characters. A segment SHALL have at most one status pin and one name pin. Pinning again SHALL replace the earlier pin of that kind. Pins SHALL be operator data. No record, space or XRPC method SHALL carry them, and no segment view SHALL say that a value is pinned.

#### Scenario: Status pinned
- **WHEN** an operator pins segment S `archived` with the note "junk test segment"
- **THEN** the AppView holds a status pin on S naming `archived`, the operator's DID, the time and the note

#### Scenario: Pinned again
- **WHEN** S has a status pin `archived` and an operator pins it `active`
- **THEN** S has one status pin, `active`, naming the second operator and time

#### Scenario: Not revealed
- **WHEN** a viewer opens a segment whose status is pinned `active`
- **THEN** the view shows the status `active`, with nothing saying it is pinned

### Requirement: Only valid status pins are accepted
The AppView SHALL refuse a pin on a segment it hasn't indexed. A status pin SHALL be `active`, `archived` or `duplicate`, never `proposed`. A `duplicate` pin SHALL name another indexed segment of the same sport group and kind. That target SHALL NOT itself be `duplicate`, and no segment SHALL have a duplicate pin naming the pinned segment. A refused pin SHALL change nothing and SHALL say why.

#### Scenario: Unknown segment
- **WHEN** an operator pins a URI the AppView hasn't indexed
- **THEN** the pin is refused as not found

#### Scenario: Pinning proposed
- **WHEN** an operator pins a segment `proposed`
- **THEN** the pin is refused

#### Scenario: Duplicate of another sport
- **WHEN** an operator pins a `run` segment `duplicate` of a `ride` segment
- **THEN** the pin is refused

#### Scenario: Duplicate of itself
- **WHEN** an operator pins a segment `duplicate` of itself
- **THEN** the pin is refused

#### Scenario: Chain
- **WHEN** segment A is pinned `duplicate` of B, and an operator pins B `duplicate` of C
- **THEN** the pin is refused, naming A

### Requirement: Only valid name pins are accepted
A name pin SHALL be either a name that is non-empty after trimming and at most 100 graphemes, or one of the segment's own name proposals that isn't withdrawn. A refused pin SHALL change nothing and SHALL say why.

#### Scenario: Blank name
- **WHEN** an operator pins a name that is only spaces
- **THEN** the pin is refused

#### Scenario: Another segment's proposal
- **WHEN** an operator pins segment S's name to a proposal made for segment T
- **THEN** the pin is refused

### Requirement: A pinned status beats the derived one
While a status pin applies, the segment's status SHALL be the pinned one in every view, search, matching, naming, Mayors and leaderboard rule, and for a `duplicate` pin, the segment it duplicates SHALL be the pinned target. Neither the status pass nor indexing nor a duplicate re-check SHALL change it.

#### Scenario: Pinned active, not yet ridden
- **WHEN** an athlete's segment with efforts from one athlete is pinned `active`
- **THEN** it is `active` at once: any viewer's search returns it, and it gets a leaderboard

#### Scenario: Never archived while pinned
- **WHEN** a segment pinned `active` was first indexed 200 days ago and has one rider
- **THEN** the status pass leaves it `active`

#### Scenario: Pinned archived, then ridden
- **WHEN** a segment pinned `archived` would have reached 3 distinct athletes
- **THEN** it stays `archived` and gets no new efforts

#### Scenario: Re-lined while pinned
- **WHEN** the creator of a segment pinned `active` moves its end so its line now duplicates an earlier active segment
- **THEN** it stays `active`, and its efforts are recomputed against the new line

#### Scenario: Pinned duplicate's original deleted
- **WHEN** segment A is pinned `duplicate` of B, and B's creator deletes B
- **THEN** A's status is derived again as for any duplicate of a deleted segment

### Requirement: Pinning active skips the riders threshold
A segment pinned `active` SHALL be matched against past and new segment tracks. It SHALL be listed in search to every viewer, and SHALL never be archived for lack of riders. For a `run`, it SHALL get leaderboards and Mayors and accept name proposals under the usual rules. The pin SHALL NOT change who counts as a rider, a regular or a voter, nor when a segment's elevation comes from efforts.

#### Scenario: Proposed segment pinned active
- **WHEN** a proposed segment that past activities pass is pinned `active`
- **THEN** it keeps its efforts and is matched against any past segment track not yet matched to it

#### Scenario: Archived segment pinned active
- **WHEN** a segment archived by the status pass is pinned `active`
- **THEN** it keeps its efforts, is matched against past segment tracks again, and is listed in search

#### Scenario: Duplicate pinned active
- **WHEN** a segment marked `duplicate` of another is pinned `active`
- **THEN** both are `active` and matched, and past segment tracks are matched against the pinned one

### Requirement: Pinning archived keeps a segment's efforts
A segment pinned `archived` SHALL keep its efforts and be served by URI, but SHALL get no new efforts, SHALL NOT be listed in search, and SHALL have no leaderboard or Mayors.

#### Scenario: Active segment pinned archived
- **WHEN** an active segment with efforts from 40 athletes is pinned `archived`
- **THEN** its activities still list their efforts on it, its leaderboard returns no entries, and search no longer returns it

### Requirement: Pinning duplicate removes a segment's efforts
A segment pinned `duplicate` SHALL lose its efforts, as a derived duplicate does, SHALL get no new ones, and SHALL be served by URI with its target.

#### Scenario: Active segment pinned duplicate
- **WHEN** an active segment A is pinned `duplicate` of B
- **THEN** A's efforts are removed, A's view shows `duplicate` of B, and no new efforts are matched to A

### Requirement: A segment taken out of use releases its duplicates and proposals
When a segment is pinned `archived` or `duplicate`, the segments marked `duplicate` of it SHALL be checked again, as when it is archived. Its open name proposals SHALL stay open, SHALL NOT be adopted, and SHALL expire as usual.

#### Scenario: Open proposal on an archived segment
- **WHEN** an active segment with an open proposal is pinned `archived`
- **THEN** the proposal isn't adopted, and expires 90 days after it opened unless the segment is active again first

#### Scenario: Its duplicates re-checked
- **WHEN** segment C is marked `duplicate` of A, and A is pinned `archived`
- **THEN** C is checked again and becomes live if it duplicates no other live segment

### Requirement: A duplicate pin applies only while its target is there
A `duplicate` pin SHALL apply only while its target is indexed with the same sport group and kind as the pinned segment. While it doesn't apply, the segment's status SHALL be derived as if it had no status pin. The pin SHALL be kept and SHALL apply again once its target is indexed again with a matching sport group and kind.

#### Scenario: Target deleted and rewritten
- **WHEN** A is pinned `duplicate` of B, B is deleted, and B's record is later written again under the same URI
- **THEN** A's status is derived while B is gone, and A is `duplicate` of B again once B is indexed

#### Scenario: Target's sport changed
- **WHEN** A is pinned `duplicate` of B, and B's creator changes B's sport group
- **THEN** A's status is derived while their sport groups differ

### Requirement: Pins hold until unpinned
A pin SHALL hold until an operator unpins it, through record edits, re-lines, renames and replays of the segment's records. A pin SHALL be kept when its segment is deleted, and SHALL apply again if a record is indexed again under the same URI. Pins SHALL NOT pass to a segment that takes over the deleted one's handoff (`segments/segment-handoffs`).

#### Scenario: Record edited
- **WHEN** the creator of a segment pinned `archived` renames it
- **THEN** it stays `archived`

#### Scenario: Deleted and replayed
- **WHEN** the AppView replays a pinned segment's delete and then its create
- **THEN** the segment is indexed with its pins in effect

#### Scenario: Handoff successor
- **WHEN** an active segment with a pinned name is deleted and the importer republishes it from its handoff
- **THEN** the republished segment has no pins, and its name comes from its handoff and proposals

### Requirement: Unpinning a status derives it again
Unpinning a status SHALL make the segment `duplicate` of the earliest-indexed live segment of its sport group and kind that it duplicates. If there is none, it SHALL be `active` when it was `active` at pin time and its line, sport group and kind haven't changed since. Otherwise it SHALL get the status of a segment that duplicates nothing, promoted at once if it has enough riders. Becoming live SHALL match it against past tracks, and becoming `duplicate` SHALL remove its efforts.

#### Scenario: Unpinning an archive of an active segment
- **WHEN** an active segment pinned `archived` is unpinned, its line unchanged
- **THEN** it is `active` again, and its leaderboard returns its entries again

#### Scenario: Unpinning an active pin on an unridden segment
- **WHEN** an athlete's segment first indexed 200 days ago, with one rider, was pinned `active` and is unpinned
- **THEN** it is `proposed`, and the next status pass archives it

#### Scenario: Unpinning a duplicate pin
- **WHEN** segment A pinned `duplicate` of B is unpinned, and A's line doesn't duplicate any earlier live segment
- **THEN** A becomes live and past segment tracks are matched against it

### Requirement: Unpinning a name derives it again
Unpinning a name SHALL make the segment's current name its latest adopted name in effect, else its original name.

#### Scenario: Unpinning a name
- **WHEN** a segment with the adopted name "Wiggly Woods" has the pinned name "Trail 214" and is unpinned
- **THEN** its current name is "Wiggly Woods"

### Requirement: Pins are set through an internal service
The AppView SHALL pin and unpin only through an internal service that the admin site's operator actions call. That service SHALL take the operator's DID and an optional note, SHALL apply the change and every effect it has in one transaction, and SHALL never write to a repo or space. It SHALL also list a segment's pins, and list pins whose segment isn't indexed. It SHALL NOT check who may call it: the admin site checks the operator's role and records each call in its audit log.

#### Scenario: Pinned through the admin site
- **WHEN** an operator pins a segment `archived` from the admin site
- **THEN** the pin, the segment's new status and the re-check of its duplicates are written together, and the admin site's audit log records the action

#### Scenario: Unpinning nothing
- **WHEN** an operator unpins the status of a segment that has no status pin
- **THEN** nothing changes, and the service says there was no pin
