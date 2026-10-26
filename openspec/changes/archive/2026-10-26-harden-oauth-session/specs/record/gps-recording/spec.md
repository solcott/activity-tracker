# Spec Delta

## MODIFIED Requirements

### Requirement: Signing out keeps the recording for its account
Signing out during a recording SHALL stop recording and keep the recording on the device as unfinished, to be offered back when the same account signs in again. A session that ends because it expired SHALL NOT stop the recording: it keeps recording, and signing in again to the same account returns to it as it is. When a different account signs in while another account's recording is running, that recording SHALL stop and be kept for its own account, as if its account had signed out.

#### Scenario: Sign out and back in
- **WHEN** an athlete signs out 20 minutes into a run and signs in to the same account later
- **THEN** the app offers to resume, save or discard the 20-minute run

#### Scenario: Session expires during a run
- **WHEN** an athlete's session expires 20 minutes into a run, and they sign in to the same account 5 minutes later
- **THEN** the run kept recording throughout, and the record screen shows it still running with all 25 minutes

#### Scenario: Another account signs in after an expiry
- **WHEN** alice's session expires during a run, and bob signs in on the same device
- **THEN** alice's run stops and is kept for her, bob's record screen shows no recording, and alice is offered her run when she signs in again
