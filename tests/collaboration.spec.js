const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('Tujulishane Hub Collaboration Lifecycle', () => {
  const opportunityTitle = `E2E Collaboration Opportunity ${Date.now()}`;

  test.beforeEach(async ({ page }) => {
    // Log all browser console messages
    page.on('console', msg => {
      console.log(`[Browser] ${msg.type().toUpperCase()}: ${msg.text()}`);
    });
  });

  test('should complete the collaboration lifecycle: post opportunity -> request -> approve', async ({ page }) => {
    // ==========================================
    // 1. PARTNER 1: Post a Collaboration Opportunity
    // ==========================================
    // Login as Braine Strathmore (Partner 1)
    await login(page, 'braine.kapolon@strathmore.edu');

    // Go to announcements page
    await page.goto('/announcements.html');

    // Click "Post New Opportunity"
    await page.locator('button:has-text("Post New Opportunity")').click();

    // Fill in opportunity form
    // Select the first valid project in the dropdown (index 0 is select placeholder)
    await page.locator('select[x-model="newAnnouncement.projectId"]').selectOption({ index: 1 });
    await page.locator('input[x-model="newAnnouncement.title"]').fill(opportunityTitle);
    await page.locator('textarea[x-model="newAnnouncement.content"]').fill('We are looking for partner organizations to collaborate on family outreach programs and maternal health campaigns.');
    await page.locator('input[x-model="newAnnouncement.deadline"]').fill('2026-12-31');

    // Intercept announcement post response
    const createResponsePromise = page.waitForResponse(response =>
      response.url().includes('/api/announcements') && response.request().method() === 'POST'
    );

    // Submit the opportunity form
    await page.locator('button[type="submit"]:has-text("Create Announcement")').click();
    await createResponsePromise;
    console.log('[E2E] Collaboration Opportunity posted successfully!');

    // Dismiss the success modal
    const okBtn = page.locator('#globalAlertModalOkBtn');
    await okBtn.waitFor({ state: 'visible' });
    await okBtn.click();

    // Verify it is listed in the opportunities feed
    const opportunityCard = page.locator('h3', { hasText: opportunityTitle });
    await expect(opportunityCard).toBeVisible();

    // Logout Partner 1
    await page.evaluate(() => window.authManager.logout());
    await page.waitForURL(/index.html/);

    // ==========================================
    // 2. PARTNER 2: Request Collaboration
    // ==========================================
    // Login as Donor Linked Partner (Partner 2)
    await login(page, 'partner.donorlinked@gmail.com');

    // Go to announcements page
    await page.goto('/announcements.html');

    // Find the opportunity card and click "Request" button directly on it
    console.log(`[E2E] Locating opportunity card for: "${opportunityTitle}"...`);
    const oppCardForRequest = page.locator('div.bg-white', { has: page.locator('h3', { hasText: opportunityTitle }) }).first();
    await expect(oppCardForRequest).toBeVisible();
    await oppCardForRequest.locator('button:has-text("Request")').click();

    // Fill in request form
    await page.locator('textarea[x-model="collaborationRequest.proposedContribution"]').fill('We can provide community mobilizers, transport support, and translation services for family outreach.');

    // Intercept request post response
    const requestResponsePromise = page.waitForResponse(response =>
      response.url().includes('/api/collaboration-requests/announcements/') && response.request().method() === 'POST'
    );

    // Submit Request
    await page.locator('button[type="submit"]:has-text("Submit Request")').click();
    await requestResponsePromise;
    console.log('[E2E] Collaboration request submitted successfully!');

    // Dismiss success modal
    await okBtn.waitFor({ state: 'visible' });
    await okBtn.click();

    // Logout Partner 2
    await page.evaluate(() => window.authManager.logout());
    await page.waitForURL(/index.html/);

    // ==========================================
    // 3. ADMIN/REVIEWER: Approve Collaboration Request
    // ==========================================
    // Login as Lomogan Reviewer (Admin/Reviewer)
    await login(page, 'lomogantech@gmail.com');

    // Go to collaboration requests page
    await page.goto('/Forms/Admin/collaboration-requests.html');

    // Find the request card
    console.log(`[E2E] Locating collaboration request for: "${opportunityTitle}"...`);
    const requestCard = page.locator('div.bg-white', { has: page.locator('p', { hasText: opportunityTitle }) }).first();
    await expect(requestCard).toBeVisible();

    // Click Approve button
    await requestCard.locator('button:has-text("Approve & Add as")').click();

    // Intercept approval response
    const approveResponsePromise = page.waitForResponse(response =>
      response.url().includes('/api/collaboration-requests/admin/') && response.url().endsWith('/approve') && response.request().method() === 'POST'
    );

    // Confirm approval in modal
    await page.locator('button:has-text("Approve & Add Collaborator")').click();
    await approveResponsePromise;
    console.log('[E2E] Collaboration request approved by admin!');

    // Dismiss success modal
    await okBtn.waitFor({ state: 'visible' });
    await okBtn.click();

    // Logout Admin
    await page.evaluate(() => window.authManager.logout());
    await page.waitForURL(/index.html/);
  });
});
