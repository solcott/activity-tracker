## 1. Map images

- [x] 1.1 `ActivityCardContent.routeMap`
- [x] 1.2 `RouteMapImage`: MapKit snapshot of `RouteMap.frame` at the tile's size and appearance, the route drawn over it, cached
- [x] 1.3 `MediaStrip` shows it in a signed-in session, the line preview meanwhile, on failure and elsewhere

## 2. Verify

- [x] 2.1 `./gradlew check -Pkotlin.incremental=false`, the Release build, `xcodebuild test` on iOS 26.0 and 18.4, snapshots unchanged
- [x] 2.2 The feed seen on a signed-in simulator (or listed under Not verified)
- [x] 2.3 Record the findings in the roadmap
