## REMOVED Requirements

### Requirement: Clear at rest
**Reason**: The iOS navigation bar is no longer drawn in Compose. Every iOS screen is native and shows the system's own bar, which behaves this way itself (ui/ios-native-shell, ui/ios-design-system).
**Migration**: None.

### Requirement: Content under the bar is not legible behind its controls
**Reason**: The iOS navigation bar is no longer drawn in Compose; the system's bar blurs or edges what scrolls under it.
**Migration**: None.

### Requirement: Soft edge toward the content
**Reason**: The iOS navigation bar is no longer drawn in Compose; iOS 26's own bar has the soft scroll edge.
**Migration**: None.
