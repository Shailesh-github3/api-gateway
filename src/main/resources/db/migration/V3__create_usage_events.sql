CREATE TABLE usage_events (
                              id BIGSERIAL PRIMARY KEY,
                              api_key_id BIGINT NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
                              endpoint VARCHAR(255) NOT NULL,
                              http_method VARCHAR(10) NOT NULL,
                              status_code INT NOT NULL,
                              response_time_ms BIGINT NOT NULL,
                              created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Index specifically for the nightly rollup job (Week 3, Day 13)
CREATE INDEX idx_usage_apikey_time ON usage_events(api_key_id, created_at);