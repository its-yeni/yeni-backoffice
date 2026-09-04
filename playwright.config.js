const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  outputDir: './build/playwright-results',
  globalSetup: require.resolve('./e2e/global-setup'),
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'build/playwright-report', open: 'never' }]
  ],
  use: {
    baseURL: 'http://127.0.0.1:8081',
    channel: 'msedge',
    viewport: { width: 1366, height: 768 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off'
  }
});
