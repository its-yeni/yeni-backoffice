const { test, expect } = require('@playwright/test');

async function setContext(page, brandId, storeId, brandStoreIds, stores) {
  await page.addInitScript(({ brandId, storeId, brandStoreIds, stores }) => {
    localStorage.setItem('commerce-brand-id', String(brandId));
    localStorage.setItem('commerce-store-id', String(storeId));
    localStorage.setItem('commerce-brand-store-scope', String(brandId));
    localStorage.setItem('commerce-brand-store-ids', JSON.stringify(brandStoreIds));
    localStorage.setItem('commerce-store-directory', JSON.stringify(stores));
    if (!storeId) localStorage.removeItem('commerce-store-code');
  }, { brandId, storeId, brandStoreIds, stores });
}

test('전체 브랜드·전체 매장은 주문 행마다 매장명을 표시한다', async ({ page }) => {
  const stores = await (await page.request.get('/admin/api/commerce/stores')).json();
  await setContext(page, 0, 0, stores.map(store => store.id), stores);
  await page.goto('/admin/commerce/orders', { waitUntil: 'domcontentloaded' });

  await expect(page.locator('.order-list-table thead')).toContainText('매장');
  const rows = page.locator('#orderRows tr');
  await expect(rows.first()).toBeVisible();
  const labels = await rows.locator('td:nth-child(5)').allTextContents();
  expect(labels.every(label => label.trim() && !label.includes('매장 #'))).toBeTruthy();

  const payments = await (await page.request.get('/admin/api/payment-operations/payments?includeUnassigned=true')).json();
  const ledger = await (await page.request.get('/admin/api/sales-ledger?startDate=2020-01-01&endDate=2099-12-31&page=0&size=500')).json();
  expect(payments.every(payment => payment.storeId != null), '매장 미지정 결제 없음').toBeTruthy();
  expect((ledger.data || []).every(transaction => transaction.storeId != null), '매장 미지정 매출 없음').toBeTruthy();
});

test('특정 브랜드·전체 매장은 해당 브랜드 소속 매장 주문만 표시한다', async ({ page }) => {
  const stores = await (await page.request.get('/admin/api/commerce/stores')).json();
  const orders = await (await page.request.get('/admin/api/commerce/orders')).json();
  const targetStore = stores.find(store => orders.some(order => order.storeId === store.id));
  expect(targetStore).toBeTruthy();
  const allowedStores = stores.filter(store => store.brandId === targetStore.brandId);
  const allowedIds = allowedStores.map(store => store.id);
  const scopedOrders = await (await page.request.get(`/admin/api/commerce/orders?brandId=${targetStore.brandId}`)).json();
  expect(scopedOrders.length).toBeGreaterThan(0);
  expect(scopedOrders.every(order => allowedIds.includes(order.storeId))).toBeTruthy();
  await setContext(page, targetStore.brandId, 0, allowedIds, stores);
  await page.goto(`/admin/commerce/orders?brandId=${targetStore.brandId}`, { waitUntil: 'domcontentloaded' });

  await expect(page.locator('#orderRows tr').first()).toBeVisible();
  const visibleIds = await page.locator('#orderRows tr').evaluateAll(rows => rows.map(row => Number(row.dataset.order)));
  expect(visibleIds.length).toBeGreaterThan(0);
  const byId = new Map(orders.map(order => [order.id, order]));
  expect(visibleIds.every(id => allowedIds.includes(byId.get(id)?.storeId))).toBeTruthy();
});

test('브랜드와 소속이 다른 매장 조합은 서버에서 거절한다', async ({ page }) => {
  const stores = await (await page.request.get('/admin/api/commerce/stores')).json();
  const brandStore = stores.find(store => store.brandId != null);
  const otherStore = stores.find(store => store.brandId != null && store.brandId !== brandStore?.brandId);
  expect(brandStore).toBeTruthy();
  expect(otherStore).toBeTruthy();

  const response = await page.request.get(`/admin/api/commerce/orders?brandId=${brandStore.brandId}&storeId=${otherStore.id}`);
  expect(response.status()).toBe(400);
});

test('특정 매장은 주문과 물류 API가 동일 매장 범위를 사용한다', async ({ page }) => {
  const stores = await (await page.request.get('/admin/api/commerce/stores')).json();
  const orders = await (await page.request.get('/admin/api/commerce/orders')).json();
  const targetStore = stores.find(store => orders.some(order => order.storeId === store.id));
  expect(targetStore).toBeTruthy();
  await setContext(page, targetStore.brandId, targetStore.id, [targetStore.id], stores);
  await page.goto(`/admin/commerce/orders?brandId=${targetStore.brandId}&storeId=${targetStore.id}`, { waitUntil: 'domcontentloaded' });

  const visibleIds = await page.locator('#orderRows tr').evaluateAll(rows => rows.map(row => Number(row.dataset.order)));
  const byId = new Map(orders.map(order => [order.id, order]));
  expect(visibleIds.every(id => byId.get(id)?.storeId === targetStore.id)).toBeTruthy();

  for (const [path, header] of [
    ['/admin/commerce/shipments', '출고 매장'],
    ['/admin/commerce/deliveries', '매장'],
    ['/admin/commerce/returns', '매장'],
    ['/admin/payment-operations', '매장'],
    ['/admin/payment-operations/sales-ledger', '매장']
  ]) {
    await page.goto(`${path}?brandId=${targetStore.brandId}&storeId=${targetStore.id}`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('thead').first()).toContainText(header);
  }
});
