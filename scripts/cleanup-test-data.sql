-- Wipe ALL data except admin accounts (SUPER_ADMIN / SUPER_ADMIN_APPROVER / SUPER_ADMIN_REVIEWER)
-- from tujulishane_hub, per pre-launch reset decision (2026-07-27).
--
-- BEFORE RUNNING:
--   1. Backup: sudo -u postgres pg_dump -F c -f /tmp/pre_cleanup.dump tujulishane_hub
--   2. Run as-is (ends with ROLLBACK) and review the SELECT output.
--   3. Change ROLLBACK to COMMIT and run again.

BEGIN;

-- Who survives
SELECT id, email, role FROM users WHERE role LIKE 'SUPER_ADMIN%';
-- Who gets deleted (review!)
SELECT id, email, role FROM users WHERE role NOT LIKE 'SUPER_ADMIN%';

-- All project-related data goes (admins own no launch content)
TRUNCATE TABLE
    project_report_document,
    project_reports,
    project_document,
    project_locations,
    project_theme_assignments,
    project_collaborators,
    collaboration_requests,
    projects,
    past_projects,
    announcements,
    general_announcements,
    messages
    CASCADE;

-- Non-admin users and their documents
DELETE FROM user_documents
 WHERE user_id IN (SELECT id FROM users WHERE role NOT LIKE 'SUPER_ADMIN%');
DELETE FROM users WHERE role NOT LIKE 'SUPER_ADMIN%';

-- Organizations with no remaining users
DELETE FROM organizations o
 WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.organization_id = o.id);

-- Final state
SELECT (SELECT count(*) FROM users)         AS users_left,
       (SELECT count(*) FROM organizations) AS orgs_left,
       (SELECT count(*) FROM projects)      AS projects_left;

-- Dry run by default. Flip to COMMIT when the output above looks right.
ROLLBACK;
