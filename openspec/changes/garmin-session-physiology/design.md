# Design

## Context

Training load travels as `activityZones#trainingLoad`: `DecodedSummary` → `ImportedActivity` → `ActivityDraft` → the zones record in the tier the narrower visibility picks. The AppView decodes it from the record's raw JSON in `ActivityStreamsViews.trainingLoad`, bounding each field, so there is no column and no migration. `ChartsBuilder.trainingLoad` → the training load card shows it. `FitWriter` writes it back to the session on export.

Three decoders fill `DecodedSummary`: `GarminJavaFitDecoder` (JVM/Android, Garmin Java SDK 21.218), `GarminJsFitDecoder` (wasmJs, Garmin's JS SDK) and `SwiftFitDecoder.swift` (iOS, Garmin's Swift SDK). The reference file's session gives:

| Field | # | Raw | Meaning | Garmin Connect |
|---|---|---|---|---|
| `training_load_peak` | 168 | 12240282 (÷65536) | 186.77 | Exercise Load 187 |
| (undocumented) | 178 | 764 | ml | Est. Sweat Loss 764 ml |
| (undocumented) | 205 | 100 | % | Beginning Potential 100% |
| (undocumented) | 206 | 70 | % | Ending Potential 70% |
| (undocumented) | 207 | 48 | % | Min Stamina 48% |

Field 168 has an SDK getter. 178 and 205–207 aren't in SDK 21.218's `SessionMesg`.

## Goals / Non-Goals

**Goals:**
- Keep these five values from Garmin files, with the same results on every platform, at training-load visibility.

**Non-Goals:**
- Stamina as a stream (Garmin's chart). The records' stamina fields are also undocumented, and a summary is enough for now.
- Computing any of them for non-Garmin files.
- Resting and active calories, and intensity minutes (see proposal).

## Decisions

**1. Lexicon fields, all optional integers, on `trainingLoad`.** `exerciseLoad` is whole units, 0–10,000. `staminaStart`, `staminaEnd` and `staminaMin` are percent, 0–100. `sweatLoss` is ml, 0–20,000. They live with training load because they describe the athlete's physiology and training state, which the athlete's training visibility already governs. *Rejected: the activity record.* Calories are public there, but stamina and load reveal fitness the athlete may keep private.

**2. Read undocumented fields by number.** Java: `mesg.getField(178)?.getIntegerValue()` (the SDK keeps unknown fields in the message). JS: decode with `includeUnknownData: true` and read the numeric keys. Swift: the SDK's unknown-field access on `SessionMesg` (or its developer/unknown field list). A shared `SessionFieldNumbers` object in common code holds the five numbers and the sentinel values (`0xFF`, `0xFFFF`), and a common helper rejects out-of-range values, so the three decoders differ only in how they fetch a raw number. If a platform SDK can't expose unknown fields, that decoder leaves the four undocumented fields null and keeps 168. The spec's "same on every platform" makes that a task blocker to resolve rather than a silent difference.

**3. Rounding.** Exercise load rounds to the nearest whole number (186.77 → 187, matching Connect).

**4. Export.** `FitWriter` writes 168 through the SDK's typed field. It writes 178 and 205–207 as raw session fields of the same base types Garmin uses: `uint16` for 178 and `uint8` for 205–207.

**5. Card layout.** The training load card lists exercise load after the training effects, then stamina ("100% → 70%" with "min 48%"), then sweat loss, then RMSSD. Sweat loss in imperial is fl oz to one decimal.

## Risks / Trade-offs

- [Garmin renumbers or repurposes undocumented fields] → Bounds checks drop implausible values, and a future public profile can replace the numbers in one place.
- [A non-Garmin device writes something else at 178 or 205–207] → We only read them when `file_id.manufacturer` is Garmin, or `development`, which our own FIT export writes, so an exported file keeps them on re-import.
- [Swift SDK lacks unknown-field access] → Task 2.3 checks this first. The fallback is a small raw-field read for the session message in the Swift decoder.
