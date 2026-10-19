# Tasks

## 1. One average per card

- [x] 1.1 In `MetricCards.card`, give each series with a stored figure (heart rate, power, speed, cadence, temperature) the headline's average and maximum where `Figures` has them, and verify a new `ActivityPageTest` case: a swim with `avgSpeed` differing from its speed stream's mean gives the Pace card's series the stored average
- [x] 1.2 Add `ActivityPageTest` cases verifying that a heart rate stream with no stored average keeps the stream's average on its card's series, that temperature keeps the stream's maximum, and that `ActivityCharts.lines` is unchanged
- [x] 1.3 On desktop, open the pool swim sample and verify the Pace card's chart description reads "average 1:59 /100 yd", matching the headline
- [x] 1.4 Round a pace once, from the speed, in the page's pace figures (design decision 3), and verify on desktop that the interval run's Pace card headline and chart description give the same best pace (5:57 /mi or 5:58 /mi, the same in both)

## 2. Checks

- [x] 2.1 Run `./gradlew check composeStabilityCheck` and verify it passes
