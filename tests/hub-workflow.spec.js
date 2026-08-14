const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('Tujulishane Hub End-to-End Workflow', () => {
  const projectTitle = `E2E Test Project ${Date.now()}`;

  test('should complete the end-to-end Partner -> Reviewer -> Approver -> Donor lifecycle', async ({ page }) => {
    test.setTimeout(120000); // 2 minutes timeout for the whole workflow

    // Capture browser console logs for diagnostics
    page.on('console', msg => {
      console.log(`[Browser] ${msg.type().toUpperCase()}: ${msg.text()}`);
    });

    // ==========================================
    // 1. PARTNER: Create and Submit a Project
    // ==========================================
    await login(page, 'braine.kapolon@strathmore.edu');

    // Go to project creation form
    await page.goto('/new-project.html');

    // STEP 1: Project Location
    // We mock the Mapbox locations selection by evaluating JavaScript directly to bypass map rendering flakes
    console.log('[E2E] Mocking Mapbox location selection in Step 1...');
    await page.evaluate(() => {
      if (window.selectedLocations) {
        window.selectedLocations.push({
          lat: -1.2841,
          lng: 36.7623,
          county: "Nairobi",
          subCounty: "Dagoretti North",
          name: "Dagoretti Area, Nairobi"
        });
        window.updateLocationsDisplay();
        window.updateFormData();
      }
    });

    // Go to Step 2
    await page.locator('#nextBtn').click();

    // STEP 2: Basic Information
    console.log('[E2E] Filling Step 2: Basic Info...');
    await page.locator('#project_category').selectOption('IMPLEMENTING');
    await page.locator('#title').fill(projectTitle);
    await page.locator('#starttime_period').fill('2026-08-01');
    await page.locator('#endtime_period').fill('2026-12-31');
    await page.locator('#activity_type').fill('Conduct E2E testing and validation for Tujulishane Hub web application');

    // Go to Step 3
    await page.locator('#nextBtn').click();

    // STEP 3: Project Themes
    console.log('[E2E] Filling Step 3: Project Themes...');
    await page.locator('input[name="themes"][value="MNH"]').check();

    // Go to Step 4
    await page.locator('#nextBtn').click();

    // STEP 4: Contact Information
    console.log('[E2E] Filling Step 4: Contact Info...');
    await page.locator('#contact_person_name').fill('E2E Tester');
    await page.locator('#contact_person_role').fill('QA Specialist');
    await page.locator('#contact_person_email').fill('tester@test.com');

    // Go to Step 5
    await page.locator('#nextBtn').click();

    // STEP 5: Budget & Objectives
    console.log('[E2E] Filling Step 5: Budget & Objectives...');
    await page.locator('#currency').selectOption('KES');
    await page.locator('#budget').fill('5000000');
    await page.locator('#objectives').fill('Verify that the system can process new projects and successfully display them to partners and admins.');

    // Go to Step 6 (Supporting Documents)
    await page.locator('#nextBtn').click();

    // STEP 6: Submit project
    console.log('[E2E] Submitting project...');
    // Intercept project submission API call
    const projectSubmitPromise = page.waitForResponse(response => 
      response.url().includes('/api/projects') && response.request().method() === 'POST'
    );
    await page.locator('#nextBtn').click();

    const submitResponse = await projectSubmitPromise;
    expect([200, 201]).toContain(submitResponse.status());
    console.log('[E2E] Project submitted successfully!');

    // Dismiss the project submission success modal
    const okBtn = page.locator('#modalCloseBtn');
    await okBtn.waitFor({ state: 'visible' });
    await okBtn.click();

    // Wait for redirect to post-login index page
    await page.waitForURL(/index-post-login.html/);

    // Logout Partner
    await page.evaluate(() => window.authManager.logout());
    await page.waitForURL(/index.html/);

    // ==========================================
    // 2. REVIEWER: Review the Pending Project
    // ==========================================
    await login(page, 'lomogantech@gmail.com');

    // Navigate to reviews page
    await page.goto('/review-requests.html');

    // Find the project card by title and open the review modal
    console.log(`[E2E] Locating project card for review: "${projectTitle}"...`);
    const projectCardForReview = page.locator('div.border.rounded-lg', { has: page.locator('h3', { hasText: projectTitle }) });
    await expect(projectCardForReview).toBeVisible();

    await projectCardForReview.locator('button:has-text("Review Project")').click();

    // Fill in review details (Approve)
    console.log('[E2E] Submitting reviewer decision...');
    await page.locator('input[type="radio"][value="approve"]').check();
    await page.locator('textarea[placeholder="Provide detailed feedback..."]').fill('Recommended for approval by E2E testing framework.');

    // Submit review
    const reviewResponsePromise = page.waitForResponse(response =>
      response.url().includes('/api/projects/admin/review/') && response.request().method() === 'POST'
    );
    await page.locator('button:has-text("Submit Review")').click();
    await reviewResponsePromise;
    console.log('[E2E] Review submitted!');

    // Logout Reviewer
    await page.evaluate(() => window.authManager.logout());
    await page.waitForURL(/index.html/);

    // ==========================================
    // 3. APPROVER: Grant Final Approval
    // ==========================================
    await login(page, 'kapolonbraine@gmail.com');

    // Navigate to reviews/approvals page
    await page.goto('/review-requests.html');

    // Switch to "Approved Projects" tab (projects approved by reviewer)
    console.log('[E2E] Switching to Approved Projects tab...');
    await page.locator('button:has-text("Approved Projects")').click();

    // Find the project card and open the final approval modal
    console.log(`[E2E] Locating project card for final approval: "${projectTitle}"...`);
    const projectCardForApproval = page.locator('div.border.rounded-lg', { has: page.locator('h3', { hasText: projectTitle }) });
    await expect(projectCardForApproval).toBeVisible();

    await projectCardForApproval.locator('button:has-text("Final Approve")').click();

    // Fill final approval comments
    await page.locator('textarea[placeholder="Add any final comments..."]').fill('Final approval granted by E2E testing framework.');

    // Grant final approval
    const approvalResponsePromise = page.waitForResponse(response =>
      response.url().includes('/api/projects/admin/final-approve/') && response.request().method() === 'POST'
    );
    await page.locator('button:has-text("Grant Final Approval")').click();
    await approvalResponsePromise;
    console.log('[E2E] Final approval granted!');

    // Logout Approver
    await page.evaluate(() => window.authManager.logout());
    await page.waitForURL(/index.html/);

    // ==========================================
    // 4. PARTNER: Verify Project Status is Active
    // ==========================================
    await login(page, 'braine.kapolon@strathmore.edu');

    // Navigate to my-projects list
    await page.goto('/my-projects.html');

    // Verify the project is shown with status APPROVED or active
    console.log(`[E2E] Verifying status for project "${projectTitle}"...`);
    const finalProjectRow = page.locator('tr', { has: page.locator('span', { hasText: projectTitle }) });
    await expect(finalProjectRow).toBeVisible();

    const statusBadge = finalProjectRow.locator('td').nth(6); // Workflow status column
    await expect(statusBadge).toContainText(/APPROVED|active/i);
    console.log('[E2E] Project is confirmed active/approved!');

    // Logout Partner
    await page.evaluate(() => window.authManager.logout());
    await page.waitForURL(/index.html/);

    // ==========================================
    // 5. DONOR: Verify Visibility (Access Control)
    // ==========================================
    await login(page, 'donor.test@gmail.com');

    await page.goto('/my-projects.html');
    console.log(`[E2E] Verifying donor cannot see Strathmore project "${projectTitle}"...`);
    
    // Assert the project is NOT visible in the donor's projects list
    const donorProjectCard = page.locator('tr', { has: page.locator('span', { hasText: projectTitle }) });
    await expect(donorProjectCard).not.toBeVisible();
    console.log('[E2E] Access control verified: Donor cannot see unlinked partner projects.');

    // Logout Donor
    await page.evaluate(() => window.authManager.logout());
    await page.waitForURL(/index.html/);
  });
});
