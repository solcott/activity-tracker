# Spec Delta

## ADDED Requirements

### Requirement: The quote sheet is native on iOS
On iOS, Quote on a native post card SHALL open a native sheet with the post being quoted, the athlete's words, who may see the quote (no wider than the quoted post), the shared too-long and public-only notes, and the error of a quote that couldn't be posted; Post SHALL be offered only while the words fit and aren't blank. Delete on the athlete's own quote SHALL ask first, in the shared words.

#### Scenario: Quoting on iOS
- **WHEN** alice taps Quote on bob's Followers post on iOS
- **THEN** a sheet opens offering Followers and Mutuals, and Post stays unavailable until she types her words

#### Scenario: Deleting a quote on iOS
- **WHEN** alice chooses Delete quote on her own quote on iOS
- **THEN** she is asked "Delete this quote?" before it is deleted
