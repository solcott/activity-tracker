# Spec Delta

## MODIFIED Requirements

### Requirement: A new search keeps the last results until it answers
When the athlete changes the search query, athlete search and segment name search SHALL keep the last query's results on screen with an indication that it is searching until the new query's first page answers, then show only the new results. A blank query SHALL list nothing and search for nothing; in segment browse it SHALL return to the area's segments where a map is shown.

#### Scenario: Refining a query
- **WHEN** results for "ann" are listed and the athlete types "anna"
- **THEN** the "ann" results stay with a searching indicator until the "anna" results replace them, and no "ann" page is added after they do

#### Scenario: Clearing the query
- **WHEN** the athlete clears the search box
- **THEN** no results are listed, nothing shows as searching, and no search is made

#### Scenario: Refining a segment name
- **WHEN** segment results for "hog" are listed and the athlete types "hogb"
- **THEN** the "hog" results stay with a searching indicator until the "hogb" results replace them

## ADDED Requirements

### Requirement: Segment browse loads like a paged list
Segment browse SHALL load like the paged lists: it SHALL show that it is loading until an area's or a query's first page answers, never an empty state while a fetch is in flight, and it SHALL keep its segments and area when the athlete returns from a segment page. Changing the sport group or the name scope SHALL drop the previous results and show loading until the new ones answer.

#### Scenario: First area
- **WHEN** an athlete opens Segments mode and the area's segments haven't answered yet
- **THEN** the list shows that it is loading, not that there are no segments here

#### Scenario: Changing the sport group
- **WHEN** an athlete viewing ride and run segments chooses run, and the run segments haven't answered yet
- **THEN** no ride segments stay listed and the list shows that it is loading
