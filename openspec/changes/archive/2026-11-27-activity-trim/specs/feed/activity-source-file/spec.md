# Spec Delta

## ADDED Requirements

### Requirement: Trimming leaves the source as imported
Trimming an imported activity (`feed/activity-trim`), whether before it's saved from the import preview or afterwards from its page, SHALL leave its source exactly as imported: the same bytes, name and format, still stored in the athlete's `self` space under the activity's record key. Downloading the original of a trimmed activity SHALL give the whole untrimmed file. Exporting the activity in its current form SHALL give the trimmed data.

#### Scenario: Trim after import
- **WHEN** an athlete trims the last 3.1 km off an activity imported from a FIT file, then downloads its original
- **THEN** the download is the FIT file byte for byte as imported, including the 3.1 km

#### Scenario: Trim in the import preview
- **WHEN** an athlete applies a suggested trim in the import preview and imports the file
- **THEN** the saved activity is trimmed and its source is the whole file as picked
