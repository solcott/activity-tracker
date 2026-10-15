## MODIFIED Requirements

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
