# Spec Delta

## ADDED Requirements

### Requirement: Threshold history's text and charts are chosen in shared code
What threshold history says (a group's heading, help and summary, a threshold's label, values and change, a value's date and source, Show all, the remove label, the chart's description and its first date) and each threshold's chart (its steps, dots, labels and the run on to today) SHALL be chosen in shared code that both UIs ask.

#### Scenario: A faster pace
- **WHEN** a Run's threshold pace rose from 3.0 to 3.2 m/s since March 2 for an athlete who reads pace per mile
- **THEN** both UIs say it got faster by the same number of seconds per mile since Mar 2

### Requirement: The type search's match rule is shared
Whether an activity type matches a type picker's query SHALL be decided by shared code that both UIs ask, given the type's and its sport's labels as each UI reads them.

#### Scenario: A token
- **WHEN** the athlete searches "sup" in either UI's type picker
- **THEN** Stand Up Paddle is listed
