CREATE TABLE usage_daily_rollup (
                                    id BIGSERIAL PRIMARY KEY,
                                    api_key_id BIGINT NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
                                    day DATE NOT NULL,
                                    request_count BIGINT NOT NULL,
                                    error_count BIGINT NOT NULL,
                                    avg_latency_ms BIGINT NOT NULL,
                                    UNIQUE(api_key_id, day)
);