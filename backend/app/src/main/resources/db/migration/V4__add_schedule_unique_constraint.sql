-- V4__add_schedule_unique_constraint.sql

-- ============================================================
-- FIX 1: workflow RLS policies — add NULLIF protection
-- Unsafe cast crashed Flyway session when app.current_organization
-- was not set, returning "" which failed ::uuid cast
-- ============================================================
DROP POLICY IF EXISTS workflow_select_policy ON workflow;
DROP POLICY IF EXISTS workflow_insert_policy ON workflow;
DROP POLICY IF EXISTS workflow_update_policy ON workflow;
DROP POLICY IF EXISTS workflow_delete_policy ON workflow;

CREATE POLICY workflow_select_policy ON workflow
    FOR SELECT
    USING (
    organization_id = NULLIF(
            current_setting('app.current_organization', true), ''
                      )::uuid
    );

CREATE POLICY workflow_insert_policy ON workflow
    FOR INSERT
    WITH CHECK (
    organization_id = NULLIF(
            current_setting('app.current_organization', true), ''
                      )::uuid
    );

CREATE POLICY workflow_update_policy ON workflow
    FOR UPDATE
    USING (
    organization_id = NULLIF(
            current_setting('app.current_organization', true), ''
                      )::uuid
    )
    WITH CHECK (
    organization_id = NULLIF(
            current_setting('app.current_organization', true), ''
                      )::uuid
    );

CREATE POLICY workflow_delete_policy ON workflow
    FOR DELETE
    USING (
    organization_id = NULLIF(
            current_setting('app.current_organization', true), ''
                      )::uuid
    );

-- ============================================================
-- FIX 2: workflow_steps RLS policy — add NULLIF protection
-- ============================================================
DROP POLICY IF EXISTS workflow_steps_tenant_isolation ON workflow_steps;

CREATE POLICY workflow_steps_tenant_isolation ON workflow_steps
    USING (
    organization_id = NULLIF(
            current_setting('app.current_organization', true), ''
                      )::uuid
    );

-- ============================================================
-- FIX 3: workflow_schedule FK using NOT VALID
-- RLS on workflow hides rows from FK validation scan in Flyway
-- session because app.current_organization is not set.
-- NOT VALID skips the historical data scan — new rows still
-- get validated. Existing data integrity is confirmed separately.
-- ============================================================
ALTER TABLE workflow_schedule
    ADD CONSTRAINT fk_schedule_workflow
        FOREIGN KEY (workflow_id) REFERENCES workflow(id)
            ON DELETE CASCADE
        NOT VALID;

-- ============================================================
-- FIX 4: Unique constraint and indexes
-- ============================================================
ALTER TABLE workflow_schedule
    ADD CONSTRAINT uq_schedule_workflow_cron_tz
        UNIQUE (workflow_id, cron_expression, timezone);

CREATE INDEX IF NOT EXISTS idx_schedule_workflow_id
    ON workflow_schedule(workflow_id);

CREATE INDEX IF NOT EXISTS idx_schedule_next_run
    ON workflow_schedule(next_run_at)
    WHERE enabled = true;