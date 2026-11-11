# Spec Delta

## ADDED Requirements

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
