const { test, expect } = require('@playwright/test');

test('재고 실사 시작 → 수량 입력 → 반영 완료', async ({ page }) => {
  const storesLoaded = page.waitForResponse(response => response.url().includes('/admin/api/commerce/stores'));
  await page.goto('/admin/commerce/stock-counts');
  await storesLoaded;
  await page.locator('#sc-new').click();
  await expect(page.locator('#sc-store option').first()).toBeAttached();
  await page.locator('#sc-memo').fill(`E2E 실사 ${Date.now()}`);
  await page.locator('#sc-start-save').click();
  await expect(page.locator('#sc-detail-dialog')).toHaveAttribute('open', '');
  const lines = page.locator('#sc-lines tr');
  await expect(lines.first()).toBeVisible();
  for (let i = 0; i < await lines.count(); i += 1) {
    const row = lines.nth(i);
    const systemQuantity = (await row.locator('td').nth(2).innerText()).trim();
    const input = row.locator('[data-count]');
    await input.fill(systemQuantity);
    await input.blur();
  }
  await page.locator('#sc-act-complete').click();
  await expect(page.locator('#sc-detail-dialog')).not.toHaveAttribute('open', '');
  await expect(page.locator('#sc-rows tr').first()).toContainText('반영 완료');
});

test('재고 이동 전표 생성 → 출고 확정 → 입고 확인', async ({ page }) => {
  await page.goto('/admin/commerce/inventory/transfers');
  let inventories = await (await page.request.get('/admin/api/commerce/location-inventory')).json();
  let source = inventories.find(row => row.availableQuantity > 0 && row.lotAvailableTotal > 0);
  if (!source) {
    const variants = await (await page.request.get('/admin/api/commerce/variants/inventory')).json();
    const storesForReceipt = await (await page.request.get('/admin/api/commerce/stores')).json();
    const variant = variants.find(row => row.saleStatus === 'ON_SALE') || variants[0];
    const receiptStore = storesForReceipt.find(store => store.active);
    const receipt = await page.request.post(`/admin/api/commerce/variants/${variant.variantId}/receive`, {
      data: { quantity: 5, reason: '[E2E] 이동 전제 입고', storeId: receiptStore.id }
    });
    expect(receipt.ok(), await receipt.text()).toBeTruthy();
    inventories = await (await page.request.get('/admin/api/commerce/location-inventory')).json();
    source = inventories.find(row => row.availableQuantity > 0 && row.lotAvailableTotal > 0);
    await page.reload();
  }
  expect(source, '이동 가능한 위치 재고').toBeTruthy();
  const stores = await (await page.request.get('/admin/api/commerce/stores')).json();
  const destination = stores.find(store => store.active && store.id !== source.storeId);
  expect(destination, '다른 활성 도착 위치').toBeTruthy();

  await expect.poll(async () => page.locator('#transfer-variant option').count()).toBeGreaterThan(0);
  await page.locator('#transfer-new').click();
  await page.locator('#source-store').selectOption(String(source.storeId));
  await page.locator('#destination-store').selectOption(String(destination.id));
  await page.locator('#transfer-variant').selectOption(String(source.variantId));
  await page.locator('#transfer-quantity').fill('1');
  const reason = `E2E 이동 ${Date.now()}`;
  await page.locator('#transfer-reason').fill(reason);
  await page.locator('#transfer-save').click();
  await expect(page.locator('#transfer-dialog')).not.toHaveAttribute('open', '');

  const transferRow = page.locator('#transfer-rows tr').filter({ hasText: reason });
  await expect(transferRow).toHaveCount(1);
  await transferRow.locator('[data-ship]').click();
  await expect(transferRow.locator('[data-receive]')).toBeVisible();
  await transferRow.locator('[data-receive]').click();
  await expect(transferRow).toContainText('입고 완료');
});
