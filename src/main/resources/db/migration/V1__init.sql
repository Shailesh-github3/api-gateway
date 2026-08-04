CREATE TABLE api_keys (
                          id BIGSERIAL PRIMARY KEY,
                          owner_id BIGINT NOT NULL,
                          key_prefix VARCHAR(12) NOT NULL,
                          key_hash VARCHAR(64) NOT NULL,
                          tier VARCHAR(30) NOT NULL DEFAULT 'STARTER',
                          scopes TEXT[] NOT NULL,
                          revoked BOOLEAN NOT NULL DEFAULT FALSE,
                          created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_apikeys_prefix ON api_keys(key_prefix);
CREATE INDEX idx_apikeys_owner ON api_keys(owner_id);