const { test, expect } = require('@playwright/test');

test.describe('운영 대시보드', () => {
  test('오늘 매출에서 원장으로 이동하면 오늘 조건과 동일한 순매출이 보인다', async ({ page }) => {
    await page.goto('/admin/operations-dashboard', { waitUntil: 'networkidle' });
    const salesCard = page.locator('.ops-summary a').filter({ hasText: '오늘 매출' });
    const dashboardAmount = (await salesCard.locator('strong').textContent()).trim();

    await salesCard.click();
    await expect(page).toHaveURL(/sales-ledger\?period=today/);
    await expect(page.locator('.dashboard-filter-context')).toContainText('오늘 매출');
    const start = await page.locator('#ledger-start').inputValue();
    await expect(page.locator('#ledger-end')).toHaveValue(start);
    await expect(page.locator('#ledger-net')).toHaveText(dashboardAmount);
  });

  test('재고 예외 링크는 선택한 재고 상태를 도착 화면에 반영한다', async ({ page }) => {
    await page.goto('/admin/operations-dashboard', { waitUntil: 'networkidle' });
    const soldOut = page.locator('.ops-queue-row').filter({ hasText: '품절 SKU' });
    await soldOut.click();
    await expect(page).toHaveURL(/health=SOLD_OUT/);
    await expect(page.locator('#inventory-health-filter')).toHaveValue('SOLD_OUT');
    await expect(page.locator('.dashboard-filter-context')).toBeVisible();
  });

  test('모든 대시보드 요약과 예외 링크가 도착 조건을 전달한다', async ({ page }) => {
    await page.goto('/admin/operations-dashboard', { waitUntil: 'networkidle' });
    const summaryHrefs = await page.locator('.ops-summary a').evaluateAll(links => links.map(link => link.getAttribute('href')));
    expect(summaryHrefs).toHaveLength(8);
    expect(summaryHrefs.every(href => href.includes('from=dashboard') && href.includes('focus='))).toBeTruthy();
    const queueHrefs = await page.locator('.ops-queue-row').evaluateAll(links => links.map(link => link.getAttribute('href')));
    expect(queueHrefs.length).toBeGreaterThan(0);
    expect(queueHrefs.every(href => href.includes('from=dashboard') && href.includes('focus=queue-item'))).toBeTruthy();
  });

  test('날짜 필터 프리셋은 오늘·7일·30일을 같은 방식으로 적용한다', async ({ page }) => {
    await page.goto('/admin/payment-operations/sales-ledger', { waitUntil: 'networkidle' });
    const buttons = page.locator('.filter-period-presets button');
    await expect(buttons).toHaveCount(3);
    await buttons.filter({ hasText: '오늘' }).click();
    await expect(page.locator('#ledger-end')).toHaveValue(await page.locator('#ledger-start').inputValue());
    await buttons.filter({ hasText: '7일' }).click();
    const range = await page.evaluate(() => (new Date(document.querySelector('#ledger-end').value) - new Date(document.querySelector('#ledger-start').value)) / 86400000);
    expect(range).toBe(6);
  });

  for (const viewport of [{ width: 1366, height: 768 }, { width: 1093, height: 614 }]) {
    test(`${viewport.width}x${viewport.height}에서 목록은 탭 아래 표 영역만 스크롤한다`, async ({ page }) => {
      await page.setViewportSize(viewport);
      for (const path of ['/admin/commerce/orders', '/admin/commerce/inventory', '/admin/payment-operations/sales-ledger']) {
        await page.goto(path, { waitUntil: 'networkidle' });
        const table = page.locator('.data-table-wrap').first();
        await expect(table).toBeVisible();
        const box = await table.boundingBox();
        expect(box.height).toBeGreaterThan(70);
        expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 2);
        const sectionOverflow = await page.locator('main > section').evaluate(node => getComputedStyle(node).overflowY);
        expect(sectionOverflow).toBe('hidden');
      }
    });
  }

  test('실패 로그 AI 원인 요약 패널이 플로팅 버튼으로 열리고 분석 결과를 보여준다', async ({ page }) => {
    const runtimeErrors = [];
    page.on('pageerror', error => runtimeErrors.push(error.message));
    page.on('console', message => {
      const benignSandboxNetworkBlock = message.text().includes('net::ERR_NETWORK_ACCESS_DENIED');
      if (message.type() === 'error' && !message.text().includes('favicon') && !benignSandboxNetworkBlock) {
        runtimeErrors.push(message.text());
      }
    });

    const response = await page.goto('/admin/operations-dashboard', { waitUntil: 'domcontentloaded' });
    expect(response?.status(), 'HTTP status').toBe(200);

    const fab = page.locator('#ops-insight-fab');
    const drawer = page.locator('#ops-insight-drawer');

    // 평소엔 우하단 버튼만 보이고 패널은 닫혀 있다.
    await expect(fab).toBeVisible();
    await expect(drawer).not.toHaveClass(/open/);

    await fab.click();
    await expect(drawer).toHaveClass(/open/);

    // 동작 방식 설명과 기술 태그는 실행 전에도 패널에 보인다.
    await expect(drawer.locator('.ops-insight-flow li')).toHaveCount(4);
    await expect(drawer.locator('.ops-insight-tech span').first()).toBeVisible();

    // 결과는 자동 로드가 아니라 버튼 클릭으로 실행한다.
    await expect(drawer.locator('#ops-insight-body')).toBeHidden();
    await drawer.locator('#ops-insight-run-btn').click();

    await expect(drawer.locator('.ops-insight-loading')).toHaveCount(0, { timeout: 10_000 });
    await expect(drawer.locator('.ops-insight-items, .ops-insight-empty')).toHaveCount(1);
    await expect(drawer.locator('.ops-insight-error')).toHaveCount(0);

    // provider 미설정 환경이므로 규칙 기반(MOCK) 배지
    await expect(drawer.locator('#ops-insight-conn')).toHaveText('규칙 기반 요약');
    await expect(drawer.locator('#ops-insight-run-btn')).toHaveText('다시 분석');

    // ESC 로 닫힌다.
    await page.keyboard.press('Escape');
    await expect(drawer).not.toHaveClass(/open/);

    await page.waitForLoadState('networkidle');
    expect(runtimeErrors, 'browser errors').toEqual([]);
    const viewport = page.viewportSize();
    const bodyWidth = await page.evaluate(() => document.documentElement.scrollWidth);
    expect(bodyWidth, 'horizontal overflow').toBeLessThanOrEqual(viewport.width + 2);
  });
});
