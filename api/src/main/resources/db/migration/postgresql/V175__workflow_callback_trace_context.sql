ALTER TABLE agent_workflow_callback_delivery
    ADD COLUMN IF NOT EXISTS traceparent VARCHAR(255);

COMMENT ON COLUMN agent_workflow_callback_delivery.traceparent IS 'W3C Trace Context captured when the callback delivery was created';
