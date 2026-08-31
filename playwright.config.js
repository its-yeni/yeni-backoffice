const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  outputDir: './build/playwright-results',
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
  },
  webServer: {
    command: '.\\gradlew.bat -g .gradle-user -Dspring.devtools.restart.enabled=false :api:bootRun --args="--server.port=8081 --spring.datasource.url=jdbc:h2:mem:yeni-e2e --spring.jpa.hibernate.ddl-auto=create-drop --spring.devtools.restart.enabled=false"',
    url: 'http://127.0.0.1:8081/actuator/health',
    timeout: 180_000,
    reuseExistingServer: false,
    env: {
      ...process.env,
      JAVA_TOOL_OPTIONS: `${process.env.JAVA_TOOL_OPTIONS || ''} -Dspring.devtools.restart.enabled=false`.trim()
    },
    stdout: 'ignore',
    stderr: 'pipe'
  }
});
