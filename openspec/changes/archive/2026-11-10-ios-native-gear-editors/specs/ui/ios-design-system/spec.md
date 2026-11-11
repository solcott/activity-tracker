# Spec Delta

## ADDED Requirements

### Requirement: Native form text fields sit in the form's rows
A native form's text field SHALL be able to show as a row of the system's grouped form, its label leading and its text trailing on one line, or as its label above several lines of text. The text that explains it, or its error, SHALL show under the row, and VoiceOver SHALL read the label with the text.

#### Scenario: A field marked wrong
- **WHEN** a form marks its name field as wrong with "Give your gear a name"
- **THEN** that text shows under the row in the error colour

### Requirement: Native form menu choices keep their choice trailing
A native form's menu choice SHALL show its label leading, wrapping when long, and the choice with the system's menu mark at the trailing end on one line, and SHALL open the choices with the current one checked. VoiceOver SHALL read its name with the choice.

#### Scenario: A long label beside a choice
- **WHEN** a choice's label is too long for its row's width
- **THEN** the label wraps and the choice stays on one line at the trailing edge
