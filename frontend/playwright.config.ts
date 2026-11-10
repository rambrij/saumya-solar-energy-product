import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  reporter: 'list',
  timeout: 30_000,
  use: {
    baseURL: process.env.E2E_API_BASE_URL || 'http://127.0.0.1:8080',
    browserName: 'chromium',
    headless: true,
  },
});
