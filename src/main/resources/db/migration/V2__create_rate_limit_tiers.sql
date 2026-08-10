CREATE TABLE rate_limit_tiers (
        tier VARCHAR(30) PRIMARY KEY,
        requests_per_minute INT NOT NULL,
        burst_capacity INT NOT NULL
);

INSERT INTO rate_limit_tiers (tier, requests_per_minute, burst_capacity)
VALUES
        ('STARTER', 60, 10),
        ('PRO', 600, 100),
        ('ENTERPRISE', 6000, 1000);