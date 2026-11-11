# Spec Delta

## ADDED Requirements

### Requirement: A natively picked photo reaches its presenter as shared code prepares it
A photo the iOS app picks natively SHALL be handed to shared code, which SHALL re-encode, scale and strip it exactly as it does for the shared UI's pick, and the result SHALL reach the screen's presenter through the event the shared UI sends, with no contract change for iOS.

#### Scenario: A large photo
- **WHEN** Swift hands shared code a 4,032 × 3,024 photo
- **THEN** the presenter receives a WebP or JPEG photo no more than 2,048 pixels on its long edge and under 1.5 MB, with its width and height

#### Scenario: Not a picture
- **WHEN** the bytes picked can't be read as a picture
- **THEN** nothing reaches the presenter and the form is as it was

### Requirement: A calendar date crosses between Swift and Kotlin unchanged
A calendar date a contract holds SHALL reach a native date control as the same day, and a day chosen there SHALL reach the presenter as that day, in any time zone the device is in.

#### Scenario: Acquired on the first of July
- **WHEN** an item was acquired on 1 July 2026 and the device is in a time zone west of UTC
- **THEN** the native calendar shows 1 July 2026, and choosing 2 July sends 2 July 2026
