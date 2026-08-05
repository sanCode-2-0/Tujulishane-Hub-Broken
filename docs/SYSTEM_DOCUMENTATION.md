# Tujulishane Hub: System Documentation

**July 2026** · Modeled on the RiKA System Documentation template.

## Table of Contents
1. Architecture Overview
2. Stack Documentation
3. Deployment Guide
4. Security Overview
5. API Documentation
6. Database Schema
7. Testing Report
8. Infrastructure Diagram
9. Credentials & Access Handover
10. Monitoring & Logging Setup
11. Backup & Recovery Plan

---

## 1. Architecture Overview

**System purpose:** A Ministry of Health partner-coordination platform for RMNCAH projects in Kenya. Partners and donors register organizations, submit projects with mapped locations, request collaborations, and go through a two-tier MOH approval workflow (thematic reviewers → final approver). Public visitors see approved projects on an interactive map.

**Key components:**
1. Static HTML/CSS/JS frontend (no framework, no build step) served by Nginx on EC2
2. Spring Boot 3.3 (Java 21) REST backend on the same EC2 instance (systemd service `tujulishane`)
3. PostgreSQL database (self-hosted on EC2)
4. Redis (caching: OTPs, geocoding results)
5. Mapbox GL JS + Mapbox Geocoding API (maps, location search)
6. OpenStreetMap Nominatim (server-side geocoding fallback)
7. Gmail SMTP (OTP and notification email)

**Architecture style:** Monolith, single Spring Boot JAR behind Nginx; frontend is static files proxied on the same host (`/api/` → localhost:8080).

**Main data flow:** Browser loads static pages from Nginx → pages call `/api/**` (JWT bearer auth, obtained via email OTP) → Spring Boot reads/writes PostgreSQL, caches in Redis → map pages call Mapbox APIs directly from the browser.

**External integrations:** Mapbox (maps + geocoding), Nominatim (geocoding), Gmail SMTP, GitHub (source control).

**Access points:**
- Frontend + API: EC2 at 44.245.151.32 (Nginx, `/var/www/tujulishane-hub`)
- API base path: `/api` (Swagger UI at `/swagger-ui.html` via springdoc)
- Fallback deploy target: Render (`tujulishane-hub-backend.onrender.com`), see `frontend/app-config.js`

## 2. Stack Documentation

- **Frontend:** Plain HTML5/CSS/JS (multi-page, ~1 file per screen in `frontend/`), Mapbox GL JS, `app-config.js` for environment detection (localhost → dev backend, otherwise prod), `config.local.js` (gitignored) for local secrets like the Mapbox token.
- **Backend:** Java 21, Spring Boot 3.3.0 (Web, Data JPA, Security, Mail, Data Redis, Validation), JJWT 0.11.5 (HS512 JWT), Lombok, springdoc-openapi 2.5.0, Flyway 11 (PostgreSQL migrations in `backend/src/main/resources/db/migration`).
- **Database:** PostgreSQL (prod, database `tujulishane_hub`, user `tujulishane`); H2 in-memory (dev profile).
- **Cache:** Redis (prod: `spring.cache.type=redis`, TTL 10m).
- **Hosting:** Single AWS EC2 (Ubuntu) running Nginx reverse proxy + static frontend + Spring Boot + PostgreSQL + Redis. Procfile/Heroku task and Render URL exist as legacy/backup deploy paths.
- **DevOps:** Git + GitHub; `deploy.ps1` (scp + ssh) for deployment; Gradle build; Playwright (`tests/ui-tests.spec.js`) for UI tests.

## 3. Deployment Guide

**Environments:**
1. **Development:** run backend locally (`cd backend; .\gradlew.bat bootRun`, dev profile, H2, OTP testing mode); serve frontend with `npm run serve` (http-server on port 8000). `app-config.js` auto-targets `http://localhost:8080` on localhost.
2. **Production:** EC2. Frontend at `/var/www/tujulishane-hub`, backend JAR at `/home/ubuntu/app.jar` run by systemd unit `tujulishane` with env vars (DB credentials, JWT secret, mail credentials) in `/etc/systemd/system/tujulishane.service`.
3. **Test (parallel):** see `docs/PARALLEL_ENVIRONMENTS.md`.

**Build & deploy commands (local PowerShell):**
```powershell
.\deploy.ps1            # frontend only (scp static files, flips USE_PROD)
.\deploy.ps1 -Backend   # gradle bootJar → scp app.jar → restart service
.\deploy.ps1 -Restart   # restart backend service only
```

**Required backend environment variables (systemd unit):**
`SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `REDIS_HOST`/`REDIS_PORT`, `APP_EMAIL_ENABLED`, `OTP_TESTING_MODE` (must be `false` in prod).

**Rollback:** redeploy previous JAR (keep the last known-good `app.jar` on the server as `app.jar.bak`); frontend is stateless, so redeploy previous git commit. Database: restore from dump (Section 11).

## 4. Security Overview

- **Authentication:** Passwordless email OTP → JWT bearer token (HS512; secret hashed with SHA-512 if shorter than key requirement). `JwtRequestFilter` validates on every request.
- **Authorization:** RBAC via `User.Role`: `SUPER_ADMIN` (legacy full admin), `SUPER_ADMIN_APPROVER` (final approval), `SUPER_ADMIN_REVIEWER` (thematic-area review), `DONOR`, `PARTNER`. Organization types include `DONOR_AGENCY`. Enforced in `SecurityConfig` + service-layer checks.
- **Transport:** PostgreSQL is not exposed publicly; access only via SSH tunnel (`.important/DATABASE.md`). SSH key auth to EC2.
- **Secrets:** environment variables in the systemd unit and gitignored `config.local.js`; never committed. Note: a default Mapbox token is currently embedded in `app-config.js`, rotate it and treat it as public.
- **Approval workflow:** unapproved projects hidden from public endpoints (`ApprovalWorkflowStatus`).

## 5. API Documentation

Base URL: `http://<host>/api`. Full, live API reference at `/swagger-ui.html` (springdoc). Auth: `Authorization: Bearer <jwt>`.

Controller map:
- `UserController`: registration, OTP request/verify, login, profile, user documents, admin user management
- `OrganizationController`: organization CRUD + approval
- `ProjectController`: project CRUD, locations, themes, approval workflow, public map data, thematic areas (`/api/projects/thematic-areas` is the health-check endpoint)
- `PastProjectController`, `ProjectReportController`: historical projects and reports
- `CollaborationRequestController`, `ProjectCollaboratorController`: collaboration requests and membership
- `AnnouncementController`, `GeneralAnnouncementController`: announcements/messaging

## 6. Database Schema

PostgreSQL, schema managed by Flyway (`V1__Initial_Schema.sql` + subsequent migrations); `ddl-auto=update` also active in prod (see Known Issues).

Core tables: `users`, `organizations`, `projects`, `project_locations`, `project_collaborators`, `project_theme_assignments`, `project_documents`, `user_documents`, `past_projects`, `project_reports`, `project_report_document`, `collaboration_requests`, `announcements`, `general_announcements`, `messages`, `reviewer_thematic_areas`.

Key relationships:
- `users` → `organizations`: many-to-one
- `projects` → `organizations`: many-to-one (owner); `project_collaborators` joins users/orgs to projects with a `CollaboratorRole`
- `projects` → `project_locations`: one-to-many (each with `maps_address`, lat/lng)
- `projects` ↔ themes via `project_theme_assignments`; reviewers scoped via `reviewer_thematic_areas`
- `project_reports` → `projects`; documents tables hold uploaded files per parent entity

## 7. Testing Report

- **UI/E2E:** Playwright (`tests/ui-tests.spec.js`, `playwright.config.js`); run with `npm test`. Results in `test-results/`.
- **Backend:** spring-boot-starter-test configured; no meaningful unit/integration suite yet (gap, see handover plan).
- **Manual testing:** role-based checklists in `docs/TESTING_GUIDE.md` (donor, collaborator/partner, admin flows).
- **OTP testing mode:** `OTP_TESTING_MODE=true` displays OTP codes on the frontend for test environments. Must remain `false` in production.
- **Known issues:** `spring.jpa.hibernate.ddl-auto=update` in prod alongside Flyway (schema drift risk, switch to `validate` once migrations are complete); embedded fallback Mapbox token; no backend test coverage.

## 8. Infrastructure Diagram

```
Internet
   ↓ HTTP(S)
Nginx (EC2, 44.245.151.32)
   ├── /            → /var/www/tujulishane-hub  (static frontend)
   └── /api/        → localhost:8080  Spring Boot (systemd: tujulishane)
                          ├── PostgreSQL (localhost:5432, db tujulishane_hub)
                          ├── Redis (localhost:6379)
                          ├── Gmail SMTP (outbound, 587)
                          └── Nominatim API (outbound geocoding)
Browser ──→ Mapbox APIs (tiles + geocoding, direct)
```

## 9. Credentials & Access Handover

Fill in names/emails before handover; never write actual secrets here.

| Asset | Where | Who has access |
|---|---|---|
| AWS account / EC2 | AWS console; SSH as `ubuntu`/`briane` with key `~/.ssh/id_rsa` | Braine Lomoni (lead) |
| GitHub repository | github.com, Tujulishane-Hub | Braine Lomoni |
| PostgreSQL | EC2-local only; SSH tunnel per `.important/DATABASE.md`; users `postgres`, `tujulishane` | Braine Lomoni |
| JWT secret, mail credentials | `/etc/systemd/system/tujulishane.service` on EC2 | Braine Lomoni |
| Mapbox account/token | mapbox.com (`lomogantech`) | Braine Lomoni |
| Gmail SMTP sender | Google account + app password | Braine Lomoni |
| Domain/DNS (when assigned) | TBD | TBD |

**Handover steps:** grant GitHub access → create IAM user / share EC2 key securely (never by email/chat in plain text) → add recipient's SSH key to EC2 → rotate DB passwords, JWT secret, mail app-password, Mapbox token → walk through `.important/CONNECTION.md` + `deploy.ps1` live → transfer Mapbox and SMTP account ownership.

## 10. Monitoring & Logging Setup

- **Backend logs:** `journalctl -u tujulishane -f` on EC2 (Spring Boot stdout; prod log level INFO/WARN).
- **Nginx logs:** `/var/log/nginx/access.log`, `error.log`.
- **Health check:** `GET /api/projects/thematic-areas` (public). Suggested: uptime monitor (UptimeRobot free tier) pointed at it.
- **Gap:** no metrics/alerting stack (Prometheus/Grafana per RiKA is optional at this scale; an uptime ping + disk-space cron alert covers the real risks).

## 11. Backup & Recovery Plan

Documented in detail in `.important/DATABASE.md`. Summary:

- **Backup:** `pg_dump -F c` on EC2 → scp to local machine. Recommended: daily cron on EC2 dumping to `/home/briane/backups` with 7-day rotation, plus weekly off-server copy (S3 or local download).
- **Restore:** `pg_restore -d tujulishane_hub --clean` as `postgres`.
- **Test-database refresh:** `CREATE DATABASE tujulishane_hub_test TEMPLATE tujulishane_hub;` (see `docs/PARALLEL_ENVIRONMENTS.md`).
- **Recovery order:** restore DB → restart `tujulishane` service → verify health endpoint → verify login + map.
