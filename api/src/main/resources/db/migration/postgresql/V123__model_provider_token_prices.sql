ALTER TABLE agent_model_provider ADD COLUMN IF NOT EXISTS input_price_per_million_tokens NUMERIC(18,8);
ALTER TABLE agent_model_provider ADD COLUMN IF NOT EXISTS output_price_per_million_tokens NUMERIC(18,8);
