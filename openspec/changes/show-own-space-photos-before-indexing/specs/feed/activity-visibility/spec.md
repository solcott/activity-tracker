## ADDED Requirements

### Requirement: Own non-public photos show before indexing
On the device that published or edited an activity, the athlete's photos on it SHALL show from the bytes that device uploaded until the AppView serves them, at any visibility. A non-public activity's photo SHALL NOT load from the PDS's public blob endpoint, which refuses blobs only a tier space holds. Once the AppView serves a photo, its URL SHALL be shown instead.

#### Scenario: Followers activity edited with a new photo
- **WHEN** an athlete adds a photo to their Followers activity and saves, before the AppView has indexed the edit
- **THEN** the activity shows the new photo on that device, loaded from the bytes it uploaded, and no request for it is made to the PDS

#### Scenario: Photos the AppView already serves
- **WHEN** the athlete edits a Mutuals activity's title, before the AppView has indexed the edit
- **THEN** the photos it already served keep showing from the AppView's URLs

#### Scenario: AppView catches up
- **WHEN** the AppView serves a photo the device had shown from its own bytes
- **THEN** the photo shows from the AppView's URL, and the device stops holding its bytes

#### Scenario: Bytes not held
- **WHEN** the app restarts before the AppView indexes the athlete's Only-me edit with a new photo
- **THEN** that photo shows as an empty placeholder, makes no request to the PDS, and stays on the activity if the athlete edits it again

#### Scenario: Public photo without held bytes
- **WHEN** a Public activity's photo isn't served by the AppView yet and the device doesn't hold its bytes
- **THEN** it loads from the PDS's public blob endpoint

#### Scenario: Other devices
- **WHEN** the athlete opens the activity on another device before the AppView indexes it
- **THEN** that device shows the activity as the AppView serves it, without the photos it hasn't indexed

### Requirement: Served photos matched by blob
The client SHALL recognize a photo the AppView serves by its blob CID whether or not its URL is signed for the viewer, so an athlete editing their non-public activity keeps every photo it has.

#### Scenario: Editing a Followers activity's text
- **WHEN** an athlete edits only the description of an indexed Followers activity with two photos
- **THEN** the edit form shows both photos, and the saved record still holds both
