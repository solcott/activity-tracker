## ADDED Requirements

### Requirement: The authorization response's issuer is checked
When an OAuth redirect returns, the client SHALL refuse it if it carries an `iss` other than the issuer sign-in started with. It SHALL also refuse a redirect with no `iss` when that authorization server's metadata advertised `authorization_response_iss_parameter_supported: true`. A server that didn't advertise it MAY omit `iss`.

#### Scenario: Matching issuer
- **WHEN** the redirect's `iss` equals the issuer sign-in started with
- **THEN** the client exchanges the code and signs in

#### Scenario: Other issuer
- **WHEN** the redirect's `iss` names a different authorization server
- **THEN** sign-in fails without exchanging the code

#### Scenario: Missing issuer from a server that advertises it
- **WHEN** the authorization server advertised `authorization_response_iss_parameter_supported: true` and the redirect has no `iss`
- **THEN** sign-in fails without exchanging the code

#### Scenario: Missing issuer from a server that doesn't advertise it
- **WHEN** the authorization server's metadata doesn't advertise the parameter and the redirect has no `iss`
- **THEN** the client exchanges the code and signs in

#### Scenario: Sign-in started by an older build
- **WHEN** a sign-in started before the client recorded whether the server advertises `iss` returns without one
- **THEN** the client treats it as not advertised and exchanges the code
