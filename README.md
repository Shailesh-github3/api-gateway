# Multi-Tenant API Gateway

A production-inspired API gateway implementing secure API-key authentication, distributed token-bucket rate limiting, and async usage analytics.

## Architecture

```text
Request
  → ApiKeyAuthFilter (401)
  → ScopeValidationFilter (403)
  → RateLimitFilter (429)
  → Controller
  → Async Log
  → PostgreSQL
```

## Prerequisites

* [Docker Desktop](https://www.docker.com/products/docker-desktop/) v24.0+
* Java 21 & Maven 3.9+ (optional, for local development outside Docker)

## Quick Start

Spin up the application, PostgreSQL, and Redis:

```bash
docker compose up --build -d
```

Access the API at:

```text
http://localhost:8080
```

### Quick Verification

Create your first API key:

```bash
curl -u admin:admin -X POST http://localhost:8080/admin/api-keys \
  -H "Content-Type: application/json" \
  -d '{"ownerId":1,"tier":"STARTER","scopes":["read"]}'
```

The API key is returned only once, so store it securely.

## API Endpoints

| Endpoint               | Method | Auth                   | Description                           |
| ---------------------- | ------ | ---------------------- | ------------------------------------- |
| `/admin/api-keys`      | POST   | Basic (`admin:admin`)  | Create API key (returns raw key once) |
| `/admin/api-keys/{id}` | DELETE | Basic (`admin:admin`)  | Revoke API key                        |
| `/admin/api-keys`      | GET    | Basic (`admin:admin`)  | List all keys                         |
| `/v1/example-resource` | GET    | API Key + `scope:read` | Protected demo endpoint               |

## Tech Stack

* **Spring Boot 4.1.0** — Core framework
* **Spring Security** — Filter chain and basic authentication
* **Bucket4j 8.19.0 + Redis** — Distributed rate limiting
* **PostgreSQL 16** — Persistent storage
* **Flyway** — Database schema migrations
* **Testcontainers** — Integration testing
* **k6** — Load testing

## Key Design Decisions

### Security

* **SHA-256 Hashing (Not Encryption):** API keys are never recoverable. If the database is breached, stored hashes cannot be reversed to obtain the original keys.
* **Constant-Time Comparison:** `MessageDigest.isEqual()` is used to reduce timing-attack risks when comparing hashes.
* **Prefix-Indexed Lookup:** A key prefix is used for efficient database lookup before performing hash verification, avoiding full-table scans.

### Rate Limiting

* **Token Bucket:** Token-bucket rate limiting avoids the boundary burst problem associated with fixed-window algorithms.
* **Redis-Backed State:** Rate-limit state is stored in Redis so limits remain consistent across multiple application instances.
* **Per-Tenant Isolation:** Each API key receives its own bucket using a key such as `bucket:{apiKeyId}`, preventing quota sharing between tenants.

### Async Processing

* **`@Async` with `CallerRunsPolicy`:** When the executor queue of 500 tasks is full and all worker threads are busy, the calling HTTP thread executes the logging task. This provides backpressure instead of dropping events or allowing the queue to grow without bounds.

### Cache Invalidation

* **60-Second TTL + Explicit Eviction on Revoke:** Cached API-key data expires after 60 seconds, while explicit eviction on revocation prevents a revoked key from remaining valid in the cache for the full TTL.

## Testing

### Integration Tests

Integration tests use Testcontainers to provision the required infrastructure:

```bash
mvn test
```

### Load Tests

Rate-limit verification:

```bash
k6 run load-tests/rate-limit-breach.js
```

Tenant-isolation verification:

```bash
k6 run load-tests/tenant-isolation.js
```

## Database Schema

See:

```text
src/main/resources/db/migration/
```

for the complete database schema and Flyway migrations.

Entity relationships are documented in:

```text
docs/er-diagram.md
```

## Performance

* **<5 ms target overhead per request** for authentication and rate limiting under typical local conditions, including Redis interaction and hash comparison.
* **Async logging** keeps logging work off the request thread during normal operation.

> Performance figures depend on hardware, network latency, Redis configuration, database load, and deployment topology. Benchmark production environments before relying on specific latency targets.

## Project Structure

```text
.
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   │       └── db/
│   │           └── migration/
│   └── test/
├── docs/
│   └── er-diagram.md
├── load-tests/
│   ├── rate-limit-breach.js
│   └── tenant-isolation.js
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Stopping the Application

```bash
docker compose down
```

To also remove persistent Docker volumes:

```bash
docker compose down -v
```

> The `-v` option removes PostgreSQL and Redis volumes and therefore deletes persisted local data.

## License

MIT
