-- The application is now single-tenant and does not persist enterprise identity
-- or standalone observability records. Keep this as a forward-only migration so
-- existing installations converge without editing previously applied migrations.
DROP TABLE IF EXISTS sys_oidc_identity_binding CASCADE;
DROP TABLE IF EXISTS aether_project CASCADE;
DROP TABLE IF EXISTS aether_workspace CASCADE;
DROP TABLE IF EXISTS aether_tenant CASCADE;
DROP TABLE IF EXISTS agent_run_context_metric CASCADE;
DROP TABLE IF EXISTS knowledge_retrieval_log CASCADE;
