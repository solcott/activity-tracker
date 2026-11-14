## 1. Steps per minute

- [x] 1.1 `StreamKind.StepCadence` and `FigureUnit.Spm`, with a `unit_spm` string and the iOS strings regenerated
- [x] 1.2 `ChartsBuilder.forSport` turns a foot sport's cadence line into a doubled `StepCadence` line
- [x] 1.3 `Figures` doubles a foot sport's record cadence as `StepCadence` and `Spm` (stats list, card headline, average/max table)
- [x] 1.4 The laps table shows a foot sport's lap cadence doubled in spm
- [x] 1.5 Lexicon descriptions say what is stored on foot

## 2. Tests

- [x] 2.1 Page tests: a run's stats, card and table in spm, doubled; a ride's in rpm
- [x] 2.2 The import test checks the run's cadence as 170 spm
- [x] 2.3 Rerecord affected Android and iOS 26.0 screenshots, if any (none: no sample draws a run's cadence)

## 3. Verify

- [x] 3.1 `./gradlew check -Pkotlin.incremental=false`, the Release build, `xcodebuild test` on iOS 26.0 and 18.4
- [x] 3.2 Record the findings in the roadmap
