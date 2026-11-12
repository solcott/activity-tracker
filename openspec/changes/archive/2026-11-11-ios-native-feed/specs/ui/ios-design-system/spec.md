# Spec Delta

## ADDED Requirements

### Requirement: A native page pulls to refresh
A native page that refreshes SHALL use the system's pull to refresh, whose spinner shows until the page's state says the refresh has ended, and SHALL show a failed refresh in the page's own refresh row.

#### Scenario: A slow refresh
- **WHEN** the athlete pulls the native feed down and the AppView takes two seconds to answer
- **THEN** the system's spinner shows for those two seconds and then closes
