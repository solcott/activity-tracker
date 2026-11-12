# Spec Delta

## MODIFIED Requirements

### Requirement: A natively picked photo reaches its presenter as shared code prepares it
A photo the iOS app picks natively SHALL be compressed on iOS without Skia or Compose, with the steps, qualities and size target shared code uses for the shared UI's pick, as a WebP that is upright by the photo's orientation and carries none of its metadata, and the result SHALL reach the screen's presenter through the event the shared UI sends, with no contract change for iOS.

#### Scenario: A large photo
- **WHEN** Swift compresses a 4,032 × 3,024 photo
- **THEN** the presenter receives a WebP photo 2,048 × 1,536 pixels and under 1.5 MB, with its width and height

#### Scenario: A photo taken on its side
- **WHEN** the photo's pixels are stored 400 × 300 with an EXIF orientation saying it was held on its side
- **THEN** the WebP is 300 × 400 and shows the photo upright

#### Scenario: Metadata
- **WHEN** the photo carries EXIF data with a GPS position
- **THEN** the WebP holds only its pixels, with no EXIF, XMP or colour profile

#### Scenario: A HEIC
- **WHEN** the photo picked is a HEIC
- **THEN** it is read directly and the presenter receives a WebP, as for any other photo

#### Scenario: Nothing fits
- **WHEN** every step but the last comes out over 1.5 MB
- **THEN** the last step's photo is sent, whatever its size

#### Scenario: Not a picture
- **WHEN** the bytes picked can't be read as a picture
- **THEN** nothing reaches the presenter and the form is as it was
