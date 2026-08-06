# Problem Statement

## 1. Project Title

**SecureGate: A Multi-Tenant API Gateway with Distributed Rate Limiting and Secure API-Key Authentication**

---

## 2. Introduction

### 2.1 Background
Application Programming Interfaces (APIs) have become the primary mechanism through which modern software systems expose functionality to external developers, partner organizations, and internal microservices. As the number of consumers of a given API grows, the API provider must be able to identify each consumer, control what each consumer is allowed to do, and prevent any single consumer from degrading service for others. This class of problem — commonly solved by an **API Gateway** — sits at the boundary between external traffic and protected backend resources.

### 2.2 Domain
The project falls under the domain of **Backend Infrastructure and API Management**, specifically the sub-domains of **Application Security (AuthN/AuthZ)**, **Distributed Systems**, and **Traffic Governance (Rate Limiting)**.

### 2.3 Current Situation
Publicly documented gateway products from companies such as Stripe and GitHub demonstrate a common pattern: every API consumer is issued a unique, hashed API key; every request is authenticated and authorized against that key's scopes; and every consumer is bound to a rate limit tier so that no single tenant can exhaust shared resources. Most academic and early-stage projects, by contrast, either expose endpoints with no access control at all, or bolt on ad-hoc, in-memory rate limiting that does not survive application restarts or scale across multiple instances.

### 2.4 Why This Project Is Needed
There is a clear gap between how real-world, production API platforms manage multi-tenant access and how such access control is typically taught or implemented in student-level projects. A project that faithfully reproduces the **core** design decisions of a production API gateway — secure key hashing, constant-time comparison, scope-based authorization, and distributed token-bucket rate limiting — gives a student direct, defensible experience with system-design concepts that are otherwise only discussed theoretically in coursework or interviews.

---

## 3. Existing System

### 3.1 How the Current Process Works
In the absence of a dedicated gateway layer, individual backend services typically implement access control themselves, if at all. Common approaches include:
- No authentication at all for internal or early-stage APIs.
- Simple hard-coded API keys checked with a plain string comparison.
- Ungoverned request handling with no per-consumer throughput limit.
- Usage tracked manually or not tracked at all, with no analytical visibility into who is calling which endpoint.

### 3.2 Problems in the Existing System
| Problem | Description |
|---|---|
| No centralized authentication | Every service re-implements (or omits) key validation, leading to inconsistent security. |
| Insecure key comparison | Using `String.equals()` for secret comparison is vulnerable to timing attacks. |
| No authorization granularity | Keys are typically all-or-nothing; there is no concept of scoped permissions. |
| No rate limiting | A single misbehaving or malicious consumer can exhaust backend resources for all tenants. |
| No usage visibility | Providers cannot see which tenant is calling which endpoint, at what volume, or with what error rate. |
| Non-distributed state | Where rate limiting exists, it is usually held in local memory and breaks as soon as the service is horizontally scaled. |

### 3.3 Limitations
The existing, ad-hoc approach does not scale beyond a single instance, does not protect against well-known attack classes (timing attacks, key leakage through logs), and provides no operational data for billing, debugging, or capacity planning.

---

## 4. Proposed System

### 4.1 Overview
**SecureGate** is a Spring Boot 3–based API gateway component that sits in front of protected backend resources and centrally enforces authentication, authorization, and per-tenant rate limiting, while asynchronously recording usage data for later analysis.

### 4.2 Working
1. Every request first passes through an **API Key Authentication Filter**, which extracts the key, looks up its hashed record by prefix, and verifies it using a constant-time comparison.
2. A **Scope Validation Filter** checks that the authenticated key carries the permission required for the requested operation.
3. A **Rate Limiting Filter**, backed by Bucket4j and Redis, enforces a token-bucket limit specific to the tenant's subscription tier.
4. Requests that pass all three filters reach the protected controller; the response is returned to the caller together with rate-limit headers.
5. Each request is logged **asynchronously** to PostgreSQL so that logging never blocks the response path.
6. A nightly scheduled job aggregates the previous day's raw usage events into a compact daily rollup table for reporting.

### 4.3 Improvements Over the Existing System
| Existing System | Proposed System |
|---|---|
| Ad-hoc / no authentication | Centralized, hashed, prefix-indexed API-key authentication |
| All-or-nothing access | Scope-based authorization |
| No rate limiting, or local in-memory limiting | Distributed, Redis-backed token-bucket rate limiting shared across instances |
| Synchronous, blocking logging (or none) | Non-blocking asynchronous usage logging |
| No usage insight | Daily rollup aggregation for reporting |
| Manual/insecure key storage | SHA-256 hashed keys, constant-time verification |

### 4.4 Expected Outcome
A working, containerized gateway service that can issue and revoke API keys, enforce per-tenant rate limits under load, demonstrably reject excess traffic with HTTP 429, and provide daily usage summaries — validated through automated integration tests and a load test.

---

## 5. Problem Statement

> *Small and mid-scale API providers frequently lack a centralized, secure, and horizontally-scalable mechanism to authenticate API consumers, authorize their access at a fine-grained (scope) level, and govern their request throughput on a per-tenant basis — resulting in inconsistent security practices, vulnerability to abuse by individual consumers, and no operational visibility into API usage.*

SecureGate addresses this by providing a single, reusable gateway layer that any backend service can sit behind, implementing the authentication, authorization, rate-limiting, and usage-logging concerns once, correctly, and consistently.

---

## 6. Objectives

### 6.1 Main Objective
To design and implement a secure, multi-tenant API gateway that authenticates consumers via hashed API keys, authorizes requests via scopes, and enforces distributed, tier-based rate limiting.

### 6.2 Specific Objectives
1. Implement secure API-key generation, SHA-256 hashing, and constant-time verification.
2. Implement scope-based authorization independent of authentication.
3. Implement distributed rate limiting using the token-bucket algorithm (Bucket4j) backed by Redis, shared correctly across multiple application instances.
4. Implement non-blocking, asynchronous usage-event logging to PostgreSQL.
5. Implement a scheduled daily rollup job that aggregates raw usage events into summarized statistics and prunes stale raw data.
6. Implement a Redis-backed metadata cache for API keys with explicit invalidation on revocation.
7. Validate the system through unit tests, Testcontainers-based integration tests, and a k6 load test that proves rate-limit enforcement under concurrent, multi-tenant traffic.
8. Package the system for reproducible deployment using Docker Compose.

---

## 7. Scope

### 7.1 In Scope
- API key issuance and revocation via an administrator-protected endpoint set.
- API key authentication filter (hash + prefix lookup + constant-time compare).
- Scope-based authorization filter.
- Bucket4j + Redis token-bucket rate limiting, configurable per subscription tier.
- Standard rate-limit response headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After`).
- Asynchronous usage-event logging.
- Scheduled daily rollup and raw-data retention pruning.
- Redis metadata cache with TTL and explicit eviction on key revocation.
- Automated testing: unit, integration (Testcontainers), and load testing (k6).
- Docker Compose–based local deployment (application, PostgreSQL, Redis).
- API documentation (Swagger/OpenAPI), time permitting.

### 7.2 Out of Scope
- Full JWT/OAuth2-based administrator identity system (a simplified HTTP Basic Auth admin role is used instead, by design).
- Quartz-based job scheduling (Spring's built-in `@Scheduled` is used instead — no clustering requirement exists at this scale).
- Message-broker-based logging (e.g., RabbitMQ/Kafka) — asynchronous in-process logging is sufficient at the target scale.
- A front-end analytics dashboard (only the underlying API is built).
- Multi-region Redis, Prometheus/Grafana observability stack, and other enterprise-scale infrastructure.
- Billing/invoicing logic built on top of usage data.

---

## 8. Users

| User Role | Description |
|---|---|
| **System Administrator** | Manages the lifecycle of API keys: issuing new keys with a tenant, tier, and scope set; revoking compromised or unused keys; and viewing per-key usage summaries. Authenticates via HTTP Basic Auth. |
| **API Consumer / Tenant** | An external or internal client holding a valid API key. Calls protected resource endpoints within the limits of the key's assigned scopes and rate-limit tier. Does not interact with the admin endpoints. |
| **Developer/Maintainer (Student)** | Builds, tests, and maintains the gateway itself; not an end-user role but relevant for project evaluation purposes. |

---

## 9. Expected Benefits

### 9.1 User Benefits
- API consumers receive clear, predictable rate-limit feedback (headers and 429 responses) rather than silent failures or unannounced throttling.
- Administrators gain a simple, auditable way to issue and revoke access without redeploying the application.

### 9.2 Technical Benefits
- Centralizing authentication, authorization, and rate limiting in reusable filters keeps downstream controllers free of cross-cutting security logic.
- Redis-backed state allows the design to be validated for horizontal scalability without needing a large production deployment.
- Asynchronous logging keeps the request/response latency of protected endpoints unaffected by persistence overhead.

### 9.3 Academic Value
- The project touches core computer-science and software-engineering topics directly relevant to coursework and technical interviews: cryptographic hashing, timing-attack mitigation, authentication vs. authorization, distributed state, concurrency (thread pools), and scheduled batch processing.
- It produces artifacts (automated tests, load-test evidence, architecture diagrams) that demonstrate engineering rigor beyond a purely functional demo.

---

## 10. Assumptions

Because the source material is written as a portfolio/interview-preparation scoping document rather than a formal academic project brief, the following assumptions have been made to adapt it for college submission:

1. **Team size:** The original material scopes the work for a single developer over 3–4 weeks. For this academic submission, a project team structure (Section 5, Project Plan) is assumed so the documentation satisfies typical team-based college project requirements; the system can equally be built solo.
2. **Timeline:** The original 3–4 week "portfolio sprint" schedule has been expanded to a 14-week academic semester schedule with added slack for review cycles, documentation, and presentation preparation, without altering the technical scope.
3. **Institution-specific details** (college name, course code, guide name, submission date) are not specified in the source material and are left as placeholders for the student to fill in.
4. **Admin authentication** is assumed to use HTTP Basic Auth over HTTPS in production-equivalent deployment, per the explicit design decision in the source material.
5. **Deployment environment** is assumed to be a single-host Docker Compose setup (application + PostgreSQL + Redis), suitable for local demonstration and evaluation rather than production-scale multi-instance deployment.
6. **Swagger/OpenAPI documentation** is treated as a stretch goal, consistent with the source material.

---

## 11. Conclusion

SecureGate addresses a well-recognized and practically important problem in backend engineering — how to securely authenticate, authorize, and rate-limit multi-tenant API traffic — using an intentionally scoped, achievable feature set suitable for a final-year college project. By faithfully implementing the security-critical details (hashed key storage, constant-time comparison, distributed token-bucket rate limiting, and asynchronous, testable usage logging) rather than a simplified imitation, the project offers genuine learning value in distributed systems and application security while remaining realistically completable within an academic timeline.
