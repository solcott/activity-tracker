# profile/private-details Specification

## Purpose

Defines where an athlete's non-public profile details (those marked Mutuals or Only me) are stored, how the athlete saves and reads them, and who sees them on a profile, with the data kept on the athlete's own PDS rather than in the AppView.

## Requirements

### Requirement: Each detail stored in exactly one place by visibility
A detail marked Public SHALL be stored in the athlete's `actor.profile` record. A detail marked Followers SHALL be stored in the athlete's `followers` tier space, one marked Mutuals in their `mutuals` tier space, and one marked Only me in their `self` tier space. In each tier space, the details SHALL be held in a single `app.activitytracker.actor.privateDetails` record with rkey `self`. No detail SHALL be stored by the AppView.

#### Scenario: Followers detail saved
- **WHEN** an athlete saves their gender with visibility Followers
- **THEN** the gender is in the `privateDetails` record of their `followers` space only

#### Scenario: Mutuals detail saved
- **WHEN** an athlete saves their weight with visibility Mutuals
- **THEN** the weight is in the `privateDetails` record of their `mutuals` space, and not in their profile record or their `self` space

#### Scenario: Only-me detail saved
- **WHEN** an athlete saves their birthdate with visibility Only me
- **THEN** the birthdate is in the `privateDetails` record of their `self` space only

#### Scenario: Public detail saved
- **WHEN** an athlete saves their location with visibility Public
- **THEN** the location is in their `actor.profile` record and in no tier space

#### Scenario: Tier emptied
- **WHEN** an athlete saves so that no detail is left in a tier
- **THEN** that tier's `privateDetails` record no longer exists

### Requirement: Tier spaces created on first use
A save that puts a detail in a tier whose space doesn't exist yet SHALL create that space, of type `app.activitytracker.space.athlete` and keyed by the tier, with the AppView's managing-app id as its read policy. A save SHALL NOT create a space for a tier it leaves empty.

#### Scenario: First Mutuals detail
- **WHEN** an athlete with no `mutuals` space saves a detail as Mutuals
- **THEN** the space `at://{did}/space/app.activitytracker.space.athlete/mutuals` exists with a managing-app read policy naming the AppView, and the detail is saved in it

#### Scenario: Space already exists
- **WHEN** an athlete saves a Mutuals detail and their `mutuals` space already exists
- **THEN** the save succeeds without creating another space

#### Scenario: Public-only save
- **WHEN** an athlete whose details are all Public saves their profile
- **THEN** no tier space is created

### Requirement: Managing app taken from the AppView's identity
The managing-app id that clients put in a tier space's policy SHALL be the AppView's own service DID, as published in its DID document, followed by `#activitytracker_space`. It SHALL NOT be derived from the address a client uses to reach the AppView.

#### Scenario: Web client behind localhost
- **WHEN** the web client, which addresses the AppView as `localhost:8081`, creates a tier space while the AppView runs behind a tunnel
- **THEN** the space's managing app is the tunnel host's `did:web` plus `#activitytracker_space`

#### Scenario: Stale managing app replaced
- **WHEN** an athlete saves a detail to a tier space whose read policy names a managing app other than the AppView's current one
- **THEN** the space's read policy is updated to the current managing app before the detail is written

#### Scenario: Unchanged save repairs a stale managing app
- **WHEN** an athlete saves their profile without changing any detail, and a tier space that holds details names a stale managing app
- **THEN** that space's read policy is updated to the current managing app, so mutuals who were refused can read it again

### Requirement: Moving a detail never loses it
When a save changes a detail's visibility, the detail SHALL be written to its new place before it is removed from its old one. If the save fails part-way, the detail SHALL remain stored in at least one place.

#### Scenario: Only me to Mutuals
- **WHEN** an athlete changes their weight from Only me to Mutuals and the save fails after the first write
- **THEN** the weight is still stored, in the `mutuals` space, the `self` space, or both

#### Scenario: Mutuals to Public
- **WHEN** an athlete changes their location from Mutuals to Public and the profile record write fails
- **THEN** the location is still stored in a tier space

### Requirement: Athlete reads their own details from their spaces
Editing a profile SHALL show every detail the athlete has set, each with the visibility of the place that holds it. A detail in the profile record is Public, and one in the `followers`, `mutuals` or `self` space is Followers, Mutuals or Only me. The athlete's own tier spaces SHALL be read with their session, without a credential exchange.

#### Scenario: Details from all three places
- **WHEN** an athlete with a public location, a Mutuals weight and an Only-me birthdate opens edit profile
- **THEN** all three details are shown, with visibilities Public, Mutuals and Only me

#### Scenario: Details from all four places
- **WHEN** an athlete with a public location, a Followers gender, a Mutuals weight and an Only-me birthdate opens edit profile
- **THEN** all four details are shown, with visibilities Public, Followers, Mutuals and Only me

#### Scenario: No tier spaces yet
- **WHEN** an athlete who has never saved a non-public detail opens edit profile
- **THEN** edit profile loads with their public details and no error

#### Scenario: Malformed tier record
- **WHEN** a tier space's `actor.privateDetails` record doesn't match the lexicon (for example, it has no `details`)
- **THEN** that tier reads as holding no details, edit profile loads with no error, and the athlete's next save replaces the record

#### Scenario: Own profile view
- **WHEN** an athlete views their own profile
- **THEN** it shows their public, Followers, Mutuals and Only-me details

### Requirement: Mutuals see Mutuals details
On another athlete's profile, a viewer SHALL see that athlete's public details plus, when the athlete's PDS admits the viewer to the athlete's `mutuals` space, the details in it. A detail present both publicly and in a tier space SHALL show its public value. A viewer SHALL request a credential for the athlete's `mutuals` space only when the profile view reports both that the viewer effectively follows the athlete and that the athlete effectively follows the viewer.

#### Scenario: Mutual views a profile
- **WHEN** bob, who follows alice and whom alice follows, opens alice's profile and alice keeps her weight as Mutuals
- **THEN** bob sees alice's weight

#### Scenario: One-way follower views a profile
- **WHEN** carol follows alice but alice doesn't follow carol, and carol opens alice's profile
- **THEN** carol sees only alice's public details, without an error

#### Scenario: One-way follower makes no mutuals request
- **WHEN** carol effectively follows alice, the profile view reports no follow of carol by alice, and carol opens alice's profile
- **THEN** carol's client requests a credential for alice's `followers` space only, and none for her `mutuals` space

#### Scenario: Follow-back not yet indexed
- **WHEN** alice has just followed carol back and the AppView hasn't indexed that follow yet
- **THEN** carol sees no Mutuals details until the profile view reports the follow-back

#### Scenario: Only-me details stay hidden
- **WHEN** bob, alice's mutual, opens alice's profile and alice keeps her birthdate as Only me
- **THEN** bob doesn't see alice's birthdate

#### Scenario: Non-follower makes no space request
- **WHEN** a viewer who doesn't follow alice opens alice's profile
- **THEN** the profile shows alice's public details and the viewer's client asks for no space credential

#### Scenario: Space read fails
- **WHEN** a mutual opens alice's profile and alice's PDS can't be reached for the space read
- **THEN** the profile still loads with alice's public details

### Requirement: Followers see Followers details
On another athlete's profile, a viewer with an effective follow of that athlete SHALL also see the details in the athlete's `followers` space, when the athlete's PDS admits them. A viewer whose follow is pending SHALL NOT request a credential for any of that athlete's tier spaces.

#### Scenario: Approved follower views a profile
- **WHEN** alice keeps her gender as Followers, and bob, whom alice approved, opens her profile
- **THEN** bob sees alice's gender

#### Scenario: Pending follower views a profile
- **WHEN** bob's follow of alice is pending and he opens her profile
- **THEN** bob sees only alice's public details, and his client asks for no space credential

#### Scenario: Open policy follower
- **WHEN** alice's policy is `open`, she keeps her gender as Followers, and carol follows her
- **THEN** carol sees alice's gender on her profile

### Requirement: Session without space access asks to sign in again
When saving or loading non-public details fails because the session lacks the `space:` scope, edit profile SHALL tell the athlete to sign in again and offer an action that does so. Other profile features SHALL keep working on that session.

#### Scenario: Old session saves a Mutuals detail
- **WHEN** an athlete whose session predates the `space:` scope saves a Mutuals detail
- **THEN** edit profile shows a sign-in-again message with an action that signs them out to the login screen, not a raw error

#### Scenario: Old session opens edit profile
- **WHEN** an athlete whose session predates the `space:` scope opens edit profile
- **THEN** their public details load and the sign-in-again message is shown

### Requirement: AppView serves public details only
The AppView SHALL NOT accept or store non-public profile details. `getProfile` SHALL return only the details in the athlete's profile record, for every viewer including the athlete. `actor.putPrivateDetails` and `actor.getPrivateDetails` SHALL NOT be served.

#### Scenario: Profile for a mutual
- **WHEN** a mutual of alice calls `getProfile` for alice
- **THEN** `details` holds only what alice's `actor.profile` record contains

#### Scenario: Removed procedure
- **WHEN** a client calls `app.activitytracker.actor.putPrivateDetails`
- **THEN** the AppView answers `404 Not Found`, since it no longer serves the method

### Requirement: Edit profile is native on iOS
On iOS edit profile SHALL be drawn with the system's own grouped form and navigation bar, pushed over the page it was opened from, with the shared form's fields in its words and limits: the profile photo, the display name and bio each with its count of characters against its maximum, Approve followers with what turning it on means, and the details. Its loading SHALL follow `ui/content-loading`, and back or a save that succeeds SHALL return to the page it was opened from.

#### Scenario: Opening it
- **WHEN** alice taps Edit profile on her profile
- **THEN** the form is titled "Edit profile" and filled from her saved profile, with Save in the bar

#### Scenario: A counter
- **WHEN** alice's display name is "Alice"
- **THEN** the display name's row reads "5/64" under it

### Requirement: Saving edit profile on iOS
On iOS edit profile's Save SHALL be in the bar, offered once the profile has loaded, and unavailable while saving or while a field is too long or not a value the lexicon allows. A failed save SHALL show at the top of the form, keeping what was entered.

#### Scenario: A bio too long from another app
- **WHEN** alice's saved bio is longer than 256 characters
- **THEN** its counter shows in the error colour, and Save is unavailable until it is shortened

#### Scenario: A failed save
- **WHEN** saving fails because the server can't be reached
- **THEN** the failure shows at the top of the form and what alice entered stays

### Requirement: Edit profile's photo on iOS
On iOS edit profile SHALL show the profile photo (the saved one, one picked and not yet saved, or the athlete's initial), let the athlete pick a new one from the photo library, and remove it. A picked photo SHALL be scaled and re-encoded as on every other platform before it is saved, dropping its metadata, its location included.

#### Scenario: Picking a photo
- **WHEN** alice taps Change photo and picks a photo taken with her phone's location on
- **THEN** the form shows the photo with Remove photo, and the photo saved is a JPEG no more than 512 pixels on its long edge that carries no location

#### Scenario: Removing it
- **WHEN** alice taps Remove photo
- **THEN** the form shows her initial and no Remove photo

### Requirement: Edit profile's details on iOS
On iOS edit profile SHALL group the details as the shared form does, a section each: the location (city, state or province, and country), the birthdate, the gender, and height and weight. Each section SHALL end with who may see every field in it, as a menu choice of the four visibilities worded for what it covers, with the Followers note when Followers is chosen and anyone can follow the athlete.

#### Scenario: Who may see the location
- **WHEN** alice opens the Location section's choice
- **THEN** it is labelled "Who can see your city, state and country" and offers Public, Followers, Mutuals and Only me, the current one checked

#### Scenario: Followers while anyone can follow
- **WHEN** alice doesn't approve followers and chooses Followers for her gender
- **THEN** the Gender section reads "Anyone who follows you can see what you share with Followers. Turn on Approve followers in your profile to choose who."

### Requirement: Edit profile warns before hiding a public detail on iOS
On iOS, a detail group that was public when the form opened, is still set, and is now set to be hidden SHALL show the shared warning that anyone may already have a copy, under that group.

#### Scenario: Hiding a public detail
- **WHEN** alice's location was Public when she opened the form and she chooses Followers for it
- **THEN** the Location section reads "Location: public until now. Hiding removes it from your profile, but anyone may already have a copy."

### Requirement: Edit profile's choices on iOS
On iOS edit profile SHALL let the athlete choose the country from every country in the device's language, searchable by name or code, or Not set; the birthdate from a calendar of past days, which can be cleared; and the gender as Not set, Female, Male, Non-binary or Self-describe, where Self-describe shows a field for the athlete's own words and a saved description shows as Self-describe with its words.

#### Scenario: Finding a country by its code
- **WHEN** alice searches the country list for "us"
- **THEN** United States is listed first, above the countries whose names merely contain "us"

#### Scenario: No later day
- **WHEN** alice opens the birthdate's calendar
- **THEN** no day after today can be chosen

#### Scenario: Describing her own gender
- **WHEN** alice chooses Self-describe and types "Agender"
- **THEN** the gender's choice reads Self-describe, the field reads "Agender", and saving stores "Agender"

### Requirement: Edit profile's height and weight on iOS
On iOS edit profile's height and weight SHALL be in the athlete's units (centimetres, or feet and inches; kilograms or pounds), each marked wrong when it isn't a height or weight the lexicon allows.

#### Scenario: Height in feet and inches
- **WHEN** alice's units are imperial
- **THEN** her height is two fields, feet and inches, and her weight is labelled "Weight (lb)"

#### Scenario: A weight that isn't one
- **WHEN** alice types "heavy" as her weight
- **THEN** the weight is marked wrong and Save is unavailable

### Requirement: Edit profile asks to sign in again on iOS
On iOS, when the session can't save hidden details until the athlete signs in again, or a save failed for that reason, edit profile SHALL say so at the top of the form with Sign in again, as the shared form does.

#### Scenario: An old session opens edit profile
- **WHEN** alice's session predates access to her spaces and she opens edit profile
- **THEN** the top of the form says she must sign in again and offers Sign in again
