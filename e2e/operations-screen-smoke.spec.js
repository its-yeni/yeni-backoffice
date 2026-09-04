const { test, expect } = require('@playwright/test');

const screens = [
  ['/admin/commerce/products', '상품 관리'],
  ['/admin/commerce/orders', '주문 관리'],
  ['/admin/payment-operations', 'PG 거래'],
  ['/admin/payment-operations/recovery-tasks', '복구 작업'],
  ['/admin/payment-operations/sales-ledger', '매출 원장'],
  ['/admin/payment-operations/settlements', '정산 관리'],
  ['/admin/payment-operations/accounting', '회계 · 분개장'],
  ['/admin/commerce/shipments', '출고 관리'],
  ['/admin/commerce/deliveries', '배송 관리'],
  ['/admin/commerce/returns', '반품 관리'],
  ['/admin/commerce/suppliers', '공급처 관리'],
  ['/admin/commerce/purchase-orders', '발주 관리'],
  ['/admin/commerce/receiving', '입고 관리'],
  ['/admin/commerce/inventory', '재고 현황'],
  ['/admin/commerce/inventory/transactions', '재고 이동 이력'],
  ['/admin/commerce/inventory/lots', 'LOT'],
  ['/admin/commerce/stock-counts', '재고 실사'],
  ['/admin/commerce/inventory/transfers', '재고 이동'],
  ['/admin/commerce/inventory/replenishment', '발주 제안']
];

test.describe('핵심 운영 화면 기본 검증', () => {
  for (const [path, title] of screens) {
    test(`${title} 화면이 오류 없이 표시된다`, async ({ page }) => {
      const runtimeErrors = [];
      page.on('pageerror', error => runtimeErrors.push(error.message));
      page.on('console', message => {
        const benignSandboxNetworkBlock = message.text().includes('net::ERR_NETWORK_ACCESS_DENIED');
        if (message.type() === 'error' && !message.text().includes('favicon') && !benignSandboxNetworkBlock) {
          runtimeErrors.push(message.text());
        }
      });

      const response = await page.goto(path, { waitUntil: 'domcontentloaded' });
      expect(response?.status(), `${path} HTTP status`).toBe(200);
      await expect(page.locator('main, .main').first()).toBeVisible();
      await expect(page.locator('h1:visible, h2:visible, h3:visible').filter({ hasText: title }).first()).toBeVisible();
      await page.waitForLoadState('networkidle');

      expect(runtimeErrors, `${path} browser errors`).toEqual([]);
      const viewport = page.viewportSize();
      const bodyWidth = await page.evaluate(() => document.documentElement.scrollWidth);
      expect(bodyWidth, `${path} horizontal overflow`).toBeLessThanOrEqual(viewport.width + 2);
    });
  }
});
