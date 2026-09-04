const { test, expect } = require('@playwright/test');

test.describe('운영 화면 품질 보완', () => {
  test('주문 목록은 Mock 상품·옵션 요청을 기다리지 않고 먼저 표시된다', async ({ page }) => {
    let mockProductRequests = 0;
    await page.route('**/admin/api/commerce/products**', async route => {
      mockProductRequests += 1;
      await new Promise(resolve => setTimeout(resolve, 3000));
      await route.continue();
    });
    await page.goto('/admin/commerce/orders', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#orderRows tr').first()).toBeVisible({ timeout: 5000 });
    expect(mockProductRequests).toBe(0);
    await page.getByRole('button', { name: 'Mock 주문 생성' }).click();
    await expect.poll(() => mockProductRequests).toBeGreaterThan(0);
  });

  test('매출·원장·정산 화면에 집계 기준과 조회 시각이 표시된다', async ({ page }) => {
    for (const [path, label] of [
      ['/admin/operations-dashboard', '매출 원장 기준'],
      ['/admin/payment-operations/sales-ledger', '불변 원장 기준'],
      ['/admin/payment-operations/sales-analytics', '매출 명세 기준'],
      ['/admin/payment-operations/settlements', '정산서 기준'],
    ]) {
      await page.goto(path, { waitUntil: 'networkidle' });
      const basis = page.locator('.aggregation-basis');
      await expect(basis).toContainText(label);
      await expect(basis.locator('time')).toContainText('조회');
    }
  });

  test('150% 확대 대응 크기에서도 목록 표가 화면 안에 유지된다', async ({ page }) => {
    const viewport = { width: 911, height: 512 };
    await page.setViewportSize(viewport);
    for (const path of ['/admin/commerce/orders', '/admin/payment-operations/sales-ledger']) {
      await page.goto(path, { waitUntil: 'networkidle' });
      const table = page.locator('.data-table-wrap').first();
      const box = await table.boundingBox();
      expect(box.height).toBeGreaterThanOrEqual(80);
      expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 2);
    }
  });

  test('표를 내부 스크롤해도 컬럼 헤더가 상단에 유지된다', async ({ page }) => {
    await page.goto('/admin/commerce/orders', { waitUntil: 'networkidle' });
    const wrap = page.locator('.data-table-wrap').first();
    const canScroll = await wrap.evaluate(node => node.scrollHeight > node.clientHeight);
    expect(canScroll).toBeTruthy();
    await wrap.evaluate(node => { node.scrollTop = 240; });
    const wrapBox = await wrap.boundingBox();
    const headBox = await wrap.locator('thead th').first().boundingBox();
    expect(Math.abs(headBox.y - wrapBox.y)).toBeLessThanOrEqual(3);
  });

  test('키보드만으로 대시보드 업무 카드에 접근하고 실행할 수 있다', async ({ page }) => {
    await page.goto('/admin/operations-dashboard', { waitUntil: 'networkidle' });
    await page.keyboard.press('Tab');
    await expect(page.locator('.skip-to-content')).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('#main-content')).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.locator('.ops-metric').first()).toBeFocused();
    await page.locator('.ops-metric').first().press('Enter');
    await expect(page).toHaveURL(/from=dashboard/);
    await expect(page.locator('.dashboard-filter-context')).toBeVisible();
  });

  test('재고 데이터가 없으면 명확한 빈 상태를 표시한다', async ({ page }) => {
    await page.route('**/admin/api/commerce/location-inventory**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
    await page.goto('/admin/commerce/inventory', { waitUntil: 'networkidle' });
    await expect(page.locator('#empty-inventory')).toBeVisible();
    await expect(page.locator('#inventory-rows tr')).toHaveCount(0);
  });

  test('재고 API 오류 시 오류 메시지와 안전한 빈 상태를 표시한다', async ({ page }) => {
    await page.route('**/admin/api/commerce/location-inventory**', route => route.fulfill({ status: 500, contentType: 'application/json', body: '{"message":"test failure"}' }));
    await page.goto('/admin/commerce/inventory', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.app-toast.error')).toBeVisible();
    await expect(page.locator('#inventory-rows tr')).toHaveCount(0);
  });
});
