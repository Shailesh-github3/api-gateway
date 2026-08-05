# API Gateway Architecture & Request Flow

## 1. High-Level Architecture Diagram

```mermaid
graph TD
    Client[API Client] -->|HTTP Request| Gateway[Spring Boot Gateway]

    subgraph "Gateway Core Filters"
        ApiKeyFilter[ApiKeyAuthFilter]
        ScopeFilter[ScopeValidationFilter]
        RateFilter[RateLimitFilter]
    end

    Gateway --> ApiKeyFilter
    ApiKeyFilter --> ScopeFilter
    ScopeFilter --> RateFilter

    RateFilter -->|1. Check Bucket & Cache| Redis[(Redis RAM)]
    ApiKeyFilter -->|2. Cache Miss Fallback| Postgres[(PostgreSQL DB)]

    RateFilter -->|3. Forward Valid Request| Controller[Demo Resource Controller]
    Controller -->|4. Fire & Forget Log| AsyncLogger["@Async Usage Logger"]
    AsyncLogger -->|5. Write Event| Postgres
```

---

## 2. Request Flow Sequence Diagram

```mermaid
sequenceDiagram
    autonumber

    actor Client
    participant Auth as ApiKeyAuthFilter
    participant Rate as RateLimitFilter
    participant Redis as Redis (Bucket4j)
    participant App as Controller / API
    participant Async as @Async Logger
    participant DB as PostgreSQL

    Client->>Auth: HTTP GET /v1/example\nHeader: X-API-KEY

    Note over Auth: 1. Extract key prefix\n2. Lookup by prefix\n3. Verify SHA-256 using constant-time comparison

    alt API key missing or invalid
        Auth-->>Client: 401 Unauthorized
    else API key valid
        Auth->>Rate: Continue filter chain

        Rate->>Redis: Execute Bucket4j Lua Script\nbucket:apiKeyId

        alt Tokens available
            Redis-->>Rate: Consume token
            Rate->>App: Forward request
            App-->>Client: 200 OK + RateLimit headers

            App-)Async: Fire-and-forget usage event
            Async->>DB: INSERT INTO usage_events

        else Rate limit exceeded
            Redis-->>Rate: No tokens remaining
            Rate-->>Client: 429 Too Many Requests\nRetry-After
        end
    end
```

---

## 3. Database Entity Relationship Diagram (ERD)

```mermaid
erDiagram
    rate_limit_tiers ||--o{ api_keys : defines
    api_keys ||--o{ usage_events : tracks
    api_keys ||--o{ usage_daily_rollup : aggregates

    api_keys {
        bigint id PK
        bigint owner_id
        varchar key_prefix
        varchar key_hash
        varchar tier FK
        string scopes
        boolean revoked
        timestamp created_at
    }

    rate_limit_tiers {
        varchar tier PK
        int requests_per_minute
        int burst_capacity
    }

    usage_events {
        bigint id PK
        bigint api_key_id FK
        varchar endpoint
        varchar http_method
        int status_code
        bigint response_time_ms
        timestamp created_at
    }

    usage_daily_rollup {
        bigint id PK
        bigint api_key_id FK
        date day
        bigint request_count
        bigint error_count
        bigint avg_latency_ms
    }
```

---

## Database Notes

### `api_keys`

| Column | Description |
|---------|-------------|
| `id` | Primary key |
| `owner_id` | User who owns the API key |
| `key_prefix` | Short searchable prefix used for fast lookup (indexed) |
| `key_hash` | SHA-256 hash of the API key |
| `tier` | Foreign key to `rate_limit_tiers` |
| `scopes` | Allowed permissions (e.g. `read`, `write`) |
| `revoked` | Whether the key is disabled |
| `created_at` | Creation timestamp |

### `rate_limit_tiers`

| Column | Description |
|---------|-------------|
| `tier` | Tier name (`STARTER`, `PRO`, `ENTERPRISE`) |
| `requests_per_minute` | Sustained rate limit |
| `burst_capacity` | Maximum bucket capacity |

### `usage_events`

Stores every API request for analytics and auditing.

| Column | Description |
|---------|-------------|
| `api_key_id` | API key used |
| `endpoint` | Requested endpoint |
| `http_method` | GET, POST, etc. |
| `status_code` | HTTP response code |
| `response_time_ms` | Processing latency |
| `created_at` | Event timestamp |

### `usage_daily_rollup`

Stores aggregated daily usage statistics.

| Column | Description |
|---------|-------------|
| `api_key_id` | API key |
| `day` | Aggregation date |
| `request_count` | Total requests |
| `error_count` | Number of failed requests |
| `avg_latency_ms` | Average response time |
