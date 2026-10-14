# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
