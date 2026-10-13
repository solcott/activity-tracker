# Spec Delta

## Purpose

Defines how the AppView identifies itself to PDSes as the managing app for athletes' atproto spaces, so that a PDS can resolve it and call it to decide who may read or write a space.

## ADDED Requirements

### Requirement: Managing-app service in the DID document
The AppView's `did:web` document SHALL list a service with id `#activitytracker_space`, type `AtprotoSpaceService`, and the AppView's public base URL as its endpoint. The managing-app identifier used in space policies SHALL be the AppView's service DID followed by `#activitytracker_space`.

#### Scenario: DID document lists the managing-app service
- **WHEN** a client fetches `/.well-known/did.json` from the AppView
- **THEN** the `service` array contains an entry with id `#activitytracker_space`, type `AtprotoSpaceService`, and `serviceEndpoint` equal to the configured public URL

#### Scenario: Existing AppView service entry is kept
- **WHEN** a client fetches `/.well-known/did.json`
- **THEN** the `#activity_appview` entry of type `ActivityTrackerAppView` is still present and unchanged

#### Scenario: Endpoint follows the configured public URL
- **WHEN** the AppView runs with `APPVIEW_PUBLIC_URL` set to a tunnel URL
- **THEN** the DID document's `id` is that host's `did:web` and the managing-app endpoint is the tunnel URL

### Requirement: Managing app resolvable from the PDS
In local development with the tunnel enabled, the PDS SHALL be able to resolve the AppView's `did:web` and reach its managing-app endpoint over HTTPS.

#### Scenario: PDS resolves the AppView through the tunnel
- **WHEN** the local stack runs with the tunnel and the PDS resolves the AppView's `did:web`
- **THEN** it obtains the DID document with the `#activitytracker_space` service and can send an HTTP request to that endpoint

#### Scenario: Without the tunnel, Spaces flows are unsupported
- **WHEN** the local stack runs with `--no-tunnel`
- **THEN** non-Spaces features keep working, and the documentation states that managing-app callouts need the tunnel
