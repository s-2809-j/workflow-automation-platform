# Workflow Automation Platform

A full-stack, AI-augmented workflow automation system that enables organizations to design, execute, and monitor multi-step workflows through a visual interface — with built-in AI assistance for workflow generation and execution log analysis.

---

## Architecture Overview

The platform is composed of three independently deployable services:

```
┌─────────────────────┐     REST API      ┌──────────────────────┐     HTTP      ┌─────────────────────┐
│   React Frontend    │ ────────────────► │  Spring Boot Backend │ ────────────► │  Node.js AI Service │
│     (Port 3000)     │ ◄──────────────── │     (Port 8080)      │ ◄──────────── │     (Port 5000)     │
└─────────────────────┘                   └──────────────────────┘               └─────────────────────┘
                                                     │                                      │
                                                     ▼                                      ▼
                                           ┌──────────────────┐               ┌─────────────────────┐
                                           │   PostgreSQL DB   │               │      MongoDB         │
                                           │  (Multi-tenant,  │               │  (Execution Logs,   │
                                           │      RLS)         │               │   Workflow Drafts)  │
                                           └──────────────────┘               └─────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React.js, Axios, React Router |
| Backend API | Spring Boot 3, Spring Security, Spring Data JPA |
| AI Service | Node.js, Express.js, Zod, Mongoose |
| Primary Database | PostgreSQL (Row-Level Security, Flyway migrations) |
| AI Service Database | MongoDB |
| Authentication | JWT, BCrypt |
| Build Tools | Gradle (Backend), npm (Frontend & AI Service) |

---

## Repository Structure

```
workflow-automation-platform/
├── frontend/               # React.js SPA
│   ├── src/
│   │   ├── components/     # Login, ProtectedRoutes
│   │   ├── pages/          # Workflows, Executions, AIDrafts, Logs
│   │   └── services/       # Axios API clients
│   └── public/
│
├── backend/                # Spring Boot REST API
│   ├── src/main/java/
│   │   ├── api/            # Controllers
│   │   ├── application/    # Use cases (GenerateWorkflow, ApproveDraft)
│   │   ├── domain/         # Entities, Repositories
│   │   ├── config/         # Security, CORS, WebClient
│   │   └── infrastructure/ # JPA, Filters (JWT, Tenant, CorrelationId)
│   └── build.gradle
│
└── ai-service/             # Node.js Express Microservice
    ├── src/
    │   ├── routes/         # workflow, executionLog routes
    │   ├── controllers/    # AI generation, log analysis
    │   ├── services/       # LLM integration, retry orchestration
    │   └── models/         # ExecutionLog, WorkflowDraft (Mongoose)
    └── package.json
```

---

## Features

- **Workflow Management** — Create, configure, and manage multi-step workflows with a visual interface
- **DAG Execution Engine** — Steps execute in dependency order using a Directed Acyclic Graph with topological sorting
- **Cron Scheduler** — Time-triggered workflow execution with timezone-aware cron expressions
- **Retry Mechanism** — Configurable exponential and fixed retry strategies with per-step attempt tracking
- **AI Workflow Generation** — Generate workflow definitions from natural language prompts via LLM integration
- **AI Draft Review** — Approve or reject AI-generated workflow drafts before they become live workflows
- **Execution Log Analysis** — Analyze step execution logs for anomalies and retry decisions using AI
- **Multi-Tenant Architecture** — Organization-scoped data isolation enforced at the database level via PostgreSQL Row-Level Security
- **JWT Authentication** — Stateless authentication with role-based route protection

---

## Prerequisites

Ensure the following are installed before setting up the project:

- Node.js v18 LTS or higher
- JDK 17 or higher
- PostgreSQL 14 or higher
- MongoDB 6 or higher
- Gradle 8 or higher

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/s-2809-j/workflow-automation-platform.git
cd workflow-automation-platform
```

---

### 2. Database Setup (PostgreSQL)

```sql
CREATE DATABASE workflow_db;
```

Flyway will automatically run all migrations when the Spring Boot application starts. Ensure your database credentials match the environment variables in the backend configuration.

---

### 3. Backend — Spring Boot

Navigate to the backend directory:

```bash
cd backend
```

Create an `application-dev.yml` file under `src/main/resources/` with the following configuration:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_db
    username: your_db_username
    password: your_db_password
  jpa:
    hibernate:
      ddl-auto: validate

jwt:
  secret: your_jwt_secret_key

ai-service:
  base-url: http://localhost:5000
```

Run the application:

```bash
./gradlew bootRun
```

Backend will start on `http://localhost:8080`

---

### 4. AI Service — Node.js

Navigate to the AI service directory:

```bash
cd ai-service
npm install
```

Create a `.env` file:

```env
PORT=5000
MONGODB_URI=mongodb://localhost:27017/ai_service_db
LLM_API_KEY=your_llm_api_key
```

Start the service:

```bash
npm start
```

AI Service will start on `http://localhost:5000`

---

### 5. Frontend — React

Navigate to the frontend directory:

```bash
cd frontend
npm install
```

Create a `.env` file:

```env
REACT_APP_API_BASE_URL=http://localhost:8080/api/v1
```

Start the development server:

```bash
npm start
```

Frontend will start on `http://localhost:3000`

---

## API Reference

### Authentication
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/login` | Login and receive JWT token |

### Workflows
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/workflows` | Get all workflows for current tenant |
| POST | `/api/v1/workflows` | Create a new workflow |
| GET | `/api/v1/workflows/{id}` | Get workflow by ID |
| PUT | `/api/v1/workflows/{id}` | Update workflow |
| DELETE | `/api/v1/workflows/{id}` | Delete workflow |

### Workflow Steps
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/workflows/{id}/steps` | Get all steps for a workflow |
| POST | `/api/v1/workflows/{id}/steps` | Add a step to a workflow |
| PUT | `/api/v1/workflows/{id}/steps/{stepId}` | Update a step |
| DELETE | `/api/v1/workflows/{id}/steps/{stepId}` | Delete a step |

### Execution
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/workflows/{id}/execute` | Trigger workflow execution |
| GET | `/api/v1/workflows/{id}/executions` | Get execution history |

### AI Service
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/ai/generate-workflow` | Generate workflow draft from prompt |
| POST | `/api/v1/ai/analyze` | Analyze execution logs for anomalies |
| POST | `/api/v1/ai/drafts/{draftId}/approve` | Approve AI-generated draft |
| POST | `/api/v1/ai/drafts/{draftId}/reject` | Reject AI-generated draft |

### System
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/health` | Service liveness probe |

---

## Database Schema

The PostgreSQL schema consists of 11 tables with Row-Level Security enforced on all tenant-scoped tables:

```
organizations        → Root tenant entity
users                → Scoped to organization
roles                → Organization-scoped roles (schema ready)
user_roles           → User-role junction table (schema ready)
workflow             → Workflow definitions
workflow_steps       → DAG step definitions with depends_on JSONB
workflow_execution   → Per-execution records with trigger_data JSONB
step_execution       → Per-step execution with input/output JSONB
workflow_run         → Coarse-grained run tracking with retry_count
workflow_schedule    → Cron-based scheduling with timezone support
workflow_draft       → AI-generated drafts pending approval
```

---

## Environment Variables

**Never commit `.env` files to the repository.** All sensitive configuration must be managed via environment variables.

| Variable | Service | Description |
|---|---|---|
| `spring.datasource.password` | Backend | PostgreSQL password |
| `jwt.secret` | Backend | JWT signing secret |
| `ai-service.base-url` | Backend | Node.js AI Service URL |
| `MONGODB_URI` | AI Service | MongoDB connection string |
| `LLM_API_KEY` | AI Service | LLM provider API key |
| `REACT_APP_API_BASE_URL` | Frontend | Spring Boot base URL |

---

## Contributing

This project follows a feature-branch Git workflow:

```bash
# Create a feature branch from develop
git checkout develop
git checkout -b feature/your-feature-name

# Commit with conventional commit messages
git commit -m "feat: add role-based access control to workflow endpoints"

# Push and open a Pull Request → develop
git push origin feature/your-feature-name
```

**Commit message conventions:**

| Prefix | Usage |
|---|---|
| `feat:` | New feature |
| `fix:` | Bug fix |
| `chore:` | Config, dependency, or tooling change |
| `refactor:` | Code restructure without feature change |
| `docs:` | Documentation update |

---

## Contributors

| Name | Role |
|---|---|
| [Sarvesh Joshi](https://github.com/s-2809-j) | Frontend (React.js) · Backend (Spring Boot) |
| Omkar Gaikwad | AI Service (Node.js · Express · MongoDB) |

---

## License

This project is developed for academic and learning purposes.
