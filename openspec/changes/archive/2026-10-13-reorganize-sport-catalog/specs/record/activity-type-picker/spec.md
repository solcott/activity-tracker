# Spec Delta

## ADDED Requirements

### Requirement: Types are grouped into categories
The type list SHALL group activity types under these categories, in this order: Running, Cycling, Gym & Fitness, Mind & Body, Combat, Swimming, Multisport, Walking, Hiking & Climbing, Winter Sports, Parasports, Water Sports, Diving, Team Sports, Racket Sports, Target, Skating, Adventure, Motorsports, Other.

#### Scenario: Walking has its own category
- **WHEN** the athlete opens the type list
- **THEN** Walk is listed under Walking, and no category named Outdoor is shown

#### Scenario: Regrouped sports
- **WHEN** the athlete opens the type list
- **THEN** Yoga is listed under Mind & Body, Boxing under Combat, Golf under Target, Snowmobile under Winter Sports and Fish under Water Sports

### Requirement: Each type is listed once
Every activity type the catalog knows SHALL be listed in exactly one category. A sub-sport SHALL be listed in its sport's category unless the catalog files it under another category, in which case it SHALL be listed only there.

#### Scenario: Indoor variant filed in Gym & Fitness
- **WHEN** the athlete opens the type list
- **THEN** Indoor Row is listed under Gym & Fitness and not under Water Sports, while Row is listed under Water Sports

#### Scenario: Indoor variant kept with its sport
- **WHEN** the athlete opens the type list
- **THEN** Treadmill Run is listed under Running only, and Indoor Ride under Cycling only

### Requirement: Sub-sports filed elsewhere are top-level rows
A sub-sport listed in a category other than its sport's SHALL show as a top-level row there and SHALL NOT be hidden by collapsing.

#### Scenario: Indoor Row under Gym & Fitness
- **WHEN** the athlete opens the type list
- **THEN** Indoor Row is visible and choosable under Gym & Fitness without expanding anything

### Requirement: Wheelchair and indoor track labels
The wheelchair sub-sports SHALL read "Push - Walk Speed" and "Push - Run Speed", and the indoor track run SHALL read "Indoor Track Run".

#### Scenario: Parasports labels
- **WHEN** the athlete opens the type list
- **THEN** Parasports shows Wheelchair, Push - Walk Speed and Push - Run Speed

## MODIFIED Requirements

### Requirement: Sports collapse in the type list
Each sport that has sub-sports listed under it SHALL be collapsible, in every category,, with an expand control separate from choosing the sport. Each such sport SHALL start collapsed every time the list opens. A collapsed sport SHALL hide its sub-sports, and expanding it SHALL show them beneath it.

#### Scenario: Collapsed when opened
- **WHEN** the athlete opens the type list
- **THEN** every collapsible sport, Run included, is collapsed and no sub-sport listed under its sport is shown

#### Scenario: Expand a sport
- **WHEN** the athlete expands Ride
- **THEN** Ride's sub-sports appear beneath it, and the other sports stay collapsed

#### Scenario: Expanding does not choose
- **WHEN** the athlete expands or collapses a sport
- **THEN** the chosen activity type is unchanged and the list stays open

#### Scenario: Reopened collapsed
- **WHEN** the athlete expands a sport, closes the list and opens it again
- **THEN** every sport is collapsed again

### Requirement: Hidden choice is marked
When the chosen activity type is a sub-sport of a collapsed sport, that sport's row SHALL name the chosen sub-sport under its label.

#### Scenario: Chosen sub-sport collapsed
- **WHEN** Gravel Ride is the chosen type and the list opens
- **THEN** the Ride row shows "Gravel Ride" under its label

### Requirement: Search shows every match
While a search query is entered, the type list SHALL show every matching type, including matching sub-sports, regardless of which sports are collapsed.

#### Scenario: Search for a sub-sport
- **WHEN** the athlete types "gravel" into the search
- **THEN** Gravel Ride is shown without expanding Ride

## REMOVED Requirements

### Requirement: Shortcut types always visible
**Reason**: Shortcuts listed a type in a second category. Each type is now listed once, and a sub-sport may be filed under another category instead.
**Migration**: Covered by "Sub-sports filed elsewhere are top-level rows" and "Each type is listed once". Types that were only reachable twice (such as Walk under Running) are found in their one category or by search.
