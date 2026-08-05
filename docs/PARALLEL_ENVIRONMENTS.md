# Parallel Test + Main Environments

Goal: run a **test** session and the **main** (production) session side by side, with the test one seeing real data, while guaranteeing production data can never be lost or corrupted by test work.

## Design (one EC2, two backends, two databases)

```
Nginx
 ├── /            → /var/www/tujulishane-hub          (main frontend)
 ├── /api/        → localhost:8080  tujulishane        (main backend → db tujulishane_hub)
 ├── /test/       → /var/www/tujulishane-hub-test      (test frontend)
 └── /test/api/   → localhost:8081  tujulishane-test   (test backend → db tujulishane_hub_test)
```

**Why not point the test backend at the main database directly:** the backend runs with `ddl-auto=update` and Flyway, so a test build with a changed entity would silently alter the production schema, and any test write (creating projects, approving, deleting) lands in real data. A same-server database **copy** gives you the real data with zero risk, and copying is one SQL statement.

## Setup (once, on EC2)

1. **Test database** (instant snapshot of main; requires no active connections to main for a second, so run it at a quiet moment):
   ```sql
   -- as postgres:  sudo -u postgres psql
   CREATE DATABASE tujulishane_hub_test TEMPLATE tujulishane_hub OWNER tujulishane;
   ```
2. **Test backend service**: copy the unit file:
   ```bash
   sudo cp /etc/systemd/system/tujulishane.service /etc/systemd/system/tujulishane-test.service
   sudo nano /etc/systemd/system/tujulishane-test.service
   ```
   Change in the test unit:
   - `Environment="SERVER_PORT=8081"` (or `--server.port=8081` on ExecStart)
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tujulishane_hub_test`
   - JAR path → `/home/ubuntu/app-test.jar`
   - Optionally `OTP_TESTING_MODE=true` so test logins show OTPs on screen.
   ```bash
   sudo systemctl daemon-reload && sudo systemctl enable --now tujulishane-test
   ```
3. **Nginx**: add to the server block:
   ```nginx
   location /test/api/ { proxy_pass http://localhost:8081/api/; }
   location /test/    { alias /var/www/tujulishane-hub-test/; try_files $uri $uri/ /test/index.html; }
   ```
   `frontend/app-config.js` already supports `window.__BASE_URL_OVERRIDE`; the test deploy sets it to `/test` via a one-line `config.local.js` dropped into the test folder.

## Daily use

- **Deploy to test:** `.\deploy.ps1 -Test` / `.\deploy.ps1 -Test -Backend` (add a `-Test` switch to `deploy.ps1` that scp's to the test folder / `app-test.jar` and restarts `tujulishane-test`, roughly a 10-line change).
- **Refresh test data from main** (any time you want current production data in test):
  ```sql
  DROP DATABASE IF EXISTS tujulishane_hub_test;
  CREATE DATABASE tujulishane_hub_test TEMPLATE tujulishane_hub OWNER tujulishane;
  ```
  (Stop `tujulishane-test` first, restart after; its connections block the drop.)
- **Promote to main:** once tested, run the normal `.\deploy.ps1 [-Backend]`. Schema migrations reach the main DB only through this deliberate step.

## Guarantees

- Main database is only ever **read** (via TEMPLATE copy) by the test flow; no test write, schema change, or deletion can touch it.
- Both backends are separate JVMs on separate ports with separate Redis cache prefixes optional (same Redis is fine; set `spring.cache.redis.key-prefix=test:` in the test unit if cache bleed is ever an issue).
- No lag on main: the test instance adds load only while you use it; keep the EC2 instance ≥ 2 GB free RAM or size up one notch if both JVMs + Postgres get tight.

ponytail: single-EC2 twin-service setup; move test to its own instance/RDS replica only if load ever hurts prod.
