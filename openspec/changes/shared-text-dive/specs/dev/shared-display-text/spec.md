## ADDED Requirements

### Requirement: A dive's text has one path
The shared UI SHALL write a dive's figures, its dive computer's settings, its depth readout and a table row's cells and spoken sentence only through the shared texts both UIs resolve (`FigureTexts`, `CardLines`, `LapTableTexts`), with no second Compose helper that chooses the same text.

#### Scenario: A dive's settings
- **WHEN** a dive's computer stored a ppO₂ warning of 1.40 bar and ZHL-16C with gradient factors 30/70
- **THEN** both UIs read those figures from `FigureTexts`, and no Compose-only function formats them
