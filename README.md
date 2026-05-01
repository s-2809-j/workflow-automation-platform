# Workflow Automation Platform

A full-stack, AI-augmented workflow automation system that enables organizations to design, execute, schedule, and monitor multi-step workflows — with an AI assistant powered by Google Gemini for instant workflow drafting and execution log analysis.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Environment Variables](#environment-variables)
  - [Running with Docker Compose](#running-with-docker-compose)
  - [Running Services Individually](#running-services-individually)
- [API Reference](#api-reference)
- [Database Schema](#database-schema)
- [Contributing](#contributing)
- [Contributors](#contributors)
- [License](#license)

---

## Overview

The Workflow Automation Platform is a three-service application that allows teams to:

- **Design** automation workflows composed of sequential or parallel steps with configurable types and inputs
- **Execute** workflows on demand or on a schedule, with automatic retries using fixed or exponential back-off strategies
- **Monitor** execution history, per-step logs, and aggregate analytics across all workflow runs
- **Draft** new workflows instantly using an AI assistant powered by Google Gemini — review and approve before going live

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                         Browser                          │
│                 React Frontend  :3000                    │
└────────────────────────┬─────────────────────────────────┘
                         │ REST / JWT
           ┌─────────────▼───────────────┐
           │    Spring Boot Backend      │
           │          :8080              │
           │  - JWT Authentication       │
           │  - Workflow CRUD API        │
           │  - DAG Execution Engine     │
           │  - Cron Scheduler & Retry   │
           │  - Multi-tenant (RLS)       │
           └────────────┬────────────────┘
                        │ REST
          ┌─────────────▼───────────────┐
          │   AI Service (Node.js)      │
          │         :3001               │
          │  - Google Gemini Drafting   │
          │  - Execution Log Analysis   │
          │  - MongoDB Storage          │
          └─────────────────────────────┘
```

| Service | Runtime | Port | Database |
|---|---|---|---|
| Frontend | React 19 | 3000 | — |
| Backend | Spring Boot / JDK 21 | 8080 | PostgreSQL 15 |
| AI Service | Node.js ESM | 3001 | MongoDB |

---

## Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Runtime |
| Spring Boot | 3.2.2 | Web framework |
| Spring Security + JWT | jjwt 0.12.6 | Authentication |
| Spring Data JPA | — | ORM / persistence |
| PostgreSQL | 15 | Primary database with RLS |
| Flyway | — | Versioned database migrations |
| GraalVM JS | 23.0.1 | In-process JS execution for workflow steps |
| Lombok | — | Boilerplate reduction |
| Gradle | 8.5 | Build tool |

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
| @google/generative-ai | 0.24.x | Google Gemini LLM integration |
| Mongoose | 9.x | MongoDB ODM |
| Zod | 3.x | Request validation |
| Helmet | — | Secure HTTP headers |
| express-rate-limit | — | Rate limiting per endpoint |

### Infrastructure
| Technology | Purpose |
|---|---|
| Docker + Docker Compose | Containerisation |
| PostgreSQL 15 | Backend persistence |
| MongoDB | AI draft and execution log storage |

---

## Features

- **Workflow Management** — Create, update, delete, and list automation workflows scoped per organization
- **Step Composition** — Build workflows from typed steps (HTTP, EMAIL, CONDITION) with configurable inputs and ordering
- **DAG Execution Engine** — Resolves step dependencies via topological sort and executes steps in correct order
- **Cron Scheduler** — Timezone-aware, time-based workflow triggering using configurable cron expressions
- **Retry Mechanism** — Fixed-interval and exponential back-off retry strategies with per-step attempt tracking
- **AI Workflow Drafting** — Describe a workflow in plain text; Google Gemini returns a structured draft ready for review
- **Approve / Reject Drafts** — Review AI-generated workflow drafts before promoting them to live workflows
- **Execution Log Analysis** — AI-powered anomaly detection and retry decision recommendations on execution logs
- **Multi-Tenant Architecture** — Organization-scoped data isolation enforced via PostgreSQL Row-Level Security
- **JWT Authentication** — Stateless authentication with protected routes on both backend and frontend
- **Analytics Dashboard** — Aggregate success/failure metrics across all workflow runs

---

## Project Structure

```
workflow-automation-platform/
│
├── backend/                        # Spring Boot application
│   ├── app/
│   │   └── src/main/java/com/company/workflowautomation/
│   │       ├── auth/               # JWT auth, Spring Security config
│   │       ├── workflow/           # Workflow domain, API, JPA
│   │       ├── workflow_steps/     # Step domain, API, JPA
│   │       ├── workflow_execution/ # DAG engine, scheduler, retry
│   │       ├── ai/                 # AI adapter & retry orchestration
│   │       ├── shared/             # Tenant filter, CORS, health
│   │       └── config/             # Global configuration beans
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── init-scripts/               # PostgreSQL initialisation SQL
│
├── frontend/                       # React SPA
│   ├── src/
│   │   ├── components/             # Workflows, Executions, AIDrafts, Logs, Analytics
│   │   ├── services/               # Axios API client
│   │   └── styles/
│   └── package.json
│
├── ai-service/                     # Node.js AI microservice
│   ├── src/
│   │   ├── controllers/            # Request handlers
│   │   ├── routes/                 # Express routers
│   │   ├── services/               # Gemini integration & business logic
│   │   ├── models/                 # Mongoose schemas (ExecutionLog, WorkflowDraft)
│   │   ├── middlewares/            # Auth, error handling
│   │   ├── schemas/                # Zod validation schemas
│   │   ├── config/                 # App configuration
│   │   └── utils/
│   └── package.json
│
├── .env.example                    # Environment variable template
└── LICENSE
```

---

## Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| Docker | 24+ |
| Docker Compose | v2+ |
| Node.js *(local AI service dev)* | 18+ |
| JDK *(local backend dev)* | 21 |

---

### Environment Variables

Copy the root template and fill in the required values:

```bash
cp .env.example .env
```

| Variable | Service | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Backend | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Backend | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | Backend | PostgreSQL password |
| `JWT_SECRET` | Backend | Secret key for signing JWTs |
| `AI_SERVICE_URL` | Backend | Base URL of the AI service |
| `MOCK_MODE` | Backend / AI Service | Set `true` to skip real Gemini calls during development |
| `PORT` | AI Service | Listening port (default `3001`) |
| `GEMINI_API_KEY` | AI Service | Google Gemini API key |
| `GEMINI_MODEL` | AI Service | Model name (e.g. `gemini-2.5-flash`) |
| `MONGODB_URI` | AI Service | MongoDB connection string |

> Additional AI service options such as `AI_TIMEOUT_MS`, `MAX_TEXT_LEN`, and `LOG_RAW_AI` are documented in `ai-service/.env.example`.

> **Never commit `.env` files to the repository.**

---

### Running with Docker Compose

The backend `docker-compose.yml` orchestrates PostgreSQL, the Spring Boot API, and the React frontend:

```bash
cd backend
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| PostgreSQL | localhost:5432 |

> Start the AI service separately (see below) or add it to the compose file as an additional service.

---

### Running Services Individually

#### Backend

```bash
cd backend
./gradlew :app:bootRun
```

Flyway will automatically apply all database migrations on startup. API available at `http://localhost:8080`.

#### Frontend

```bash
cd frontend
npm install
npm start
```

App available at `http://localhost:3000`.

#### AI Service

```bash
cd ai-service
cp .env.example .env      # fill in GEMINI_API_KEY and MONGODB_URI
npm install
npm run dev               # uses nodemon for hot-reload
```

Service available at `http://localhost:3001`.

---

## API Reference

All backend endpoints require a valid JWT `Authorization: Bearer <token>` header except `/api/auth/**`.

### Authentication
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Authenticate and receive JWT |

### Workflows
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/workflows` | List all workflows for current tenant |
| `POST` | `/api/workflows` | Create a new workflow |
| `GET` | `/api/workflows/{id}` | Get workflow details |
| `PUT` | `/api/workflows/{id}` | Update a workflow |
| `DELETE` | `/api/workflows/{id}` | Delete a workflow |

### Workflow Steps
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/workflows/{id}/steps` | List steps for a workflow |
| `POST` | `/api/workflows/{id}/steps` | Add a step to a workflow |
| `PUT` | `/api/workflows/{id}/steps/{stepId}` | Update a step |
| `DELETE` | `/api/workflows/{id}/steps/{stepId}` | Delete a step |

### Execution
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/workflows/{id}/execute` | Trigger a workflow run |
| `GET` | `/api/executions` | List all workflow runs |
| `GET` | `/api/executions/{runId}/logs` | Fetch per-step execution logs |

### AI
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/ai/generate-workflow` | Generate workflow draft from plain text prompt |
| `POST` | `/api/ai/analyze` | Analyze execution logs for anomalies |
| `POST` | `/api/ai/drafts/{draftId}/approve` | Approve AI-generated draft → creates live workflow |
| `POST` | `/api/ai/drafts/{draftId}/reject` | Reject AI-generated draft |

### System
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/actuator/health` | Service liveness probe |

---

## Database Schema

The PostgreSQL schema consists of 11 tables with Row-Level Security enforced on all tenant-scoped tables via the `app.current_organization` session variable:

```
organizations        → Root tenant entity
users                → Scoped to organization (RLS)
roles                → Organization-scoped roles
user_roles           → User-role junction table
workflow             → Workflow definitions (RLS)
workflow_steps       → DAG step definitions with depends_on JSONB
workflow_execution   → Per-execution records with trigger_data JSONB
step_execution       → Per-step results with input/output JSONB and attempt_count
workflow_run         → Coarse-grained run tracking — PENDING/RUNNING/RETRYING/SUCCESS/FAILED
workflow_schedule    → Cron-based scheduling with timezone and next_run_at
workflow_draft       → AI-generated drafts — PENDING/APPROVED/REJECTED
```

---

## Contributing

1. Fork the repository and create a feature branch off `develop`:
   ```bash
   git checkout develop
   git checkout -b feat/your-feature-name
   ```
2. Commit changes following [Conventional Commits](https://www.conventionalcommits.org/):
   ```
   feat:     new feature
   fix:      bug fix
   chore:    config or tooling change
   refactor: code restructure without behaviour change
   docs:     documentation update
   ```
3. Ensure the backend builds and the frontend compiles without errors:
   ```bash
   ./gradlew build        # backend
   npm run build          # frontend & ai-service
   ```
4. Push your branch and open a Pull Request targeting `develop`.

---

## Contributors

| Name | Role |
|---|---|
| [Sarvesh Joshi](https://github.com/s-2809-j) | Frontend (React.js) · Backend (Spring Boot) |
| Omkar Gaikwad | AI Service (Node.js · Express · Google Gemini · MongoDB) |

---

## License

This project is licensed under the **MIT License**. See [LICENSE](./LICENSE) for full terms.

© 2026 Sarvesh Joshi
