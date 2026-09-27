# PROJECT SCOPE AND ARCHITECTURE ANALYSIS

> **Document Type**: Architecture Specification & MVP Scope Definition  
> **Target Context**: Student Internship Project / Interview-Ready System  
> **Repository**: Workflow Automation Platform (Backend: Spring Boot / Java 17 / PostgreSQL; Frontend: React)

---

# 1. Project Definition

The **Workflow Automation Platform** is a multi-tenant, AI-assisted workflow orchestration system designed to automate multi-step technical and business tasks. Users can either define DAG-based (Directed Acyclic Graph) workflows manually or generate them from natural language prompts using an AI draft generator. Once configured and approved, workflows are executed asynchronously by an in-engine scheduler supporting step dependency resolution, isolated step execution, configurable retries with AI failure analysis, and multi-tenant security enforced at the database level via PostgreSQL Row-Level Security (RLS).

---

# 2. Current Technology Stack

| Component | Technology | Purpose | Current Status |
|---|---|---|---|
| **Frontend Framework** | React 18 (SPA) | Single-page user interface for auth, workflow creation, AI drafts, step editor, and execution visualization | **Active & Functional** |
| **Frontend HTTP Client** | Axios | API communication with JWT interceptors and response normalization | **Active & Functional** |
| **Backend Framework** | Spring Boot 3.2.2 (Java 17) | Core backend REST API, application services, and business logic | **Active & Functional** |
| **Security Layer** | Spring Security 6 + JJWT | Stateless JWT authentication, role/tenant extraction, request filtering | **Active & Functional** |
| **Persistence & ORM** | Spring Data JPA / Hibernate 6 | Relational data access, entity lifecycle management | **Active & Functional** |
| **Database** | PostgreSQL 15+ | Multi-tenant storage with Row-Level Security (RLS) policies | **Active & Functional** |
| **Database Migrations** | Flyway 9.22 | Versioned DDL migrations and baseline schema control | **Active & Functional** |
| **Script Execution Engine** | GraalVM Polyglot (JavaScript) | Sandboxed in-process JavaScript execution for `SCRIPT` steps | **Active & Functional** |
| **Email Service** | Spring Mail (JavaMailSender) / Jakarta Mail | SMTP email dispatch for `EMAIL` notification steps | **Active (requires valid SMTP config)** |
| **Cron Scheduling** | Spring `@Scheduled` + Spring `CronExpression` | Polling daemon for due scheduled workflow triggers | **Active & Functional** |
| **AI Integration** | External AI HTTP Service + Local Mock Fallback | Natural language workflow generation and execution error diagnosis | **Active & Functional** |
| **Build & Test Tooling** | Gradle 8.5 (Wrapper) + JUnit 5 | Compilation, dependency resolution, and test automation | **Active & Functional** |

---

# 3. Current Architecture

```text
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   FRONTEND (React SPA)                                  │
│   ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌─────────────┐ ┌───────────┐   │
│   │ Login/Register│ │  AI Drafts UI │ │Workflow Detail│ │ Executions  │ │ Analytics │   │
│   └───────┬───────┘ └───────┬───────┘ └───────┬───────┘ └──────┬──────┘ └─────┬─────┘   │
└───────────┼─────────────────┼─────────────────┼────────────────┼──────────────┼─────────┘
            │                 │                 │                │              │
            ▼                 ▼                 ▼                ▼              ▼
     [ HTTP REST API with Authorization: Bearer <JWT> ] (Port 8080)
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                               SPRING BOOT BACKEND CORE                                  │
│                                                                                         │
│   ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│   │ Security & Tenant Filter Chain                                                  │   │
│   │ JwtAuthenticationFilter ──► TenantFilter ──► TenantContextHolder (orgId, userId)│   │
│   └────────────────────────────────────────┬────────────────────────────────────────┘   │
│                                            │                                            │
│   ┌────────────────────────────────────────┴────────────────────────────────────────┐   │
│   │ REST API Controllers                                                            │   │
│   │ AuthController | WorkflowController | WorkflowStepController                    │   │
│   │ WorkflowExecutionController | WorkflowScheduleController | AiController         │   │
│   └────────────────────────────────────────┬────────────────────────────────────────┘   │
│                                            │                                            │
│   ┌────────────────────────────────────────┴────────────────────────────────────────┐   │
│   │ Application & Domain Services                                                   │   │
│   │ ├── AuthenticationService (Bcrypt password hashing, token issue)                │   │
│   │ ├── WorkflowService & WorkflowStepService (CRUD, step reordering)               │   │
│   │ ├── GenerateWorkflowUseCase & ApproveDraftUseCase (AI generation/approval)     │   │
│   │ ├── WorkflowExecutionService (Tx management, execution lifecycle, RLS context)  │   │
│   │ ├── WorkflowScheduleService & WorkflowScheduleTrigger (Cron polling)            │   │
│   │ └── WorkflowScheduler (DAG resolution, thread pool, step dispatch)              │   │
│   │       ├── StepAttemptTransactionService (HTTP, SCRIPT, EMAIL, LOG, DELAY)       │   │
│   │       └── RetryOrchestrator (AI analysis & retry backoff)                       │   │
│   └────────────────────────────────────────┬────────────────────────────────────────┘   │
└────────────────────────────────────────────┼────────────────────────────────────────────┘
                                             │
                      ┌──────────────────────┴──────────────────────┐
                      ▼                                             ▼
       ┌──────────────────────────────┐              ┌──────────────────────────────┐
       │   POSTGRESQL DATABASE        │              │     AI SERVICE COMPONENT     │
       │  (Multi-tenant RLS active)   │              │  (Port 3001 or Mock Mode)    │
       │  • organizations             │              │  • POST /api/v1/workflows/   │
       │  • users                     │              │    generate                  │
       │  • workflow                  │              │  • POST /api/v1/analyze      │
       │  • workflow_steps            │              └──────────────────────────────┘
       │  • workflow_draft            │
       │  • workflow_execution        │
       │  • step_execution            │
       │  • workflow_schedule         │
       └──────────────────────────────┘
```

### Component Roles
1. **Frontend**: Manages user authentication tokens, presents natural language workflow generation interfaces, provides interactive step configuration modals (e.g. Email recipient modes), and polls execution statuses.
2. **Security & Tenant Pipeline**: Intercepts requests, validates JWT claims, populates `TenantContextHolder`, and binds tenant identity to transaction session variables.
3. **Execution Engine & Scheduler**: Constructs a DAG in memory, evaluates step readiness based on upstream status, dispatches ready steps across a worker pool (`ThreadPoolExecutor`), persists individual step attempt states, and handles AI-assisted retries.
4. **PostgreSQL RLS**: Enforces organization isolation directly in SQL queries using `SET LOCAL app.current_organization`.
5. **AI Service**: Provides prompt-to-workflow synthesis and execution error diagnostics via REST or internal fallback generators.

---

# 4. Current User Journey

```text
1. Sign Up / Login
   User registers with email & password ──► System creates Organization & User ──► JWT returned.

2. Navigation to Dashboard / AI Drafts
   User navigates to "AI Drafts" in the sidebar.

3. Natural Language Workflow Generation
   User submits prompt: "Fetch USD exchange rate, check if INR > 83, and send alert email"
   ──► AiController (POST /api/v1/ai/drafts) calls AI service
   ──► Draft is saved in `workflow_draft` table (status = PENDING_APPROVAL).

4. Review & Configuration
   User views generated steps in UI:
   - Step 1 (HTTP): GET https://api.exchangerate-api.com/v4/latest/USD
   - Step 2 (SCRIPT): Compare rate against threshold
   - Step 3 (EMAIL): Configured with recipient source (`FIXED`, `WORKFLOW_INPUT`, or `PREVIOUS_STEP_OUTPUT`)
   - Step 4 (LOG): Log outcome
   User configures verified email address in the draft configuration panel.

5. Approval
   User clicks "Approve Draft" ──► Backend validates EMAIL step configuration
   ──► Creates `WorkflowEntity` and `WorkflowStepEntity` rows ──► Marks draft `APPROVED`.

6. Workflow Management & Manual Execution
   User views the created workflow under "Workflows", inspects step details, or clicks "Run Workflow".
   ──► Calls `POST /api/workflows/{id}/execute`
   ──► Execution is created in `workflow_execution` table (status = `RUNNING`).

7. Asynchronous Execution & Observability
   WorkflowScheduler executes steps according to dependency order:
   - Step 1 (HTTP) ──► `SUCCESS` (Output: JSON body with exchange rates)
   - Step 2 (SCRIPT) ──► `SUCCESS` (Evaluated JS logic)
   - Step 3 (EMAIL) ──► Dispatches email via SMTP
   - Step 4 (LOG) ──► Logs execution message
   Workflow completes as `SUCCESS` or `FAILED` (with detailed reason stored in `errorMessage`).

8. Execution History & Logs
   User inspects execution runs and step-by-step input/output payloads in the "Executions" view.
```

---

# 5. Current Workflow Architecture

The execution model operates on a relational hierarchy mapped to an in-memory Directed Acyclic Graph (DAG):

```text
WorkflowEntity (id, organizationId, name, status)
       │
       ▼ 1:N
WorkflowStepEntity (id, workflowId, stepOrder, stepType, config, dependsOn)
       │
       ▼ (Execution Instance)
WorkflowExecutionEntity (id, workflowId, status, triggerData, errorMessage, startedAt, completedAt)
       │
       ▼ 1:N
StepExecutionEntity (id, workflowExecutionId, stepId, status, inputData, outputData, attemptCount)
```

### Data Flow Between Steps
- **`triggerData`**: Initial inputs passed when starting a workflow execution (stored in `workflow_execution.trigger_data`).
- **`dependsOn`**: JSON array of step UUIDs in `workflow_step.depends_on` indicating upstream prerequisite steps.
- **Data Resolution**:
  - `StepAttemptTransactionService` receives the current `step` and `executionId`.
  - For `EMAIL` steps using `PREVIOUS_STEP_OUTPUT`, it queries `step_execution` table for parent step's `output_data` JSON.
  - For `SCRIPT` steps, bindings can be passed via `config.inputs`.

---

# 6. Current Step Types

| Step Type | Purpose | Input Configuration | Output Data | Current Status |
|---|---|---|---|---|
| **`HTTP`** | Makes external HTTP/REST requests (GET, POST, PUT, DELETE) | `{"url": "...", "method": "GET", "headers": {}, "body": {}}` | `{"statusCode": 200, "responseBody": {...}, "timestamp": "..."}` | **Fully Implemented** |
| **`SCRIPT`** | Executes sandboxed JavaScript logic using GraalVM Polyglot | `{"script": "return rates.INR > 83;", "inputs": {...}}` | `{"result": true, "stdout": "...", "timestamp": "..."}` | **Fully Implemented** |
| **`EMAIL`** | Sends notifications via SMTP with strict recipient source resolution | `{"recipientSource": "FIXED", "recipient": "user@example.com", "subject": "...", "body": "...", "isHtml": false}` | `{"status": "email_sent", "to": "...", "recipientSource": "...", "subject": "..."}` | **Fully Implemented** |
| **`LOG`** | Logs structured execution messages to backend logs | `{"message": "Check completed", "level": "INFO"}` | `{"status": "logged", "level": "INFO", "message": "...", "timestamp": "..."}` | **Fully Implemented** |
| **`DELAY`** | Pauses workflow thread execution for a specified duration | `{"duration": 5000}` (0 to 300,000 ms) | `{"status": "delayed", "durationMs": 5000, "timestamp": "..."}` | **Fully Implemented** |
| **`DATABASE`** | Placeholder for direct database operations | `{"query": "...", "type": "..."}` | `{"status": "database_executed"}` | **Partial / Stub** |
| **`WEBHOOK`** | Outbound webhook trigger call | `{"url": "...", "method": "POST", "body": {...}}` | `{"status": "webhook_sent", "statusCode": 200}` | **Partial / Stub** |
| **`ACTION`** | Generic legacy action runner (redirects to HTTP/Email) | Generic JSON configuration | Output of underlying handler | **Deprecated / Legacy** |

---

# 7. Current AI Architecture

```text
User Natural Language Prompt
              │
              ▼
 AiController.createDraft()
              │
              ▼
 GenerateWorkflowUseCase
       │              │
       ▼ (if online)  ▼ (if offline/mock mode)
 External AI Service   Internal Rule-Based Fallback Generator
 (POST /generate)      (Generates valid multi-step DAG draft)
              │
              ▼
 WorkflowDraft Entity (Stored in workflow_draft table, status: PENDING_APPROVAL)
              │
              ▼
 Frontend Review & Configuration (User specifies verified email / params)
              │
              ▼
 ApproveDraftUseCase (Validates safety, creates WorkflowEntity & WorkflowStepEntity rows)
```

### What AI DOES Do:
1. Synthesizes a structured JSON DAG with step orders, step types, mock configurations, and dependency IDs based on natural language prompts.
2. Performs error analysis during step failures via `RetryOrchestrator` to decide whether a retry strategy should be `FIXED`, `EXPONENTIAL`, or `NONE`.

### What AI DOES NOT Do:
1. Does **not** execute arbitrary unchecked code directly against production infrastructure.
2. Does **not** bypass tenant isolation or PostgreSQL RLS policies.
3. Does **not** send unverified emails to fabricated addresses (strictly guarded by recipient configuration validation).

---

# 8. Current Security Architecture

1. **Authentication**: Stateless JWT token authentication. Users authenticate via `/api/auth/login` and receive a signed Bearer token containing `userId`, `organizationId`, and `email`.
2. **Organization Context**: `JwtAuthenticationFilter` parses the token; `TenantFilter` sets the active tenant in `TenantContextHolder` (ThreadLocal).
3. **Database RLS (Row-Level Security)**:
   - PostgreSQL tables (`workflow`, `workflow_steps`, `workflow_execution`, `step_execution`, `workflow_draft`, `workflow_schedule`) have RLS enabled.
   - Application services invoke `SET LOCAL app.current_organization = '<orgId>'` within every transactional boundary.
   - PostgreSQL RLS policies enforce `organization_id = NULLIF(current_setting('app.current_organization', true), '')::uuid`.
4. **Cross-Tenant Isolation**: Even if a user attempts to request an entity by UUID belonging to another organization, PostgreSQL queries return 0 rows at the SQL level.

---

# 9. Current Scheduler

1. **Scheduling Engine**: Spring `@Scheduled` background worker in `WorkflowScheduleTrigger.java` polls every 60 seconds (`fixedDelay = 60000`).
2. **Schedule Entity**: Stored in `workflow_schedule` with `cron_expression`, `timezone`, `next_run_at`, `last_run_at`, and `organization_id`.
3. **Execution Invocation**:
   - `WorkflowScheduleService.triggerDueSchedules()` queries all schedules where `next_run_at <= NOW()`.
   - For each due schedule, it explicitly establishes the tenant context (`setOrganizationContext(schedule.getOrganizationId())`) to satisfy RLS.
   - Calls `WorkflowExecutionService.startExecutionInternal(...)` to launch the execution.
   - Recalculates and updates `next_run_at` using Spring `CronExpression`.

---

# 10. Current Email Architecture

```text
EMAIL Step Triggered
        │
        ▼
 StepAttemptTransactionService.executeEmail()
        │
        ▼
 Recipient Source Resolution:
 ├── FIXED: uses config.recipient
 ├── WORKFLOW_INPUT: resolves from execution.triggerData
 └── PREVIOUS_STEP_OUTPUT: queries parent step_execution.output_data
        │
        ▼
 Validation Check: Is address configured? (Fails fast if empty/fabricated)
        │
        ▼
 JavaMailSenderImpl (SMTP dispatch to smtp.gmail.com:587)
        │
        ▼
 Recipient Inbox
```

- **SMTP Configuration**: Currently uses application-level credentials configured in `application.yml` (`spring.mail.username` and `spring.mail.password`).
- **Safety**: Unconfigured EMAIL steps are blocked at approval time and runtime to avoid blind sending.

---

# 11. Current Limitations

1. **Static Trigger Payloads**: Currently, `POST /api/workflows/{id}/execute` does not accept a custom JSON body from the UI; executions always start with an empty `{}` `triggerData`.
2. **Single Tenant Context on Unauthenticated Endpoints**: Inbound public webhook triggers do not exist yet.
3. **No Intermediate Pause / Human-in-the-Loop State**: Executions run non-stop from root steps to leaf steps; there is no suspended `WAITING_FOR_USER_INPUT` state.
4. **Basic Data Mapping**: Intermediate data passing relies on JSON keys rather than a visual variable picker.
5. **Single SMTP Gateway**: All tenant emails share the backend's configured SMTP server.

---

# 12. Zapier Concept Mapping

| Zapier Concept | Our Current System | Status | Gap |
|---|---|---|---|
| **Trigger** | Manual (`POST /execute`) & Scheduled (Cron polling) | **Implemented** | No dynamic runtime input parameters provided by the user at run-time |
| **Input** | `trigger_data` column in `workflow_execution` | **Implemented** | Backend supports `triggerData`, but Frontend "Run" button does not collect inputs |
| **Data Mapping** | `dependsOn` DAG links + JSON output storage | **Implemented** | Steps can read parent outputs via code; no interactive UI parameter binding |
| **Processing** | `SCRIPT` step (GraalVM JS) & `DELAY` step | **Implemented** | Fully functional JavaScript transformations |
| **AI Decision** | AI Prompt Generation + AI Error Analysis & Retry | **Implemented** | AI operates during design time and failure recovery, not as an in-line condition node |
| **Decision / Logic**| `SCRIPT` step returning boolean + Step failure branching | **Implemented** | No explicit `IF/ELSE` visual edge routing, but scripts can throw/halt |
| **Action** | `HTTP`, `EMAIL`, `LOG` steps | **Implemented** | Highly reliable action executors |
| **Execution** | Multi-threaded async DAG scheduler | **Implemented** | Parallel execution with thread pool |
| **Result** | `workflow_execution` & `step_execution` logs | **Implemented** | Full input/output payload inspection in UI |

---

# 13. Candidate Small Features (Zapier-like Options)

### Option A: Runtime User Input Trigger (Parameterized Workflow Execution)
- **Purpose**: Allow the user to provide custom parameters (e.g. `City`, `TargetCurrency`, `RecipientEmail`) when clicking "Run Workflow", passing them into `workflow_execution.trigger_data`.
- **Modules Reused**: `WorkflowExecutionController`, `WorkflowExecutionService`, `StepAttemptTransactionService`, `WorkflowDetail.js`, `api.js`.
- **New Code Required**: Add optional `@RequestBody JsonNode inputData` to `POST /api/workflows/{id}/execute`; add a simple modal in React to prompt for parameters before running.
- **Implementation Size**: Very Small (~2 backend files, ~2 frontend files).
- **Risk**: Minimal.
- **Classification**: 🟢 **GREEN**

### Option B: Interactive Human-in-the-Loop Approval Step (Pause & Resume)
- **Purpose**: Workflow pauses at a specific approval step with status `WAITING_FOR_APPROVAL`, prompting the user in the UI to click "Approve" or "Reject" before continuing downstream actions (e.g., sending an email).
- **Modules Reused**: `WorkflowScheduler`, `StepExecutionEntity`, `WorkflowExecutionEntity`, `Executions.js`.
- **New Code Required**: New step status `WAITING_FOR_APPROVAL`, resume execution endpoint, state machine persistence.
- **Implementation Size**: Medium (~5 backend files, ~3 frontend files).
- **Risk**: Moderate (requires scheduler suspension and thread release).
- **Classification**: 🟡 **YELLOW**

### Option C: Intermediate Step Execution Visualizer (Step-by-Step Stepper)
- **Purpose**: Allows users to step through a workflow one node at a time in the UI, inspecting output before manually triggering the next step.
- **Modules Reused**: `WorkflowStepExecutionService`, `StepExecutionRepository`.
- **New Code Required**: Single-step execution controller endpoint, manual DAG progression tracker.
- **Implementation Size**: Moderate (~4 backend files, ~3 frontend files).
- **Risk**: Moderate (changes the execution lifecycle).
- **Classification**: 🟡 **YELLOW**

### Option D: Simple Conditional Step (If/Else Branching Node)
- **Purpose**: A condition step that evaluates an expression; if true, runs branch A; if false, runs branch B.
- **Modules Reused**: `DagBuilder`, `WorkflowScheduler`, `StepAttemptTransactionService`.
- **New Code Required**: Conditional edge evaluation in DAG builder, branch skipping logic.
- **Implementation Size**: Moderate-Large (~6 backend files).
- **Risk**: High (potential scheduler deadlocks or edge skips).
- **Classification**: 🔴 **RED**

### Option E: Inbound Webhook Trigger
- **Purpose**: Allow an external service (e.g. GitHub/Stripe webhook) to trigger a workflow via a public URL `POST /api/webhooks/{workflowToken}`.
- **Modules Reused**: `WorkflowExecutionService`.
- **New Code Required**: Webhook token generation, unauthenticated endpoint with tenant extraction from token.
- **Implementation Size**: Moderate (~4 backend files).
- **Risk**: Moderate (security & tenant bypass concerns).
- **Classification**: 🟡 **YELLOW**

---

# 14. Recommended Feature: **Runtime Parameterized Execution (Option A)**

### **Why Option A is the Absolute Best Choice**:
1. **Maximum Existing Code Reuse**: The backend database table `workflow_execution` **already has** the `trigger_data` column mapped to `JsonNode`, and `StepAttemptTransactionService` already has helper methods to resolve variables from `execution.getTriggerData()`!
2. **Minimal Changes**: Only requires accepting a JSON body in `WorkflowExecutionController.execute` and adding a clean "Run with Parameters" input modal in `WorkflowDetail.js`.
3. **High Demo & Interactive Value**: In a live interview demo, the interviewer can enter their own city or email (e.g. `currency: "EUR"`, `email: "interviewer@example.com"`), click "Run", and immediately see the workflow fetch live data for that specific input and send the result.
4. **Zero Structural Risk**: Does not alter the scheduler's threading model, DAG traversal, or transaction management.
5. **Timeline**: Achievable within 1–2 days.

---

# 15. Final MVP Scope

| Feature / Capability | Included? | Technical & Practical Reason |
|---|---|---|
| **JWT Authentication & Registration** | ✅ **YES** | Multi-tenant user login and secure token exchange is already fully working |
| **PostgreSQL RLS Multi-Tenancy** | ✅ **YES** | Core architectural highlight showcasing robust data isolation |
| **AI Workflow Draft Generation** | ✅ **YES** | Natural language prompt synthesis into actionable DAGs |
| **AI Draft Review & Email Validation** | ✅ **YES** | Prevents unconfigured email dispatch and lets user configure parameters |
| **Manual Workflow Execution** | ✅ **YES** | Core execution triggering mechanism |
| **Runtime Parameter Input Modal** | ✅ **YES (New Feature)** | Lets user provide dynamic inputs (e.g. `{ "city": "London", "email": "test@demo.com" }`) |
| **HTTP Step Execution** | ✅ **YES** | Real REST calls to external public APIs |
| **SCRIPT Step Execution (GraalVM)** | ✅ **YES** | In-process JavaScript transformations and threshold evaluations |
| **EMAIL Step Execution (SMTP)** | ✅ **YES** | Real email notification with recipient source resolution |
| **LOG & DELAY Step Execution** | ✅ **YES** | Observability and rate limiting steps |
| **Cron Scheduling** | ✅ **YES** | Demonstrates automated time-based workflow polling |
| **Execution History & Observability** | ✅ **YES** | Step-by-step status, timing, and error message logging |

---

# 16. Explicitly Out of Scope

The following items are **strictly excluded** to maintain finishability and prevent unnecessary complexity:

- ❌ **OAuth2 Integrations (Google/Microsoft OAuth)**: Requires complex callback redirect flows and third-party app verification.
- ❌ **Complex Conditional Branching (IF/ELSE DAG Splitter)**: Risk of scheduler edge deadlocks.
- ❌ **Visual Drag-and-Drop Node Canvas (React Flow / React Flow Renderer)**: High frontend overhead with low backend interview relevance.
- ❌ **Distributed Task Workers (RabbitMQ / Celery / Kafka)**: Unnecessary infrastructure overhead for single-instance demo.
- ❌ **Per-Tenant Custom SMTP Servers**: Application-level SMTP credentials are fully sufficient for demonstration.
- ❌ **Billing, Subscriptions, and Organization RBAC Roles**: Focus is on workflow automation and backend engineering.

---

# 17. Implementation Roadmap (5–7 Days Total)

```text
Day 1: Runtime Trigger Input API (Backend)
├── Task: Update WorkflowExecutionController.execute to accept optional @RequestBody JsonNode inputData.
├── Task: Pass triggerData through WorkflowExecutionService to WorkflowExecutionEntity.
├── Deliverable: POST /api/workflows/{id}/execute with JSON payload persists trigger_data.
└── Stop Condition: Unit tests verify triggerData is saved and accessible in StepAttemptTransactionService.

Day 2: "Run Workflow" Parameter Modal (Frontend)
├── Task: Add a "Run with Inputs" button and parameter input dialog in WorkflowDetail.js.
├── Task: Update services/api.js executeWorkflow(id, payload).
├── Deliverable: User can type custom key-value pairs before executing.
└── Stop Condition: Running a workflow from UI sends parameters to backend.

Day 3: Intermediate Variable Resolution Verification
├── Task: Ensure SCRIPT and EMAIL steps smoothly resolve values from trigger_data.
├── Task: Verify full Exchange Rate / Weather API demo scenario.
├── Deliverable: Workflow runs end-to-end using user-entered runtime variables.
└── Stop Condition: Step execution outputs reflect dynamic runtime inputs.

Day 4: Execution Status & Error UI Polish
├── Task: Display execution.errorMessage and step error payloads prominently in Executions.js.
├── Task: Add status badges (RUNNING, SUCCESS, FAILED) with auto-refresh polling.
├── Deliverable: Clear visual feedback when a step succeeds or fails.
└── Stop Condition: No silent failures in the UI.

Day 5: End-to-End Regression Testing & Demo Preparation
├── Task: Run full regression test suite (Auth -> AI Generation -> Edit -> Parameterized Run -> Execution View).
├── Task: Prepare seed demo workflows and standard script for interviews.
├── Deliverable: Rock-solid, reproducible demo flow.
└── Stop Condition: All Gradle tests pass, frontend builds without warnings.
```

---

# 18. Definition of Done (Acceptance Criteria)

1. **Authentication**: User can register, log in, receive a JWT, and access protected routes.
2. **AI Synthesis**: Natural language prompt creates a valid draft with correct step dependencies.
3. **Safety Validation**: Draft cannot be approved if an `EMAIL` step lacks a valid recipient configuration.
4. **Parameterized Trigger**: User can click "Run", provide dynamic JSON parameters (e.g. email, city), and execute.
5. **Step Execution**:
   - `HTTP` step executes live GET request and captures response body.
   - `SCRIPT` step transforms the data in GraalVM JS.
   - `EMAIL` step resolves recipient and dispatches email.
   - `LOG` step records the final message.
6. **Observability**: If any step fails, the exact error reason is visible in the UI and stored in `WorkflowExecutionEntity.errorMessage`.
7. **Security**: Organization A cannot view, edit, or execute workflows belonging to Organization B.
8. **Automated Verification**: `.\gradlew test` passes 100% cleanly without errors.

---

# 19. Interview-Relevant Concepts & Architecture Answers

### 1. PostgreSQL Row-Level Security (RLS) & Multi-Tenancy
- **What it is**: PostgreSQL native security where table rows are filtered automatically based on session variables.
- **Why we use it**: Instead of relying solely on `WHERE organization_id = ?` in every JPA query (which can be forgotten by developers), PostgreSQL RLS guarantees at the database engine level that no cross-tenant data leaks occur.

### 2. Spring Security & JWT Filter Chain
- **What it is**: Stateless token authentication using `OncePerRequestFilter`.
- **Why we use it**: Enables horizontal scalability without HTTP session state. `JwtAuthenticationFilter` validates token signatures and extracts claims, while `TenantFilter` sets the thread-local tenant context.

### 3. In-Memory DAG Orchestration & Thread Pool Scheduling
- **What it is**: Dependency graph traversal where steps execute as soon as all prerequisite step IDs reach `SUCCESS`.
- **Why we use it**: Allows non-blocking parallel step execution using a managed `ThreadPoolExecutor` while strictly respecting upstream data dependencies.

### 4. Transaction Isolation & RLS Session Binding
- **What it is**: Scoping `SET LOCAL app.current_organization` inside individual Spring `@Transactional` blocks.
- **Why we use it**: When threads from a connection pool execute tasks for different tenants, `SET LOCAL` ensures tenant settings automatically revert upon transaction commit/rollback, preventing tenant bleed across reused connections.

### 5. Sandboxed Script Execution via GraalVM Polyglot
- **What it is**: Running JavaScript inside the JVM with restricted host access and memory limits.
- **Why we use it**: Allows users to write custom transformation and threshold logic safely without spawning insecure external shell processes.

### 6. AI Resilience & Retry Backoff Strategies
- **What it is**: Combining AI diagnostic analysis with exponential and fixed backoff retries.
- **Why we use it**: Distinguishes between transient network glitches (which warrant automated retry) and fatal validation errors (which should fail immediately without wasting resources).

---

# 20. Final Recommendation

1. **What the Project Should Become**:  
   A clean, robust, multi-tenant AI Workflow Automation platform that highlights core software engineering fundamentals: multi-tenancy, asynchronous orchestration, DAG scheduling, sandboxed code execution, and AI-assisted creation.
2. **Exactly ONE Feature to Add**:  
   **Runtime Parameterized Execution (Option A)** — adding a dynamic input modal on execution so the user can pass runtime data (e.g. email, target thresholds, API parameters) directly into the workflow trigger.
3. **What Should NOT Be Added**:  
   Do not add OAuth2 authentication, visual drag-and-drop workflow canvases, Kafka/distributed queues, or complex conditional DAG branching.
4. **Estimated Remaining Implementation Time**:  
   **2 to 3 working days** for code additions, followed by 1 day of verification and demo rehearsal.
5. **The Final Demo Scenario**:  
   - **Step 1**: Type prompt: *"Create a workflow that fetches live exchange rates, checks if INR > 80, and emails the result."*
   - **Step 2**: AI creates draft with 4 steps (`HTTP` ──► `SCRIPT` ──► `EMAIL` ──► `LOG`).
   - **Step 3**: Configure email recipient source as `WORKFLOW_INPUT` and approve draft.
   - **Step 4**: Click "Run", enter runtime input: `{"recipientEmail": "interviewer@example.com", "threshold": 80}`.
   - **Step 5**: Watch steps execute in real time, view intermediate payloads, verify email delivery, and inspect execution logs.
