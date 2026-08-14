const { defineConfig, devices } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8000';
const isLocal = baseURL.includes('localhost') || baseURL.includes('127.0.0.1');

module.exports = defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: isLocal ? {
    command: 'npx http-server frontend -p 8000',
    url: 'http://localhost:8000',
    reuseExistingServer: true,
    timeout: 60 * 1000,
  } : undefined,
});

