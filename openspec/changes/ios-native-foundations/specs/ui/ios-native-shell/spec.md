## REMOVED Requirements

### Requirement: Native text matches the shared text
**Reason**: The iOS app no longer keeps a hand-written copy of some strings to compare. Its whole String Catalog is generated from the shared strings.
**Migration**: `dev/ios-native-resources` covers it for every string: "Every shared string is in the iOS app under its name" and "Generated iOS resources match their sources". `checkIosStrings` is replaced by `checkIosResources`, and `./gradlew generateIosResources` rewrites the catalog.
