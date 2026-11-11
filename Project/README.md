# ResolveIt — Online Complaint and Grievance Management System

**ResolveIt** is a full-stack three-tier modular-monolith complaint and grievance management platform built with **React + Vite + Tailwind CSS**, **Spring Boot 3 + Spring Security (JWT)**, and **PostgreSQL + Flyway**.

It supports citizen/user complaint filing, public ticket tracking, triage and department routing, single-active-owner staff assignment, SLA countdowns with automated escalation, internal staff notes vs. public replies, evidence file uploads, post-resolution ratings, analytics reporting with CSV export, and Swagger/OpenAPI documentation.

---

## Table of Contents

- [Core Features](#core-features)
- [Architecture & Project Structure](#architecture--project-structure)
- [Workflow State Machine & SLA Policies](#workflow-state-machine--sla-policies)
- [Database Schema](#database-schema)
- [REST API & Frontend Routes](#rest-api--frontend-routes)
- [Environment Variables](#environment-variables)
- [How to Run on Any Device](#how-to-run-on-any-device)
  - [Option 1: Docker Compose (Recommended — Any OS)](#option-1-docker-compose-recommended--any-os)
  - [Option 2: Manual Local Setup (Windows, macOS, Linux)](#option-2-manual-local-setup-windows-macos-linux)
  - [Option 3: Access from Another Device on Your Local Network (LAN)](#option-3-access-from-another-device-on-your-local-network-lan)
- [Verification & Seeded Demo Accounts](#verification--seeded-demo-accounts)
- [Automated Tests](#automated-tests)
- [Troubleshooting](#troubleshooting)

---

## Core Features

- **Role-Based Access Control (RBAC)**
  - `ROLE_USER` (Complainant): Submit complaints with attachments, check potential duplicate complaints before filing, track tickets publicly or from the dashboard, post public comments, reopen resolved tickets (within the reopen window), cancel submitted tickets, and submit 1–5 star feedback.
  - `ROLE_EMPLOYEE` (Support Staff): View assigned complaint queue, start work (`IN_PROGRESS`), resolve complaints with resolution notes, and collaborate via `PUBLIC` replies or `INTERNAL` staff-only notes.
  - `ROLE_MANAGER` (Department Manager): Monitor department queues and overdue complaints, assign/reassign department staff (enforcing single active assignment per complaint via PostgreSQL partial unique index), update workflow status, reject invalid complaints, and view reports.
  - `ROLE_ADMIN` (Administrator): Manage users, roles, activation/lock status, departments, department memberships, categories, SLA policies, complaint routing, analytics reports, CSV exports, and reopen `CLOSED`/`REJECTED` complaints.
- **Security & Reliability**
  - Stateless JWT access tokens (`900s` default) + persisted hashed refresh tokens (`604800s` default) with automatic frontend refresh interceptor.
  - BCrypt password hashing, account lockout tracking (`failed_login_attempts`, `locked_until`), per-IP rate limiting (`APP_RATE_LIMIT_PER_MINUTE`), and CORS origin enforcement.
  - Optimistic locking (`@Version`) on complaints and idempotent scheduled SLA breach scanning.
- **Attachments & Audit Trail**
  - Disk-backed evidence storage (`APP_FILE_STORAGE_PATH`) validating file extensions (`pdf, png, jpg, jpeg, webp, txt, csv, doc, docx`), size limits (`10MB` per file, `15MB` max multipart request), and max `5` files per complaint.
  - Immutable `complaint_history` log capturing actor, event type, old/new statuses, reasons, and JSONB metadata.

---

## Architecture & Project Structure

```text
Project/
|-- backend/                                  # Spring Boot 3.3.5 REST API (Java 17)
|   |-- .mvn/wrapper/                         # Maven Wrapper configuration & jar
|   |-- mvnw / mvnw.cmd                       # Cross-platform Maven Wrapper (no global Maven needed)
|   |-- pom.xml                               # Maven dependencies & plugins
|   |-- Dockerfile                            # Multi-stage backend container image
|   `-- src/
|       |-- main/java/com/resolveit/
|       |   |-- ResolveItApplication.java     # Spring Boot entrypoint + scheduling
|       |   |-- auth/                         # Register, login, refresh, logout, password reset, profile
|       |   |-- user/                         # User & role administration
|       |   |-- department/                   # Departments & department member management
|       |   |-- category/                     # Complaint categories & default department routing
|       |   |-- complaint/                    # Filing, tracking, duplicate checks, status transitions
|       |   |-- assignment/                   # Staff assignment & department/overdue queues
|       |   |-- workflow/                     # Status enums & TransitionPolicy state machine
|       |   |-- comment/                      # Public comments & staff-only internal notes
|       |   |-- attachment/                   # File upload, metadata listing & download
|       |   |-- notification/                 # In-app notifications & read tracking
|       |   |-- escalation/                   # SLA calculations & scheduled overdue escalation
|       |   |-- feedback/                     # Post-resolution 1-5 star ratings & comments
|       |   |-- report/                       # Admin/Manager analytics & CSV export
|       |   |-- security/                     # JWT service, auth filter, rate limit filter, SecurityConfig
|       |   |-- config/                       # AppProperties, OpenAPI, DemoDataSeeder
|       |   `-- exception/                    # Global API error handler
|       |-- main/resources/
|       |   |-- application.yml               # App config (auto-imports .env / ../.env)
|       |   `-- db/migration/
|       |       |-- V1__init_schema.sql       # PostgreSQL schema, constraints & indexes
|       |       `-- V2__seed_data.sql         # Roles, SLA policies, default departments & categories
|       `-- test/                             # JUnit 5 + Mockito + H2 MockMvc integration tests
|-- frontend/                                 # React 18 + Vite 5 + Tailwind CSS 3 SPA
|   |-- Dockerfile                            # Multi-stage Nginx production build
|   |-- nginx.conf                            # SPA fallback & /api/ reverse proxy for Docker
|   |-- vite.config.js                        # Vite dev server (:5173) + /api proxy to :8080
|   |-- package.json                          # NPM scripts & dependencies (Axios, Router, Recharts)
|   `-- src/
|       |-- app/                              # App providers & role-guarded React Router
|       |-- context/                          # AuthContext (JWT state & session lifecycle)
|       |-- services/                         # Axios client + domain API modules
|       |-- components/                       # Layout, navigation, badges, modals
|       |-- pages/                            # Public, User, Staff, Admin & Shared views
|       `-- __tests__/                        # Vitest + React Testing Library tests
|-- postman/
|   `-- ResolveIt.postman_collection.json     # Ready-to-import Postman API collection
|-- docker-compose.yml                        # PostgreSQL 16 + Backend + Frontend stack
|-- .env.example                              # Environment variable template
`-- README.md
```

---

## Workflow State Machine & SLA Policies

Complaint status transitions are enforced by `TransitionPolicy.java`:

```text
SUBMITTED ──► UNDER_REVIEW ──► ASSIGNED ──► IN_PROGRESS ──► RESOLVED ──► CLOSED
    │              │               │             │             │
    ├──► ASSIGNED  ├──► REJECTED   └──► UNDER_REVIEW           └──► REOPENED ──► ASSIGNED / IN_PROGRESS
    ├──► REJECTED  └──► CLOSED                   │
    └──► CLOSED                                  └──► ASSIGNED (reassign)
```

- **Admin Override**: Users with `ROLE_ADMIN` can also reopen a `CLOSED` or `REJECTED` complaint back to `REOPENED`.
- **Default SLA Policies (`V2__seed_data.sql`)**:

| Priority | Target Resolution Time | Reopen Window |
| :--- | :--- | :--- |
| `CRITICAL` | 4 hours | 7 days |
| `HIGH` | 24 hours | 7 days |
| `MEDIUM` | 72 hours | 7 days |
| `LOW` | 120 hours | 7 days |

---

## Database Schema

Managed automatically on backend startup by **Flyway** (`backend/src/main/resources/db/migration/V1__init_schema.sql`):

| Table / Sequence | Purpose |
| :--- | :--- |
| `complaint_number_seq` | Sequence generating unique complaint tracking numbers |
| `roles` | Security roles (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_EMPLOYEE`) |
| `departments` | Organizational handling units |
| `users` | Accounts, BCrypt `password_hash`, lockout counters |
| `department_members` | Maps managers and employees to departments (`MANAGER` / `EMPLOYEE`) |
| `categories` | Complaint categories linked to a default department |
| `sla_policies` | Per-priority resolution targets and reopen windows |
| `complaints` | Core grievance records, status, priority, SLA timestamps, optimistic lock `version` |
| `complaint_assignments` | Assignment history with partial unique index `uq_active_assignment_per_complaint` |
| `complaint_history` | Chronological audit log with JSONB metadata |
| `comments` | `PUBLIC` citizen-visible replies and `INTERNAL` staff-only notes |
| `attachments` | Uploaded file metadata and storage keys |
| `notifications` | User alert feed and `read_at` timestamps |
| `feedback` | Unique 1–5 star complainant rating & comment per complaint |
| `refresh_tokens` | Hashed JWT refresh tokens and revocation timestamps |
| `password_reset_tokens` | Hashed password-reset tokens and expiry/usage tracking |

---

## REST API & Frontend Routes

### Backend API Groups (Base Path: `/api`)

| Module | Key Endpoints |
| :--- | :--- |
| **Auth** | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`, `GET/PUT /api/auth/me` |
| **Complaints** | `POST /api/complaints`, `GET /api/complaints`, `GET /api/complaints/potential-duplicates`, `GET /api/complaints/{id}`, `GET /api/complaints/track/{number}`, `PATCH /api/complaints/{id}`, `POST /api/complaints/{id}/{cancel\|close\|reopen\|reject\|start\|resolve\|status}`, `GET /api/complaints/{id}/history` |
| **Assignments & Queues** | `POST /api/complaints/{id}/assignments`, `POST /api/complaints/{id}/assignments/{assignmentId}/reassign`, `GET /api/complaints/{id}/assignments`, `GET /api/employee/complaints`, `GET /api/manager/department-queue`, `GET /api/manager/overdue` |
| **Comments & Attachments** | `POST/GET /api/complaints/{complaintId}/comments`, `POST/GET /api/complaints/{complaintId}/attachments`, `GET /api/attachments/{id}/download` |
| **Feedback & Notifications** | `POST/GET /api/complaints/{complaintId}/feedback`, `GET /api/notifications`, `PATCH /api/notifications/{id}/read`, `PATCH /api/notifications/read-all` |
| **Admin, Users, Catalogs & Reports** | `GET/POST/PUT /api/departments`, `GET/POST/DELETE /api/departments/{id}/members`, `GET/POST/PUT /api/categories`, `GET/POST/PUT/PATCH /api/users`, `GET /api/admin/dashboard`, `GET /api/admin/complaints`, `POST /api/admin/complaints/{id}/route`, `GET/PUT /api/admin/sla-policies`, `GET /api/reports/{summary\|by-department\|trends\|resolution-time\|export}` |
| **Docs & Health** | `GET /swagger-ui.html`, `GET /v3/api-docs`, `GET /actuator/health` |

A complete collection is provided at [ResolveIt.postman_collection.json](file:///c:/Users/gargt/Downloads/Spring_Student_Management-main/Assignment/Project/postman/ResolveIt.postman_collection.json).

### Frontend Routes (`frontend/src/app/router.jsx`)

- **Public**: `/`, `/login`, `/register`, `/track`, `/forgot-password`, `/reset-password`, `/unauthorized`
- **Authenticated / Complainant**: `/dashboard`, `/complaints`, `/complaints/new`, `/complaints/:id`, `/notifications`, `/profile`
- **Staff**: `/employee` (`ROLE_EMPLOYEE`, `ROLE_ADMIN`), `/manager` (`ROLE_MANAGER`, `ROLE_ADMIN`)
- **Admin & Reporting**: `/admin`, `/admin/departments`, `/admin/categories`, `/admin/users`, `/reports` (`ROLE_ADMIN`, `ROLE_MANAGER`)

---

## Environment Variables

`backend/src/main/resources/application.yml` automatically loads `.env` from the `backend/` folder or parent `Project/` folder (`optional:file:.env[.properties]`, `optional:file:../.env[.properties]`).

Copy `.env.example` to `.env` in `Project/` and adjust values as needed:

| Variable | Default (`application.yml`) | Purpose |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/resolveit` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `resolveit_app` | PostgreSQL username (match your local DB user, e.g., `postgres`) |
| `SPRING_DATASOURCE_PASSWORD` | `resolveit_secret` | PostgreSQL password |
| `JWT_SECRET` | Dev fallback in `application.yml` | Signing key (**must be >= 32–64 chars** in production) |
| `JWT_ACCESS_TTL_SECONDS` | `900` | Access token lifetime (15 min) |
| `JWT_REFRESH_TTL_SECONDS` | `604800` | Refresh token lifetime (7 days) |
| `APP_FRONTEND_ORIGIN` | `http://localhost:5173` | Comma-separated allowed CORS origins |
| `APP_FILE_STORAGE_PATH` | `./uploads` | Directory where attachments are saved |
| `APP_MAX_FILE_SIZE` | `10MB` | Spring multipart file limit |
| `APP_MAX_FILE_SIZE_BYTES` | `10485760` | Validator max bytes (`10 MiB`) |
| `APP_SLA_SCAN_DELAY_MS` | `60000` | Overdue SLA check interval in ms |
| `APP_MAIL_ENABLED` | `false` | Set `true` + `MAIL_*` vars to enable SMTP alerts |
| `MAIL_HOST` / `MAIL_PORT` | `""` / `587` | SMTP server host and port |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | `""` | SMTP credentials |
| `APP_RATE_LIMIT_PER_MINUTE` | `60` | Per-IP API rate limit |

---

## How to Run on Any Device

Choose **Option 1 (Docker)** if Docker is installed (fastest zero-config setup on Windows, macOS, and Linux), or **Option 2 (Manual Local Setup)** to run directly on your OS.

---

### Option 1: Docker Compose (Recommended — Any OS)

**Prerequisites:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows/macOS) or Docker Engine + Docker Compose v2 (Linux). No local Java, Maven, Node.js, or PostgreSQL installation is required.

1. Open a terminal inside the `Project/` directory.
2. *(Optional)* Copy `.env.example` to `.env` and customize `JWT_SECRET`:
   - **Windows (PowerShell):** `Copy-Item .env.example .env`
   - **macOS / Linux:** `cp .env.example .env`
3. Build and start all three containers (`postgres`, `backend`, `frontend`):
   ```bash
   docker compose up --build
   ```
   *(Run in detached/background mode with `docker compose up --build -d`.)*
4. Wait until `resolveit-backend` logs `Started ResolveItApplication`. Flyway automatically initializes PostgreSQL and seeds demo data.
5. Open the app at **`http://localhost:5173`** (Nginx serves the React app on port `5173` and proxies `/api/` to `backend:8080`).

**Useful Docker commands:**
```bash
docker compose logs -f backend    # Stream backend logs
docker compose down               # Stop containers (keeps database & upload volumes)
docker compose down -v            # Stop containers and wipe database/upload volumes
```

---

### Option 2: Manual Local Setup (Windows, macOS, Linux)

#### Prerequisites
1. **Java JDK 17 or newer** (`java -version`) — e.g., Eclipse Temurin / Oracle JDK 17+.
2. **Node.js 18+ & npm** (`node -v`, `npm -v`) — e.g., Node.js 20 LTS or 22 LTS.
3. **PostgreSQL 15+** running on port `5432` (native install or `docker run`).
4. **Maven is NOT required globally** — the included `backend/mvnw` and `backend/mvnw.cmd` scripts automatically download and use the pinned Maven version.

#### Step 1: Open the Project Directory
```bash
cd path/to/Project
```

#### Step 2: Create `.env` Configuration
Copy `.env.example` to `.env` in `Project/`:
- **Windows (PowerShell):**
  ```powershell
  Copy-Item .env.example .env
  ```
- **macOS / Linux:**
  ```bash
  cp .env.example .env
  ```

Edit `.env` so `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` match your local PostgreSQL instance (for example `postgres` and your Postgres password):
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/resolveit
SPRING_DATASOURCE_USERNAME=resolveit_app
SPRING_DATASOURCE_PASSWORD=resolveit_secret
JWT_SECRET=ChangeThisToALongRandomSecretKeyWithAtLeast64CharactersForProduction2026
APP_FRONTEND_ORIGIN=http://localhost:5173
APP_FILE_STORAGE_PATH=./uploads
APP_MAX_FILE_SIZE=10MB
APP_MAX_FILE_SIZE_BYTES=10485760
APP_MAIL_ENABLED=false
```

#### Step 3: Create the PostgreSQL Database

Ensure PostgreSQL is running on `localhost:5432`, then create the `resolveit` database (and user if using `resolveit_app`):

- **Windows (PowerShell / CMD with `psql` on PATH or in `C:\Program Files\PostgreSQL\<ver>\bin\psql.exe`):**
  ```powershell
  psql -h localhost -p 5432 -U postgres
  ```
- **macOS (Homebrew PostgreSQL):**
  ```bash
  brew services start postgresql@16
  psql postgres
  ```
- **Linux (Ubuntu / Debian):**
  ```bash
  sudo systemctl start postgresql
  sudo -u postgres psql
  ```

Inside the `psql` prompt, run (if using a dedicated app user):
```sql
CREATE USER resolveit_app WITH PASSWORD 'resolveit_secret';
CREATE DATABASE resolveit OWNER resolveit_app;
\q
```
*(Or if your `.env` uses `SPRING_DATASOURCE_USERNAME=postgres` with your `postgres` password, simply run:)*
```sql
CREATE DATABASE resolveit;
\q
```

> **Quick Alternative (PostgreSQL in Docker only):**
> ```bash
> docker run -d --name resolveit-db -e POSTGRES_DB=resolveit -e POSTGRES_USER=resolveit_app -e POSTGRES_PASSWORD=resolveit_secret -p 5432:5432 postgres:16-alpine
> ```

#### Step 4: Start the Spring Boot Backend (`:8080`)

- **Windows (PowerShell / CMD):**
  ```powershell
  cd backend
  .\mvnw.cmd spring-boot:run
  ```
- **macOS / Linux:**
  ```bash
  cd backend
  chmod +x mvnw
  ./mvnw spring-boot:run
  ```

*(Optional production JAR run instead of `spring-boot:run`:)*
```bash
./mvnw -DskipTests package          # .\mvnw.cmd -DskipTests package on Windows
java -jar target/resolveit-backend-1.0.0.jar
```

On startup, Flyway automatically executes `V1__init_schema.sql` and `V2__seed_data.sql`, and `DemoDataSeeder` creates the initial demo accounts. Wait until you see:
`Started ResolveItApplication in ... seconds`.

#### Step 5: Start the React Frontend (`:5173`)

Open a **second terminal** in `Project/frontend`:
```bash
cd frontend
npm install
npm run dev
```
Vite starts at **`http://localhost:5173`** and automatically proxies all `/api/*` requests to `http://localhost:8080`.

*(For a static production bundle, run `npm run build` and serve `frontend/dist/` via Nginx/reverse proxy.)*

---

### Option 3: Access from Another Device on Your Local Network (LAN)

To open ResolveIt from a phone, tablet, or another laptop on the same Wi-Fi/LAN:

1. Find your host machine's LAN IP (e.g., `192.168.1.50` via `ipconfig` on Windows or `ifconfig`/`ip a` on macOS/Linux).
2. Add the LAN origin to `APP_FRONTEND_ORIGIN` in `.env`:
   ```env
   APP_FRONTEND_ORIGIN=http://localhost:5173,http://192.168.1.50:5173
   ```
3. Restart the backend so it picks up `.env`.
4. Start Vite bound to all network interfaces:
   ```bash
   cd frontend
   npm run dev -- --host
   ```
5. Ensure Windows/macOS/Linux firewall allows incoming TCP on ports `5173` and `8080`, then open `http://192.168.1.50:5173` on the other device.

---

## Verification & Seeded Demo Accounts

Once both servers are running, verify these URLs:

| Service | URL | Expected Result |
| :--- | :--- | :--- |
| **Web Application** | `http://localhost:5173` | ResolveIt Landing & Portal UI |
| **Backend Health** | `http://localhost:8080/actuator/health` | `{"status":"UP"}` (HTTP 200) |
| **Swagger / OpenAPI UI** | `http://localhost:8080/swagger-ui.html` | Interactive API documentation |
| **Public Categories API** | `http://localhost:8080/api/categories` | JSON list of 6 seeded categories |

### Seeded Demo Users (`DemoDataSeeder.java`)

Seeded automatically on first boot when the `users` table is empty (`app.seed.enabled=true`):

| Role | Name | Email | Password | Department |
| :--- | :--- | :--- | :--- | :--- |
| `ROLE_ADMIN` | System Admin | `admin@resolveit.local` | `Admin@1234` | Global |
| `ROLE_MANAGER` | Rohan Mehta | `manager@resolveit.local` | `Manager@1234` | Facilities & Maintenance |
| `ROLE_MANAGER` | Neha Kapoor | `it.manager@resolveit.local` | `Manager@1234` | IT & Network Services |
| `ROLE_EMPLOYEE` | Vikram Singh | `employee@resolveit.local` | `Employee@1234` | Facilities & Maintenance |
| `ROLE_EMPLOYEE` | Sneha Nair | `sneha.staff@resolveit.local` | `Employee@1234` | Facilities & Maintenance |
| `ROLE_EMPLOYEE` | Karan Patel | `it.staff@resolveit.local` | `Employee@1234` | IT & Network Services |
| `ROLE_USER` | Aarav Sharma | `user@resolveit.local` | `User@1234` | — |
| `ROLE_USER` | Priya Verma | `priya@resolveit.local` | `User@1234` | — |

---

## Automated Tests

### Backend (`JUnit 5 + Mockito + Spring Boot MockMvc + H2`)
Tests cover workflow transition policies, SLA deadlines, authentication, password concealment, IDOR protections, cross-department assignment blocking, internal-note visibility, attachment upload validation, idempotent SLA escalation, and feedback constraints.

- **Windows:**
  ```powershell
  cd backend
  .\mvnw.cmd -B test
  ```
- **macOS / Linux:**
  ```bash
  cd backend
  ./mvnw -B test
  ```

### Frontend (`Vitest + React Testing Library + Vite Production Build`)
```bash
cd frontend
npm install
npm test
npm run build
```

---

## Troubleshooting

- **Backend fails with `database "resolveit" does not exist` or password authentication failed**
  - Create the `resolveit` database in PostgreSQL and verify `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` in `.env` (or environment variables).
- **`./mvnw: Permission denied` on macOS/Linux**
  - Run `chmod +x backend/mvnw` once before running `./mvnw`.
- **Port `8080` or `5173` already in use**
  - **Windows (PowerShell):** `Get-NetTCPConnection -LocalPort 8080,5173` to identify the PID, or change `SERVER_PORT` in `.env`.
  - **macOS / Linux:** `lsof -i :8080` or `lsof -i :5173` to inspect and stop the conflicting process.
- **CORS error in browser when accessing from another host/port**
  - Add that exact browser origin (`http://<host>:<port>`) to `APP_FRONTEND_ORIGIN` in `.env` and restart the backend.
- **`./mvnw` cannot download Maven wrapper behind an offline/corporate proxy**
  - Install Maven 3.9+ locally and run `mvn spring-boot:run`, or configure `HTTP_PROXY` / `HTTPS_PROXY` and `~/.m2/settings.xml`.
