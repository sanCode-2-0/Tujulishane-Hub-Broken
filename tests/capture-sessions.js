const { chromium } = require('@playwright/test');
const readline = require('readline');
const fs = require('fs');
const path = require('path');

const AUTH_DIR = path.join(__dirname, 'auth');
const BASE_URL = 'https://cohub.go.ke';

const ROLES = {
  1: { name: 'partner', desc: 'Partner User (Project owner/creator)' },
  2: { name: 'reviewer', desc: 'MOH Reviewer User (Thematic reviewer)' },
  3: { name: 'approver', desc: 'MOH Approver User (Final approval authority)' },
  4: { name: 'donor', desc: 'Donor User' }
};

// Create authentication directory if it does not exist
if (!fs.existsSync(AUTH_DIR)) {
  fs.mkdirSync(AUTH_DIR, { recursive: true });
}

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

function askQuestion(query) {
  return new Promise((resolve) => rl.question(query, resolve));
}

async function captureRoleSession(roleKey) {
  const role = ROLES[roleKey];
  console.log(`\n==================================================`);
  console.log(`Capturing session for: ${role.desc.toUpperCase()}`);
  console.log(`==================================================`);
  console.log(`Starting headed browser...`);

  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext();
  const page = await context.newPage();

  // Navigate to login page
  await page.goto(`${BASE_URL}/access.html?tab=login`);

  console.log(`\n👉 ACTION REQUIRED:`);
  console.log(`1. In the opened browser window, log in as the ${role.desc}.`);
  console.log(`2. Enter the OTP code sent to your email/phone.`);
  console.log(`3. Complete the login process until you see the post-login dashboard.`);
  console.log(`4. Once fully logged in, return to this terminal.`);

  await askQuestion('\nPress [Enter] key in this terminal once you have successfully logged in to save the session...');

  // Save storage state (cookies, local storage, etc.)
  const statePath = path.join(AUTH_DIR, `${role.name}.json`);
  await context.storageState({ path: statePath });
  
  console.log(`\n✅ Session saved successfully to: ${statePath}`);
  
  await browser.close();
}

async function main() {
  console.log('Tujulishane Hub - Production Session Capture Tool');
  console.log('Use this tool to save authenticated storage states for E2E tests against cohub.go.ke\n');
  
  console.log('Available Roles:');
  for (const [key, role] of Object.entries(ROLES)) {
    console.log(`  [${key}] ${role.desc}`);
  }
  console.log(`  [5] Capture All Roles (sequential)`);
  console.log(`  [0] Exit`);

  const choice = await askQuestion('\nSelect a role to capture (0-5): ');
  
  if (choice === '0') {
    rl.close();
    process.exit(0);
  }

  if (choice === '5') {
    for (const key of Object.keys(ROLES)) {
      await captureRoleSession(key);
    }
  } else if (ROLES[choice]) {
    await captureRoleSession(choice);
  } else {
    console.log('Invalid choice.');
  }

  rl.close();
  console.log('\nSession capture complete. You can now run production tests using these storage states!');
}

main().catch((err) => {
  console.error('Error running session capture:', err);
  rl.close();
  process.exit(1);
});
