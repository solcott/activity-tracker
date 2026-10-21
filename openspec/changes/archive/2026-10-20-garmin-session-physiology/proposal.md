# Proposal

## Why

Garmin devices record an effort's exercise load, stamina and estimated sweat loss in the FIT session, and Garmin Connect shows them, but import drops them. The reference mountain bike ride carries exercise load 187, stamina 100% → 70% (minimum 48%) and sweat loss 764 ml. In the app all of it is missing, so the activity page says less about the effort than the file does.

## What Changes

- FIT import keeps the session's exercise load (`training_load_peak`), beginning and ending stamina potential, minimum stamina, and estimated sweat loss, where the file records them.
- They are stored with the activity's training load, at the narrower of its charts visibility and the athlete's training visibility, like training effect and RMSSD. They are not on the activity record.
- The training load card shows them to viewers admitted to the training load: exercise load, stamina as start → end with the minimum, and sweat loss in ml or fl oz.
- FIT export writes them back, so an exported file keeps them.
- Out of scope: intensity minutes and the paved/unpaved split. Neither is in the FIT file; Garmin Connect works them out on its servers.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `feed/activity-file-data`: Training load gains exercise load, stamina and sweat loss.
- `feed/activity-page-layout`: The training load card shows them.

## Impact

- **Lexicon**: `app.activitytracker.feed.activityZones#trainingLoad` gains optional `exerciseLoad`, `staminaStart`, `staminaEnd`, `staminaMin` and `sweatLoss`, mirrored in `core/lexicon` `TrainingLoad` (use the `lexicon-change` skill). The change is additive, so older records stay valid.
- **Decoders**: the JVM (`GarminJavaFitDecoder`), wasmJs (`GarminJsFitDecoder`) and iOS (`SwiftFitDecoder.swift`) decoders read session fields 168, 178, 205, 206 and 207. Fields 178 and 205–207 aren't in the public FIT profile (SDK 21.218), so each decoder reads them by field number.
- `DecodedSummary`, `ImportedActivity`, `ActivityDraft` (into the zones record), `FitWriter`/`ExportActivity`.
- **AppView**: `ActivityStreamsViews` serves the new fields with bounds, like `hrvRmssd`. No new table if training load is stored as JSON. Otherwise there's a Flyway migration.
- `app/presenters` `ActivityPage`/`ChartsBuilder.trainingLoad` and the `composeApp` card UI and strings.
- No backfill: activities imported earlier don't show the new figures (`Older activities are unchanged`).
