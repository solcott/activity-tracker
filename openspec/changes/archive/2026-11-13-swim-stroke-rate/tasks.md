## 1. Strokes per minute

- [x] 1.1 `StreamKind.StrokeCadence`, `FigureUnit.StrokesPerMinute` and a `unit_strokes_per_minute` string; iOS strings regenerated
- [x] 1.2 `ActivityCharts.cadenceKind` replaces `stepCadence`; the laps table takes its unit from it
- [x] 1.3 `ChartsBuilder.forSport` and `Figures` read a swim's cadence as `StrokeCadence`, unchanged in value

## 2. Verify

- [x] 2.1 Page test: a swim's stats, card and laps in strokes/min, as stored
- [x] 2.2 `./gradlew check -Pkotlin.incremental=false`, the Release build, `xcodebuild test` on iOS 26.0 and 18.4
- [x] 2.3 Record the findings in the roadmap
