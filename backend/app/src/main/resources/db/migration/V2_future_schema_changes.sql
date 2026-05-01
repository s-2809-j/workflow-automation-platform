-- Enable RLS after tables are created by Flyway
ALTER TABLE workflow ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON workflow
  USING (tenant_id = current_setting('myapp.current_tenant', true)::integer);