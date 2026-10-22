# Spec Delta

## ADDED Requirements

### Requirement: The detail screen lists comments a page at a time
The detail screen SHALL show the activity once it has loaded, and its first page of threads as soon as that has, with the comment count. Replies SHALL be shown indented under their thread's top-level comment or placeholder. It SHALL load the next page when the viewer scrolls near the end of the listed threads, until there are no more, showing that more are loading.

#### Scenario: Popular activity
- **WHEN** a viewer opens an activity with 200 comments in 120 threads
- **THEN** the activity and the 30 oldest threads with their first replies are shown, and the count says 200

#### Scenario: Scrolling to the end
- **WHEN** the viewer scrolls near the last listed thread while there are more
- **THEN** the next page is listed after it, once, however many times the viewer reaches the end while it loads

#### Scenario: Every comment loaded
- **WHEN** the viewer has reached the last page
- **THEN** nothing more is loaded, and nothing shows more loading

### Requirement: A failed page of comments keeps what's listed
When a page of an activity's comments fails to load, the detail screen SHALL keep the activity and the comments already listed, show the failure and offer to try again. Trying again SHALL load only the page that failed.

#### Scenario: Next page fails
- **WHEN** loading the next page fails
- **THEN** the comments already listed stay, the screen says the rest couldn't be loaded and offers to try again, and trying again lists the next page after them without loading the earlier pages again

#### Scenario: Comments fail but the activity loads
- **WHEN** the activity loads and its first page of comments fails
- **THEN** the activity and its count are shown, with the failure and a way to try again where the comments would be

### Requirement: Comments outlast a reload and coming back
A reload of the detail screen SHALL keep the comment threads listed until its first page answers, then replace the pages and the extra replies loaded before. Coming back to the screen SHALL show the pages and replies loaded before at once.

#### Scenario: Reload
- **WHEN** the viewer reloads the screen
- **THEN** the activity and its first page are loaded again, the threads listed stay until that page answers, and then the pages and the extra replies loaded before are replaced

#### Scenario: Back to the activity
- **WHEN** the viewer has loaded a second page of threads and more replies in one thread, opens a commenter's profile and goes back
- **THEN** both pages and the thread's extra replies show at once, and none of them is loaded again

## REMOVED Requirements

### Requirement: The detail screen loads comments as the viewer scrolls
**Reason**: It stated several behaviors in one requirement, over the 500-character limit strict validation checks.
**Migration**: Split into "The detail screen lists comments a page at a time", "A failed page of comments keeps what's listed" and "Comments outlast a reload and coming back", which together keep its text and every scenario.
