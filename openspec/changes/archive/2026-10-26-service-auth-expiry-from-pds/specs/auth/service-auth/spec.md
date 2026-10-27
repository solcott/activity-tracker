# Spec Delta

## ADDED Requirements

### Requirement: Clients time service-auth tokens by the PDS's clock
A client SHALL ask the athlete's PDS for a service-auth token without naming an expiry, so the PDS sets it. The client SHALL reuse a token only for its lifetime as the token states it (its expiry minus its issue time, both set by the PDS), counted from when the token arrived and ending a few seconds early. The device's clock SHALL NOT make a token look expired or valid in any other way. A token whose times the client can't read SHALL be sent for the one call it was minted for and not reused.

#### Scenario: Device clock running slow
- **WHEN** the device's clock is two minutes behind the PDS's and the athlete opens their feed
- **THEN** the PDS mints a token and the AppView call succeeds

#### Scenario: Device clock running fast
- **WHEN** the device's clock is two minutes ahead of the PDS's and the athlete makes two AppView calls to the same method ten seconds apart
- **THEN** the second call reuses the first call's token

#### Scenario: Token lifetime used up
- **WHEN** a token's stated lifetime is 60 seconds and the athlete calls the same method again 55 seconds after it arrived
- **THEN** the client asks the PDS for a new token

#### Scenario: Token whose times can't be read
- **WHEN** the PDS returns a token the client can't decode
- **THEN** the client sends it for that call and asks for a new token on the next call
