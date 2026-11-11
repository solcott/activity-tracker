# Spec Delta

## ADDED Requirements

### Requirement: A loaded list reaches Swift as its own types
When a state's loaded content is itself a list of contract objects, Swift SHALL read it as objects of that type, in order, with each property under a name Swift accepts, without the contract changing its content for Swift's sake.

#### Scenario: The ignored devices
- **WHEN** Swift reads the loaded Ignored devices sample
- **THEN** it has two devices, the Garmin Edge 840 with its serial's end "2071" and the Polar H10 marked as known only by its model
