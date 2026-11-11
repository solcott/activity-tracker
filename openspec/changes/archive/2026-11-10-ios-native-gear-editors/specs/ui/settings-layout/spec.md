# Spec Delta

## ADDED Requirements

### Requirement: The gear editor is native on iOS
On iOS the gear item form SHALL be drawn with the system's own grouped form and navigation bar, and SHALL offer the shared form's fields in its words and limits: the name, required; the type and, when it has kinds, the kind; brand and model; the weight in whole grams; the starting distance in the athlete's units and the starting time; and the notes. Save SHALL be in the bar. Its loading SHALL follow `ui/content-loading`.

#### Scenario: A new item
- **WHEN** alice opens Add gear from Gear
- **THEN** the form is titled "Add gear", its name is empty, and it starts at Only me

#### Scenario: Saving without a name
- **WHEN** alice taps Save with the name empty
- **THEN** nothing is saved, and the name field reads "Give your gear a name" in the error colour

#### Scenario: A kind on one line
- **WHEN** alice edits her gravel bike, a bike whose kind is Gravel
- **THEN** the Kind row shows "Gravel" at its trailing end on one line, and its menu offers None and each kind of bike

#### Scenario: Weight takes digits only
- **WHEN** alice types "9a" into an empty weight
- **THEN** the weight reads "9"

#### Scenario: A starting distance in miles
- **WHEN** alice's units are imperial
- **THEN** the starting distance is labelled "Starting distance (mi)"

#### Scenario: A failed save
- **WHEN** saving fails because the server can't be reached
- **THEN** the failure shows at the top of the form and what alice entered stays

### Requirement: The gear editor's choices on iOS
On iOS the gear form SHALL offer the shared form's choices in its words: type, kind and who may see it as menus, with the Followers note as shared; the sports it is for and the activity types it is the default for, each in a sheet by category, a sub-sport under its sport; its collections, ticked in place; and the acquired date, from a calendar of past days, which can be cleared.

#### Scenario: Sports in a sheet
- **WHEN** alice chooses Ride and Run in the sports sheet and closes it
- **THEN** the Sports row reads "Ride, Run"

#### Scenario: No later day
- **WHEN** alice opens the acquired date's calendar
- **THEN** no day after today can be chosen

### Requirement: Picking a gear photo on iOS
On iOS the gear form SHALL let the athlete pick its photo from the photo library, show it in the form, change it and remove it. A picked photo SHALL be re-encoded and scaled as on every other platform before it is saved, dropping its metadata, its location included.

#### Scenario: Adding a photo
- **WHEN** alice taps Add photo and picks a photo taken with her phone's location on
- **THEN** the form shows the photo with Change photo and Remove photo, and the photo saved carries no location

#### Scenario: Removing it
- **WHEN** alice taps Remove photo
- **THEN** the form shows no photo and offers Add photo

### Requirement: A saved item's management on iOS
On iOS the form of a saved item SHALL also show what it is mounted on and since when, which opens that item; the day it was retired; Retire, or Put back in use for a retired item; and Delete. Delete SHALL ask first in the system's alert, in the shared form's words, offering Retire instead; only its confirmation SHALL delete.

#### Scenario: A mounted chain
- **WHEN** alice edits Chain #4, mounted on her gravel bike since 1 July 2026
- **THEN** the form reads "Mounted on Gravel bike since 2026-07-01", and tapping it opens the gravel bike's page

#### Scenario: Asked before deleting
- **WHEN** alice taps Delete on her gravel bike
- **THEN** an alert asks "Delete this gear?", says "Deleting removes it for good. Retiring keeps it and its details, out of the way.", and offers Retire, Delete and Cancel

### Requirement: The collection editor is native on iOS
On iOS the collection form SHALL be drawn with the system's own grouped form and navigation bar, and SHALL offer what the shared form offers, in the same words: the name, required; the notes, with the collections help; and the items in order, each with its photo, name, kind, who may see it and Retired where it is, moved and removed in place. Save SHALL be in the bar. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Saving without a name
- **WHEN** alice taps Save on a new collection with no name
- **THEN** nothing is saved, and the name field reads "Give your collection a name" in the error colour

#### Scenario: Reordering
- **WHEN** alice drags Race shoes above Aero helmet in "Race kit" and saves
- **THEN** the collection lists Tarmac, Race shoes and Aero helmet, in that order

#### Scenario: Moving read aloud
- **WHEN** VoiceOver is on Aero helmet in the items
- **THEN** its actions include "Move Aero helmet up", "Move Aero helmet down" and "Remove Aero helmet"

### Requirement: Adding items to a collection on iOS
On iOS the collection form's Add items SHALL offer the athlete's other items to tick and add them at the end in the order ticked. Once the collection holds 50 items, the form SHALL say it holds no more in place of Add items.

#### Scenario: Adding in the order ticked
- **WHEN** alice ticks the power meter and then her wetsuit under Add items, and taps Add
- **THEN** the items end with the power meter, then the wetsuit

#### Scenario: Nothing left to add
- **WHEN** every item alice has is in the collection and she opens Add items
- **THEN** the sheet reads "All your gear is in this collection."

#### Scenario: A full collection
- **WHEN** the collection holds 50 items
- **THEN** the form reads "A collection holds at most 50 items." and offers no Add items

### Requirement: The iOS gear editors open full width
On iOS both editors SHALL open full width over Settings, alone or beside the list, and back, or a save that succeeds, SHALL return to the page they were opened from.

#### Scenario: Editing a collection from beside the list
- **WHEN** on an iPad alice opens "Race kit" from Gear beside Settings' list and taps Edit
- **THEN** the collection form takes the whole window, and back returns to "Race kit"

#### Scenario: Saving a new item
- **WHEN** alice saves a new item from Add gear
- **THEN** the form closes onto Gear, which lists the item
