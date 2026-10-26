-- identity/handle-verification: when an account's handle is next checked both ways, in epoch milliseconds. Null means due now, so every
-- account indexed before handles were verified is checked soon after the deploy.
ALTER TABLE actors ADD COLUMN handle_check_due_at BIGINT;
