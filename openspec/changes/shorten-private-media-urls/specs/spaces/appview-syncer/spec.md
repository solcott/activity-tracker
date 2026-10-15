## ADDED Requirements

### Requirement: Signed photo URLs are short-lived
A signed URL for a photo of a non-public activity SHALL stop working at most 30 minutes after the AppView issues it, and at least 15 minutes after, so a URL passed on to someone else works only briefly. A viewer who may still see the activity SHALL get a new URL the next time a view containing the photo is loaded. The AppView SHALL NOT let a client cache the photo's response past the moment its URL expires.

#### Scenario: Expiry within bounds
- **WHEN** the AppView signs a photo URL for a viewer at any moment
- **THEN** the URL's expiry is between 15 and 30 minutes later

#### Scenario: Stable within a window
- **WHEN** the AppView signs a viewer's URL for the same photo twice a few seconds apart, within the same 15-minute window
- **THEN** both URLs are the same, so the viewer's device can reuse its cached image

#### Scenario: Expired URL
- **WHEN** a viewer who may see the activity requests the photo with a URL whose expiry has passed
- **THEN** the AppView doesn't serve the photo

#### Scenario: Cache lifetime bounded by the URL
- **WHEN** the AppView serves a photo of a non-public activity
- **THEN** the response may be cached only privately, and for no longer than the time left until the URL expires
