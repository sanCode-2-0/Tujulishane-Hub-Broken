const { test, expect } = require('@playwright/test');

test.describe('eCitizen Redesign UI Tests', () => {

  test('should load landing page with RMNCAH branding and titles', async ({ page }) => {
    // Navigate to local server
    await page.goto('/');

    // Check title contains RMNCAH
    await expect(page).toHaveTitle(/RMNCAH Co-ordination Hub/);

    // Verify eCitizen-styled logo is visible
    const logoText = page.locator('span.tracking-tight');
    await expect(logoText).toContainText('RMNCAH');

    // Verify main simplified/unified slogans are visible
    const heroHeading = page.locator('h1');
    await expect(heroHeading).toContainText('simplified');
    await expect(heroHeading).toContainText('unified');

    // Verify search input placeholder
    const searchInput = page.locator('#homepage_search_input');
    await expect(searchInput).toHaveAttribute('placeholder', /Type name of project/);
  });

  test('should navigate to access.html on sign in click', async ({ page }) => {
    await page.goto('/');

    // Click sign in in header (ensure we select the desktop one or target the link directly)
    const signInLink = page.locator('header a[href="access.html?tab=login"]').first();
    await signInLink.click();

    // Verify we are redirected to access page
    await expect(page).toHaveURL(/access.html\?tab=login/);

    // Verify login card heading
    const loginHeading = page.locator('#loginCard h2');
    await expect(loginHeading).toContainText('Sign in to RMNCAH Hub');
  });

  test('should trigger accessibility controls and modify classes', async ({ page }) => {
    await page.goto('/access.html?tab=login');

    // Wait for the accessibility script to register
    await page.waitForTimeout(500);

    // Open accessibility dropdown by clicking accessibility button
    const accessibilityButton = page.locator('button[aria-label="Accessibility Settings"]');
    await accessibilityButton.click();

    // Click grayscale checkbox
    const grayscaleCheckbox = page.locator('#grayscale-btn');
    await expect(grayscaleCheckbox).toBeVisible();
    await grayscaleCheckbox.check();

    // Verify grayscale-active class is added to html element
    const htmlElement = page.locator('html');
    await expect(htmlElement).toHaveClass(/grayscale-active/);

    // Uncheck grayscale
    await grayscaleCheckbox.uncheck();
    await expect(htmlElement).not.toHaveClass(/grayscale-active/);

    // Click high contrast checkbox
    const highContrastCheckbox = page.locator('#high-contrast-btn');
    await highContrastCheckbox.check();

    // Verify high-contrast-active class is added
    await expect(htmlElement).toHaveClass(/high-contrast-active/);
  });

  test('should toggle step sections in Become a Member form', async ({ page }) => {
    await page.goto('/access.html?tab=register');

    // Verify Step 1 Account details is visible, Step 2 hidden
    const step1 = page.locator('#step1Content');
    const step2 = page.locator('#step2Content');
    await expect(step1).toBeVisible();
    await expect(step2).toBeHidden();

    // Fill in required Step 1 details
    await page.locator('#regName').fill('Test User');
    await page.locator('#regEmail').fill('test@user.com');

    // Click next
    const nextBtn = page.locator('#nextStepBtn');
    await nextBtn.click();

    // Verify Step 1 is hidden and Step 2 is visible
    await expect(step1).toBeHidden();
    await expect(step2).toBeVisible();

    // Click back
    const backBtn = page.locator('#prevStepBtn');
    await backBtn.click();

    // Verify Step 1 is visible again
    await expect(step1).toBeVisible();
    await expect(step2).toBeHidden();
  });
});
