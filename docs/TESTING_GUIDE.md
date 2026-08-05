# Testing Guide: Role-Based Manual Test Checklists

Run these in the **test environment** (`/test/`, OTP testing mode on); see `docs/PARALLEL_ENVIRONMENTS.md`. Automated UI tests: `npm test` (Playwright, `tests/ui-tests.spec.js`).

## A. Donor account test

Setup: register a fresh account with a throwaway email, organization type **Donor Agency**, role **DONOR**.

- [ ] Registration with supporting documents uploads and submits
- [ ] OTP email received (or shown on screen in testing mode); login succeeds; JWT persists across pages
- [ ] Pending-approval state: donor sees correct limited view before admin approves the organization
- [ ] After approval: dashboard loads donor-specific view (`donor-management.html`)
- [ ] Can browse approved projects and the public map; unapproved projects are NOT visible
- [ ] Can view project details, locations on map, reports
- [ ] Can send/receive collaboration requests where permitted; cannot access admin pages (approvals, review-requests); direct URL entry must redirect/deny
- [ ] Announcements visible; messages send/receive
- [ ] Profile edit + document re-upload works
- [ ] Logout clears session; back-button does not restore authenticated pages

## B. Collaborator (partner) account test

Setup: register a **PARTNER** account in a second organization; have an existing project owned by another org.

- [ ] Registration → OTP → login as above
- [ ] Create new project: all form steps, multiple locations via map pin + address, themes, documents
- [ ] Location search on the new-project map finds Kenyan places and drops the pin correctly
- [ ] Submitted project enters review workflow (PENDING → thematic reviewer → final approver); hidden from public until APPROVED
- [ ] Request collaboration on another org's project; owner sees and can accept/reject
- [ ] Accepted collaborator appears in project collaborators with correct `CollaboratorRole`; gains agreed access (edit/report per role), nothing more
- [ ] Rejected/withdrawn requests reflect correct status both sides
- [ ] Project reports: create, upload documents, edit
- [ ] Past projects: create and view
- [ ] Cannot approve own project or see other orgs' drafts

## C. Admin workflow (regression, run after A/B)

- [ ] SUPER_ADMIN_REVIEWER sees only projects in their thematic areas; review actions advance workflow
- [ ] SUPER_ADMIN_APPROVER final approval publishes project to public map
- [ ] Organization/user approval flows work; rejected users handled cleanly

## D. Map / location smoke test (all roles + guest)

- [ ] Public map (guest) loads, markers cluster/plot at real coordinates (not stacked at Kenya-center fallback; stacked markers mean geocoding failed server-side)
- [ ] Location search returns Kenya-biased results and flies to the selection
- [ ] Reverse geocoding fills address when a pin is dropped
- [ ] Works on the deployed site (Mapbox token valid for the domain), not just localhost

Record results per release in a copy of this file or a shared sheet; any failure blocks promotion from test → main.
