# Tasks

## 1. Cause

- [x] 1.1 On the iOS 26 simulator, scroll an activity page until "Details" is behind the back button, screenshot it, and record which cause (fade band, missing scroll connection, or missing Haze source) it is in design.md

## 2. Fix

- [x] 2.1 Apply the fix the cause calls for in `AppTopBar.ios.kt` (or the detail screen's scroll wiring); verify with a simulator screenshot that the heading is illegible behind the back button and the bar is clear at rest
- [x] 2.2 Check the feed, a profile and settings scrolled under the bar on iOS 26, and the activity page on an iOS 18 simulator (material and hairline), each by screenshot

## 3. Verification

- [x] 3.1 iOS build passes (`xcodebuild … CODE_SIGN_IDENTITY=- build`) and `./gradlew check` passes
