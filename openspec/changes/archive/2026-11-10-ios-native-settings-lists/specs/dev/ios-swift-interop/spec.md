# Spec Delta

## ADDED Requirements

### Requirement: A paged list reaches Swift as its own types
Swift SHALL read a state's paged list's items as objects of the list's own item type, in order, and its loading statuses as their Swift types, without the contract changing its paged state for Swift's sake. A set of account ids in a state SHALL read as Swift's type for an id.

#### Scenario: The blocked accounts
- **WHEN** Swift reads the loaded blocked list's sample
- **THEN** it has two accounts, bob's and carol's, each with its name, handle and id

#### Scenario: Accounts being changed
- **WHEN** Swift reads the accounts whose unblock is being written while bob's is
- **THEN** it has bob's id

### Requirement: A held photo's bytes reach Swift
Swift SHALL be able to ask Kotlin for the bytes of a photo the signed-in session holds by its `heldphoto:` URL, and SHALL get none for another URL or when signed out.

#### Scenario: Signed out
- **WHEN** Swift asks for a held photo while no one is signed in
- **THEN** it gets none
