# Documentation & Handover Plan

Reference/template: `docs/RIKA System Documentation.docx` (11-section structure).

## Documents

| Doc | Status | Purpose |
|---|---|---|
| `docs/SYSTEM_DOCUMENTATION.md` | ✅ Done | Master handover doc (RiKA 11-section template, real Tujulishane stack) |
| `docs/PARALLEL_ENVIRONMENTS.md` | ✅ Done | Test + main parallel setup, safe DB access, refresh procedure |
| `docs/TESTING_GUIDE.md` | ✅ Done | Donor / collaborator / admin / map manual test checklists |
| `.important/CONNECTION.md`, `.important/DATABASE.md` | ✅ Existing | Ops runbook: deploy, SSH, tunnel, backup, password rotation |
| `scripts/cleanup-test-data.sql` | ✅ Drafted | Removes test accounts + their data (review placeholders before running) |
| Swagger UI (`/swagger-ui.html`) | ✅ Live | API reference, no separate API doc needed |

## Remaining before handover (in order)

1. **Run test-data cleanup**: deferred (2026-07-27). Dry-run passed but the wipe was canceled because 4 of the 7 non-admin accounts look like real partners (icrhk.org, wvi.org, hennet.or.ke ×2). Decide which accounts are truly test data, adjust `scripts/cleanup-test-data.sql`, dry-run, then COMMIT. Backup from the dry-run session: `/tmp/pre_cleanup_20260727.dump` on EC2 (move it out of /tmp; it's wiped on reboot).
2. **Execute testing checklists** (donor + collaborator) in the test environment; record results in the guide.
3. **Stand up the parallel test environment** per `PARALLEL_ENVIRONMENTS.md` (~30 min on EC2) and add the `-Test` switch to `deploy.ps1`.
4. **Fix map/location search** and re-run the map smoke test.
5. **Fill in Section 9 (Credentials & Access)** of `SYSTEM_DOCUMENTATION.md` with final names; rotate all secrets on the day of handover (DB passwords, JWT secret, Gmail app password, Mapbox token).
6. **Export** `SYSTEM_DOCUMENTATION.md` to .docx/PDF if the recipient expects the RiKA-style Word format (`pandoc SYSTEM_DOCUMENTATION.md -o "Tujulishane System Documentation.docx"`).
7. **Live walkthrough** with the incoming maintainer: deploy both environments, restore a backup, review one project through the approval workflow.

## Known gaps to disclose at handover

- No backend unit/integration tests (Playwright UI tests only)
- `ddl-auto=update` in prod alongside Flyway: plan migration to `validate`
- No automated backups yet (manual dump procedure only): set up the daily cron in Section 11
- Fallback Mapbox token committed in `app-config.js`: rotate
