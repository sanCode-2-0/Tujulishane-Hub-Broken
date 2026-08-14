const { expect } = require('@playwright/test');

// Helper to log in a user using the local OTP testing mode
async function login(page, email) {
  console.log(`[E2E] Logging in as ${email}...`);
  
  // Navigate to login page
  await page.goto('/access.html?tab=login');

  // Fill in email
  await page.locator('#loginEmail').fill(email);

  // Set up network listener to capture the OTP from the backend API response
  const responsePromise = page.waitForResponse(response => 
    response.url().includes('/api/auth/login') && response.status() === 200
  );

  // Click Request OTP button
  await page.locator('#loginBtn').click();

  // Wait for the login request to finish
  const response = await responsePromise;
  const data = await response.json();
  
  // Extract OTP
  const otp = data.data?.otp;
  if (!otp) {
    throw new Error(`OTP not found in login response for email ${email}. Make sure OTP_TESTING_MODE=true is set on the backend.`);
  }
  console.log(`[E2E] Intercepted OTP for ${email}: ${otp}`);

  // Dismiss the custom SweetAlert-like modal
  const okBtn = page.locator('#globalAlertModalOkBtn');
  await okBtn.waitFor({ state: 'visible' });
  await okBtn.click();

  // Page redirects to otp.html, wait for it
  await page.waitForURL(/otp.html/);

  // Fill the 6 digits in otpForm inputs
  const inputs = page.locator('#otpForm input.otp-input');
  for (let i = 0; i < 6; i++) {
    await inputs.nth(i).fill(otp[i]);
  }

  // Click Verify button
  await page.locator('#verifyBtn').click();

  // Wait for redirect to post-login page
  await page.waitForURL(/index-post-login.html/);
  console.log(`[E2E] Successfully logged in as ${email}`);
}

module.exports = { login };
