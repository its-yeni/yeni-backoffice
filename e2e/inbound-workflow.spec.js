const { test, expect } = require('@playwright/test');

test('공급처 → 발주 → 확정 → 입고 → 재고 원장·LOT 반영', async ({ page }) => {
  const unique = Date.now().toString().slice(-8);
  const supplierName = `E2E 공급처 ${unique}`;

  await page.goto('/admin/commerce/inventory/lots');
  await page.waitForLoadState('networkidle');
  const lotsBefore = (await (await page.request.get('/admin/api/commerce/inventory-planning/lots')).json()).length;

  await page.goto('/admin/commerce/suppliers');
  await page.locator('#supplier-new').click();
  await page.locator('#supplier-name').fill(supplierName);
  await page.locator('#supplier-manager').fill('E2E 담당자');
  await page.locator('#supplier-lead').fill('3');
  await page.locator('#supplier-save').click();
  await expect(page.locator('#supplier-dialog')).not.toHaveAttribute('open', '');
  await page.locator('#supplier-keyword').fill(supplierName);
  await expect(page.locator('#supplier-rows tr').filter({ hasText: supplierName })).toHaveCount(1);

  const purchaseOrderDataLoaded = Promise.all([
    '/admin/api/commerce/purchase-orders',
    '/admin/api/commerce/suppliers',
    '/admin/api/commerce/stores',
    '/admin/api/commerce/variants/inventory',
    '/admin/api/commerce/purchase-orders/summary'
  ].map(path => page.waitForResponse(response =>
    response.request().method() === 'GET' && new URL(response.url()).pathname === path)));
  await page.goto('/admin/commerce/purchase-orders');
  await purchaseOrderDataLoaded;
  await page.waitForTimeout(100);
  await expect(page.locator('#po-new')).toBeVisible();
  await page.locator('#po-new').click();
  const supplierValue = await page.locator('#po-supplier option').filter({ hasText: supplierName }).getAttribute('value');
  await page.locator('#po-supplier').selectOption(supplierValue);
  await page.locator('#po-line-variant').selectOption({ index: 0 });
  await page.locator('#po-line-qty').fill('2');
  await page.locator('#po-line-cost').fill('1000');
  await page.locator('#po-line-add-btn').click();
  await expect(page.locator('#po-lines tr')).toHaveCount(1);
  await page.locator('#po-create-save').click();
  await expect(page.locator('#po-create-dialog')).not.toHaveAttribute('open', '');

  await page.locator('#po-keyword').fill(supplierName);
  const purchaseOrder = page.locator('#po-rows tr').first();
  await expect(purchaseOrder).toContainText(supplierName);
  const poNo = (await purchaseOrder.locator('td').first().innerText()).trim();

  await purchaseOrder.click();
  await expect(page.locator('#po-act-place')).toBeVisible();
  await page.locator('#po-act-place').click();
  await expect(page.locator('#po-detail-dialog')).not.toHaveAttribute('open', '');

  await expect(purchaseOrder).toBeVisible();
  await purchaseOrder.click();
  await expect(page.locator('#po-act-receive')).toBeVisible();
  await page.locator('#po-act-receive').click();
  await expect(page.locator('#po-receive-rows tr')).toHaveCount(1);
  await page.locator('#po-receive-save').click();
  await expect(page.locator('#po-receive-dialog')).not.toHaveAttribute('open', '');

  await page.goto('/admin/commerce/inventory/transactions');
  await page.locator('#tx-keyword').fill(poNo);
  await expect(page.locator('#tx-rows tr').first()).toContainText(poNo);

  await page.goto('/admin/commerce/inventory/lots');
  await page.waitForLoadState('networkidle');
  await expect.poll(async () => {
    const response = await page.request.get('/admin/api/commerce/inventory-planning/lots');
    return (await response.json()).length;
  }).toBeGreaterThan(lotsBefore);
});
