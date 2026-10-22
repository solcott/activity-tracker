# Spec Delta

## MODIFIED Requirements

### Requirement: The detail screen loads comments as the viewer scrolls
The detail screen SHALL show the activity once it has loaded, and its first page of threads as soon as that has, with the comment count. Replies SHALL be shown indented under their thread's top-level comment or placeholder. It SHALL load the next page when the viewer scrolls near the end of the listed threads, until there are no more, showing that more are loading. A page that fails SHALL keep what's listed, show the failure and offer to try again, and trying again SHALL load only that page. A reload SHALL keep the threads listed until its first page answers. Coming back to the screen SHALL show the pages and replies loaded before at once.

#### Scenario: Popular activity
- **WHEN** a viewer opens an activity with 200 comments in 120 threads
- **THEN** the activity and the 30 oldest threads with their first replies are shown, and the count says 200

#### Scenario: Scrolling to the end
- **WHEN** the viewer scrolls near the last listed thread while there are more
- **THEN** the next page is listed after it, once, however many times the viewer reaches the end while it loads

#### Scenario: Every comment loaded
- **WHEN** the viewer has reached the last page
- **THEN** nothing more is loaded, and nothing shows more loading

#### Scenario: Next page fails
- **WHEN** loading the next page fails
- **THEN** the comments already listed stay, the screen says the rest couldn't be loaded and offers to try again, and trying again lists the next page after them without loading the earlier pages again

#### Scenario: Comments fail but the activity loads
- **WHEN** the activity loads and its first page of comments fails
- **THEN** the activity and its count are shown, with the failure and a way to try again where the comments would be

#### Scenario: Reload
- **WHEN** the viewer reloads the screen
- **THEN** the activity and its first page are loaded again, the threads listed stay until that page answers, and then the pages and the extra replies loaded before are replaced

#### Scenario: Back to the activity
- **WHEN** the viewer has loaded a second page of threads and more replies in one thread, opens a commenter's profile and goes back
- **THEN** both pages and the thread's extra replies show at once, and none of them is loaded again

### Requirement: The rest of a thread's replies are listed in pages
The comments query SHALL take a thread's top-level comment instead of a cursor of threads, and SHALL then list that thread's replies listed for the viewer, oldest first, a page at a time with a cursor, applying `Who sees a comment` and `Who sees a hidden comment`. The screen SHALL list the replies it loads after the ones shown, once each, until there are no more. A page of replies that fails SHALL keep the replies shown, and trying again SHALL load only that page.

#### Scenario: View more replies
- **WHEN** the viewer chooses to view the 2 more replies of a thread showing three
- **THEN** the fourth and fifth replies are shown after the third, and the control goes away

#### Scenario: Many replies
- **WHEN** a thread has 80 replies the viewer may see and they keep choosing to view more
- **THEN** each choice lists the next page of replies after those shown, until all 80 are listed

#### Scenario: View more fails
- **WHEN** loading more replies fails
- **THEN** the replies already shown stay, and the control offers to try again

#### Scenario: Trying again after a failed page of replies
- **WHEN** a thread shows its first three replies and a page of ten more, the next page fails, and the viewer tries again
- **THEN** only the failed page is loaded, and its replies are shown after the thirteen already listed

#### Scenario: Activity the viewer can't see
- **WHEN** a viewer who may not see the activity asks for a thread's replies
- **THEN** the answer is `NotFound`, and reveals none of the replies
