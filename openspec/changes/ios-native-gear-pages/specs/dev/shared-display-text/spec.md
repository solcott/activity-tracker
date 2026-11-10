# Spec Delta

## ADDED Requirements

### Requirement: Devices read the same in both UIs
A device's name from its maker and model, the line under it (the end of its serial, or that it may match other units), and the spoken labels of its Un-ignore and Unlink actions SHALL be chosen by shared code, and both UIs SHALL show that choice wherever a device is listed.

#### Scenario: A model that names its maker
- **WHEN** a device's maker is "Shearwater" and its model "Shearwater Perdix AI"
- **THEN** both UIs name it "Shearwater Perdix AI"

#### Scenario: A device known only by its model
- **WHEN** a Polar H10 has no serial and is known only by its model
- **THEN** both UIs read "Known only by its model, so it may match other units" under its name

#### Scenario: Unlinking read aloud
- **WHEN** a screen reader reads Unlink beside a linked SRAM 1052
- **THEN** both UIs read "Unlink SRAM 1052"

### Requirement: A gear collection's page reads the same in both UIs
A collection page's title, and when its Edit and Delete show and can be used, SHALL be decided by shared code that both UIs ask.

#### Scenario: Being deleted
- **WHEN** a loaded collection is being deleted
- **THEN** both UIs show Edit and Delete, and neither can be used
