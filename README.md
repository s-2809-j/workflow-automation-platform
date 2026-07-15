# Workflow Automation Platform

[![CI](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](./LICENSE)
[![Backend](https://img.shields.io/badge/backend-Spring%20Boot%203.2.2-6DB33F)](#)
[![Frontend](https://img.shields.io/badge/frontend-React%2019-61DAFB)](#)
[![Node](https://img.shields.io/badge/AI%20Service-Node.js%2018%2B-339933)](#)
[![Coverage](https://img.shields.io/badge/coverage-report-lightgrey)](#)
[![Docker](https://img.shields.io/badge/container-Docker%20Compose-2496ED)](#)

A production-grade, multi-tenant workflow automation platform for designing, executing, scheduling, and monitoring automated business processes — with an integrated AI assistant (Google Gemini) for workflow drafting and execution log analysis.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Key Features](#key-features)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Environment Variables](#environment-variables)
  - [Local Development](#local-development)
  - [Docker Deployment](#docker-deployment)
- [API Reference](#api-reference)
- [Database Schema](#database-schema)
- [Security](#security)
- [Testing](#testing)
- [Observability & Monitoring](#observability--monitoring)
- [CI/CD Pipeline](#cicd-pipeline)
- [Performance & Scaling](#performance--scaling)
- [Versioning & Releases](#versioning--releases)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [Support](#support)
- [Contributors](#contributors)
- [License](#license)

---

## Overview

The Workflow Automation Platform is a **three-service, multi-tenant SaaS system** that enables engineering and operations teams to:

- **Design** automation workflows composed of sequential or parallel steps (HTTP, EMAIL, CONDITION) with configurable inputs and dependency ordering.
- **Execute** workflows on demand or on a schedule, with automatic retries using fixed or exponential back-off strategies.
- **Monitor** execution history, per-step logs, and aggregate analytics across all workflow runs.
- **Draft workflows using AI** — describe a process in plain language and receive a structured, reviewable workflow draft powered by Google Gemini.

The system is built with a **security-first, tenant-isolated architecture** using PostgreSQL Row-Level Security, stateless JWT authentication, and strict service boundaries between the API, frontend, and AI microservice.

> **Status:** Actively maintained · **Current stable version:** `v1.4.0` · See [CHANGELOG.md](./CHANGELOG.md) for release history.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                            Client Layer                          │
│                     React 19 SPA — Port 3000                     │
└───────────────────────────────┬──────────────────────────────────┘
                                 │ HTTPS / REST / JWT
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Application Layer                            │
│              Spring Boot 3.2.2 (JDK 21) — Port 8080               │
│  ┌────────────┐ ┌────────────────┐ ┌───────────────────────────┐ │
│  │ Auth (JWT) │ │ Workflow CRUD  │ │ DAG Execution Engine        │ │
│  └────────────┘ └────────────────┘ └───────────────────────────┘ │
│  ┌────────────┐ ┌────────────────┐ ┌───────────────────────────┐ │
│  │ Cron        │ │ Retry           │ │ Multi-Tenant Isolation     │ │
│  │ Scheduler   │ │ Orchestration   │ │ (Row-Level Security)       │ │
│  └────────────┘ └────────────────┘ └───────────────────────────┘ │
└──────────────┬───────────────────────────────┬────────────────────┘
               │ JDBC                          │ REST (internal)
               ▼                               ▼
   ┌─────────────────────┐        ┌─────────────────────────────┐
   │ PostgreSQL 15         │        │  AI Microservice (Node.js)  │
   │ Flyway-managed schema │        │  Express 5 — Port 3001       │
   │ Row-Level Security    │        │  Google Gemini Integration   │
   └─────────────────────┘        │  MongoDB (drafts / logs)     │
                                    └─────────────────────────────┘
```

| Service | Runtime | Port | Datastore | Responsibility |
|---|---|---|---|---|
| **Frontend** | React 19 | 3000 | — | UI, auth session handling |
| **Backend** | Spring Boot / JDK 21 | 8080 | PostgreSQL 15 | Core domain logic, execution engine, scheduling |
| **AI Service** | Node.js (ESM) | 3001 | MongoDB | LLM drafting, log analysis |

All inter-service traffic occurs over authenticated REST calls; no service shares a database with another.

---

## Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 21 (LTS) | Runtime |
| Spring Boot | 3.2.2 | Application framework |
| Spring Security + JJWT | 0.12.6 | Stateless JWT authentication |
| Spring Data JPA | — | ORM / persistence layer |
| PostgreSQL | 15 | Primary datastore with RLS |
| Flyway | — | Versioned, repeatable schema migrations |
| GraalVM JS | 23.0.1 | Sandboxed in-process JS execution for workflow steps |
| Lombok | — | Boilerplate reduction |
| Gradle | 8.5 | Build automation |

### Frontend
| Technology | Version | Purpose |
|---|---|---|
| React | 19 | UI framework |
| React Router DOM | 7 | Client-side routing |
| Axios | 1.x | HTTP client |

### AI Service
| Technology | Version | Purpose |
|---|---|---|
| Node.js (ESM) | 18+ | Runtime |
| Express | 5 | HTTP framework |
| @google/generative-ai | 0.24.x | Gemini LLM integration |
| Mongoose | 9.x | MongoDB ODM |
| Zod | 3.x | Schema/request validation |
| Helmet | — | Secure HTTP headers |
| express-rate-limit | — | Per-endpoint rate limiting |

### Infrastructure
| Technology | Purpose |
|---|---|
| Docker + Docker Compose | Containerization & local orchestration |
| PostgreSQL 15 | Backend persistence |
| MongoDB | AI drafts and execution log storage |
| GitHub Actions | CI/CD automation |

---

## Key Features

- **Workflow Management** — Create, update, delete, and list workflows scoped per organization.
- **Step Composition** — Build workflows from typed steps (HTTP, EMAIL, CONDITION) with configurable inputs and explicit ordering.
- **DAG Execution Engine** — Resolves step dependencies via topological sort and executes in correct order.
- **Cron Scheduler** — Timezone-aware, cron-expression-based workflow triggering.
- **Retry Mechanism** — Fixed-interval and exponential back-off strategies with per-step attempt tracking.
- **AI Workflow Drafting** — Natural-language workflow generation via Google Gemini, reviewed before promotion to production.
- **Approve / Reject Drafts** — Human-in-the-loop review gate for all AI-generated workflows.
- **Execution Log Analysis** — AI-assisted anomaly detection and retry recommendations.
- **Multi-Tenant Isolation** — Enforced at the database layer via PostgreSQL Row-Level Security, not just application logic.
- **JWT Authentication** — Stateless, protected routes on both backend and frontend.
- **Analytics Dashboard** — Aggregate success/failure metrics across all workflow runs.

---

## Project Structure

```
workflow-automation-platform/
│
├── backend/                            # Spring Boot application
│   ├── app/
│   │   └── src/main/java/com/company/workflowautomation/
│   │       ├── auth/                   # JWT auth, Spring Security config
│   │       ├── workflow/               # Workflow domain, API, JPA
│   │       ├── workflow_steps/         # Step domain, API, JPA
│   │       ├── workflow_execution/     # DAG engine, scheduler, retry
│   │       ├── ai/                     # AI adapter & retry orchestration
│   │       ├── shared/                 # Tenant filter, CORS, health
│   │       └── config/                 # Global configuration beans
│   ├── src/test/                       # Unit & integration tests
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── init-scripts/                   # PostgreSQL initialization SQL
│
├── frontend/                           # React SPA
│   ├── src/
│   │   ├── components/                 # Workflows, Executions, AIDrafts, Logs, Analytics
│   │   ├── services/                   # Axios API client
│   │   └── styles/
│   ├── src/__tests__/                  # Component/unit tests
│   └── package.json
│
├── ai-service/                         # Node.js AI microservice
│   ├── src/
│   │   ├── controllers/                # Request handlers
│   │   ├── routes/                     # Express routers
│   │   ├── services/                   # Gemini integration & business logic
│   │   ├── models/                     # Mongoose schemas
│   │   ├── middlewares/                # Auth, error handling
│   │   ├── schemas/                    # Zod validation schemas
│   │   └── config/
│   ├── test/
│   └── package.json
│
├── .github/
│   ├── workflows/                      # CI/CD pipeline definitions
│   ├── ISSUE_TEMPLATE/
│   └── PULL_REQUEST_TEMPLATE.md
│
├── docs/                                # Architecture decision records, diagrams
├── .env.example
├── CHANGELOG.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
├── CONTRIBUTING.md
└── LICENSE
```

---

## Getting Started

### Prerequisites

| Tool | Version | Required For |
|---|---|---|
| Docker | 24+ | Full-stack local deployment |
| Docker Compose | v2+ | Multi-service orchestration |
| JDK | 21 | Local backend development |
| Node.js | 18+ | Local frontend / AI service development |
| Gradle | 8.5 (wrapper included) | Backend builds |

### Environment Variables

Copy the root template and populate required values before running any service:

```bash
cp .env.example .env
```

| Variable | Service | Description | Required |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | Backend | PostgreSQL JDBC connection URL | Yes |
| `SPRING_DATASOURCE_USERNAME` | Backend | PostgreSQL username | Yes |
| `SPRING_DATASOURCE_PASSWORD` | Backend | PostgreSQL password | Yes |
| `JWT_SECRET` | Backend | Secret key for signing JWTs (min. 256-bit) | Yes |
| `AI_SERVICE_URL` | Backend | Base URL of the AI microservice | Yes |
| `MOCK_MODE` | Backend / AI Service | `true` to bypass live Gemini calls in development | No |
| `PORT` | AI Service | Listening port (default `3001`) | No |
| `GEMINI_API_KEY` | AI Service | Google Gemini API key | Yes |
| `GEMINI_MODEL` | AI Service | Model identifier (e.g. `gemini-2.5-flash`) | Yes |
| `MONGODB_URI` | AI Service | MongoDB connection string | Yes |
| `AI_TIMEOUT_MS` | AI Service | Request timeout for Gemini calls | No |
| `MAX_TEXT_LEN` | AI Service | Max input length accepted for drafting | No |
| `LOG_RAW_AI` | AI Service | Enable verbose AI response logging (dev only) | No |

> **Security note:** Never commit `.env` files. Rotate `JWT_SECRET` and `GEMINI_API_KEY` on any suspected exposure. Use a secrets manager (AWS Secrets Manager, HashiCorp Vault, or GitHub Actions Encrypted Secrets) in staging/production.

### Local Development

**Backend**
```bash
cd backend
./gradlew :app:bootRun
```
Flyway applies all pending migrations automatically on startup. API available at `http://localhost:8080`.

**Frontend**
```bash
cd frontend
npm install
npm start
```
Available at `http://localhost:3000`.

**AI Service**
```bash
cd ai-service
cp .env.example .env      # populate GEMINI_API_KEY and MONGODB_URI
npm install
npm run dev                # hot-reload via nodemon
```
Available at `http://localhost:3001`.

### Docker Deployment

```bash
cd backend
docker compose up --build -d
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| PostgreSQL | localhost:5432 |

The AI service can be started independently (see above) or added as an additional service in `docker-compose.yml` for a single-command full-stack deployment.

```bash
# Tear down
docker compose down -v
```

For staging/production, deploy each service as an independent container behind a reverse proxy (e.g. NGINX or an API gateway) with TLS termination, and point `SPRING_DATASOURCE_URL` / `MONGODB_URI` at managed database instances.

---

## API Reference

All backend endpoints require `Authorization: Bearer <token>` except `/api/auth/**`. Interactive OpenAPI documentation is available at `http://localhost:8080/swagger-ui.html` when the backend is running with the `dev` profile.

**Authentication**

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/login` | Authenticate and receive a JWT |

**Workflows**

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/workflows` | List all workflows for the current tenant |
| POST | `/api/workflows` | Create a new workflow |
| GET | `/api/workflows/{id}` | Get workflow details |
| PUT | `/api/workflows/{id}` | Update a workflow |
| DELETE | `/api/workflows/{id}` | Delete a workflow |

**Workflow Steps**

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/workflows/{id}/steps` | List steps for a workflow |
| POST | `/api/workflows/{id}/steps` | Add a step to a workflow |
| PUT | `/api/workflows/{id}/steps/{stepId}` | Update a step |
| DELETE | `/api/workflows/{id}/steps/{stepId}` | Delete a step |

**Execution**

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/workflows/{id}/execute` | Trigger a workflow run |
| GET | `/api/executions` | List all workflow runs |
| GET | `/api/executions/{runId}/logs` | Fetch per-step execution logs |

**AI**

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/ai/generate-workflow` | Generate a workflow draft from a plain-text prompt |
| POST | `/api/ai/analyze` | Analyze execution logs for anomalies |
| POST | `/api/ai/drafts/{draftId}/approve` | Approve a draft → promotes to a live workflow |
| POST | `/api/ai/drafts/{draftId}/reject` | Reject a draft |

**System**

| Method | Endpoint | Description |
|---|---|---|
| GET | `/actuator/health` | Service liveness/readiness probe |

---

## Database Schema

The PostgreSQL schema consists of 11 tables, with **Row-Level Security enforced on every tenant-scoped table** via the `app.current_organization` session variable:

```
organizations        → Root tenant entity
users                → Scoped to organization (RLS)
roles                → Organization-scoped roles
user_roles           → User-role junction table
workflow             → Workflow definitions (RLS)
workflow_steps       → DAG step definitions with depends_on JSONB
workflow_execution   → Per-execution records with trigger_data JSONB
step_execution        → Per-step results with input/output JSONB, attempt_count
workflow_run         → Coarse-grained run status: PENDING/RUNNING/RETRYING/SUCCESS/FAILED
workflow_schedule    → Cron-based scheduling with timezone and next_run_at
workflow_draft       → AI-generated drafts: PENDING/APPROVED/REJECTED
```

Schema changes are managed exclusively through **Flyway migrations** (`backend/app/src/main/resources/db/migration`). Direct DDL changes against staging/production databases are prohibited.

---

## Security

- **Authentication**: Stateless JWT (HS256), short-lived access tokens.
- **Authorization**: Role-based access control enforced per organization.
- **Tenant Isolation**: PostgreSQL Row-Level Security — not application-layer filtering — prevents cross-tenant data leakage even in the event of a query bug.
- **Transport**: TLS termination required at the reverse proxy/gateway layer in all non-local environments.
- **Secrets**: No secrets are committed to source control; `.env.example` documents required keys without values.
- **AI Service Hardening**: Helmet for secure headers, Zod schema validation on all inbound requests, and per-endpoint rate limiting via `express-rate-limit`.
- **Dependency Scanning**: Automated vulnerability scanning runs as part of the CI pipeline (see below).

To report a security vulnerability, please follow the process outlined in [`SECURITY.md`](./SECURITY.md) rather than filing a public issue.

---

## Testing

```bash
# Backend
cd backend
./gradlew test
./gradlew jacocoTestReport      # coverage report

# Frontend
cd frontend
npm test
npm run test:coverage

# AI Service
cd ai-service
npm test
npm run test:coverage
```

| Layer | Framework | Scope |
|---|---|---|
| Backend | JUnit 5 + Mockito | Unit, integration (Testcontainers for PostgreSQL) |
| Frontend | Jest + React Testing Library | Component and integration tests |
| AI Service | Jest + Supertest | Route, service, and contract tests |

Minimum coverage threshold enforced in CI: **80% line coverage** on backend and AI service modules.

---

## Observability & Monitoring

- **Health checks**: `/actuator/health` (backend), equivalent liveness endpoint on the AI service.
- **Structured logging**: JSON-formatted logs across all services for ingestion into centralized log pipelines (e.g. ELK, Loki, CloudWatch).
- **Metrics**: Spring Boot Actuator exposes Micrometer-compatible metrics for Prometheus scraping.
- **Execution analytics**: Aggregate success/failure/retry metrics surfaced via the Analytics Dashboard.

---

## CI/CD Pipeline

GitHub Actions workflows run on every pull request and merge to `develop`/`main`:

1. **Lint** — static analysis across backend (Checkstyle/Spotless), frontend (ESLint), and AI service (ESLint).
2. **Test** — full unit and integration suites for all three services.
3. **Build** — Gradle build for backend, production bundle for frontend, container images for all services.
4. **Security Scan** — dependency vulnerability scanning (e.g. `npm audit`, OWASP Dependency-Check).
5. **Deploy** *(main branch only)* — publishes container images and triggers deployment to the target environment.

---

## Performance & Scaling

- Backend services are stateless and horizontally scalable behind a load balancer.
- The DAG execution engine processes steps asynchronously; long-running workflows do not block the request thread.
- PostgreSQL connection pooling is configured via HikariCP; tune `spring.datasource.hikari.*` for production load.
- The AI service is rate-limited per endpoint to protect against Gemini API quota exhaustion and abusive traffic.

---

## Versioning & Releases

This project follows [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`). All notable changes are documented in [`CHANGELOG.md`](./CHANGELOG.md). Release tags are cut from `main` and published as GitHub Releases with accompanying container image tags.

---

## Roadmap

- [ ] Webhook-based step type
- [ ] Role-based UI permission gating
- [ ] Workflow versioning and rollback
- [ ] SSO / OIDC integration
- [ ] Horizontal execution worker pool

See open [issues](../../issues) and the [project board](../../projects) for current priorities.

---

## Contributing

Contributions are welcome. Please read [`CONTRIBUTING.md`](./CONTRIBUTING.md) before submitting a pull request.

**Summary workflow:**

```bash
git checkout develop
git checkout -b feat/your-feature-name
```

Commit using [Conventional Commits](https://www.conventionalcommits.org/):

```
feat:     new feature
fix:      bug fix
chore:    config or tooling change
refactor: code restructure without behavior change
docs:     documentation update
test:     adding or updating tests
```

Before opening a PR, ensure all services build and pass tests:

```bash
./gradlew build        # backend
npm run build           # frontend & ai-service
```

Push your branch and open a pull request targeting `develop`. All PRs require at least one approving review and a passing CI run before merge. Please also review our [Code of Conduct](./CODE_OF_CONDUCT.md).

---

## Support

- **Bug reports & feature requests**: [GitHub Issues](../../issues)
- **Security issues**: see [`SECURITY.md`](./SECURITY.md) — do not file publicly
- **Discussions**: [GitHub Discussions](../../discussions)

---

## Contributors

| Name | Role |
|---|---|
| Sarvesh Joshi | Frontend (React) · Backend (Spring Boot) |
| Omkar Gaikwad | AI Service (Node.js · Express · Google Gemini · MongoDB) |

Interested in contributing? See [Contributing](#contributing) above.

---

## License

This project is licensed under the **MIT License** — see [`LICENSE`](./LICENSE) for full terms.

© 2026 Sarvesh Joshi
