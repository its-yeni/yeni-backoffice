const { test, expect } = require('@playwright/test');

test('상품 주문 → 결제 승인 → 매출 원장 → 출고 완료', async ({ page }) => {
  const buyer = `E2E 고객 ${Date.now().toString().slice(-7)}`;

  await page.goto('/admin/commerce/orders');
  await expect.poll(async () => {
    const response = await page.request.get('/admin/api/commerce/orders');
    return response.ok() ? (await response.json()).length : 0;
  }, { timeout: 60_000 }).toBeGreaterThan(0);
  await page.reload();
  await page.locator('[data-tab="mock"]').click();
  await expect(page.locator('#orderItemBody tr').first()).toBeVisible();
  const selectableProductIds = await page.locator('#orderItemBody .product-select').first().locator('option')
    .evaluateAll(options => options.map(option => Number(option.value)).filter(Boolean));
  const productsPage = await (await page.request.get('/admin/api/commerce/products?saleStatus=ON_SALE&size=100')).json();
  const globallyAvailableProductIds = (productsPage.items || [])
    .filter(product => product.stockQuantity > 0)
    .map(product => product.id);
  let stores = [];
  await expect.poll(async () => {
    const response = await page.request.get('/admin/api/commerce/stores');
    if (!response.ok()) return 0;
    stores = (await response.json()).filter(store => store.active);
    return stores.length;
  }, { timeout: 60_000 }).toBeGreaterThan(0);
  let selectedStore, stockCandidate;
  for (const store of stores) {
    const response = await page.request.get(`/admin/api/commerce/location-inventory?storeId=${store.id}`);
    if (!response.ok()) continue;
    const rows = await response.json();
    const candidate = rows.find(row =>
      selectableProductIds.includes(row.productId) && globallyAvailableProductIds.includes(row.productId)
        && row.saleEnabled && row.availableQuantity > 0 && row.lotAvailableTotal > 0);
    if (candidate) { selectedStore = store; stockCandidate = candidate; break; }
  }
  if (!stockCandidate) {
    selectedStore = stores[0];
    const variants = await (await page.request.get('/admin/api/commerce/variants/inventory')).json();
    const variant = variants.find(row => selectableProductIds.includes(row.productId)
      && globallyAvailableProductIds.includes(row.productId) && row.saleStatus === 'ON_SALE');
    expect(variant, 'E2E receipt variant').toBeTruthy();
    const receipt = await page.request.post(`/admin/api/commerce/variants/${variant.variantId}/receive`, {
      data: { quantity: 5, reason: '[E2E] outbound prerequisite receipt', storeId: selectedStore.id }
    });
    expect(receipt.ok(), await receipt.text()).toBeTruthy();
    const locationInventory = await (await page.request.get(`/admin/api/commerce/location-inventory?storeId=${selectedStore.id}`)).json();
    stockCandidate = locationInventory.find(row =>
      selectableProductIds.includes(row.productId) && globallyAvailableProductIds.includes(row.productId)
        && row.saleEnabled && row.availableQuantity > 0 && row.lotAvailableTotal > 0);
  }
  expect(stockCandidate, '출고 가능한 위치·LOT 재고 SKU').toBeTruthy();
  const storeId = String(selectedStore.id);
  expect(selectedStore, 'selected fulfillment store').toBeTruthy();
  await page.evaluate(({ brandId, selectedStoreId }) => {
    localStorage.setItem('commerce-brand-id', String(brandId));
    localStorage.setItem('commerce-store-id', String(selectedStoreId));
  }, { brandId: selectedStore.brandId, selectedStoreId: selectedStore.id });
  await page.goto(`/admin/commerce/orders?brandId=${selectedStore.brandId}&storeId=${storeId}`);
  await page.locator('[data-tab="mock"]').click();
  await expect(page.locator('#orderItemBody .product-select').first().locator(`option[value="${stockCandidate.productId}"]`)).toBeAttached();
  const productVariants = (await (await page.request.get('/admin/api/commerce/variants/inventory')).json())
    .filter(row => row.productId === stockCandidate.productId);
  for (const variant of productVariants) {
    const topUp = await page.request.post(`/admin/api/commerce/variants/${variant.variantId}/receive`, {
      data: { quantity: 5, reason: '[E2E] outbound stock top-up', storeId: Number(storeId) }
    });
    expect(topUp.ok(), await topUp.text()).toBeTruthy();
  }
  await page.locator('#orderItemBody .product-select').first().selectOption(String(stockCandidate.productId));
  await expect(page.locator('#orderItemBody .configuration').first()).not.toContainText('불러오는 중');
  await page.locator('#buyerNameInput').fill(buyer);
  await page.locator('input[name="scenario"][value="NORMAL"]').check();
  await page.locator('#mockCreateBtn').click();
  await expect(page.locator('#mockMessage')).toContainText('ORD-');
  const message = await page.locator('#mockMessage').innerText();
  const orderNo = message.match(/ORD-[A-Z0-9-]+/)?.[0];
  expect(orderNo).toBeTruthy();
  await expect(page.locator('#order-detail-modal')).toHaveClass(/open/);
  await page.locator('#order-detail-modal .icon-close').click();

  await page.locator('[data-tab="list"]').click();
  await page.locator('#orderKeyword').fill(orderNo);
  await page.locator('#orderSearchBtn').click();
  const orderRow = page.locator('#orderRows tr').filter({ hasText: orderNo });
  await expect(orderRow).toHaveCount(1);
  await expect(orderRow.locator('[data-a="ship"]')).toBeVisible();

  await page.goto('/admin/payment-operations');
  await page.locator('#payment-keyword').fill(orderNo);
  await expect(page.locator('#payment-rows tr').filter({ hasText: orderNo })).toHaveCount(1);

  await page.goto('/admin/payment-operations/sales-ledger');
  await page.locator('#ledger-keyword').fill(orderNo);
  await page.locator('#ledger-search').click();
  await expect(page.locator('#ledger-rows tr').filter({ hasText: orderNo })).toHaveCount(1);

  await page.goto(`/admin/commerce/orders?keyword=${encodeURIComponent(orderNo)}`);
  const shippableRow = page.locator('#orderRows tr').filter({ hasText: orderNo });
  await expect(shippableRow).toHaveCount(1);
  await expect(shippableRow.locator('[data-a="ship"]')).toBeVisible();
  const shipmentResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().includes('/admin/api/commerce/shipments/'));
  await shippableRow.locator('[data-a="ship"]').click();
  const shipmentResponse = await shipmentResponsePromise;
  expect(shipmentResponse.ok(), await shipmentResponse.text()).toBeTruthy();
  await expect(shippableRow.locator('[data-a="ship"]')).toHaveCount(0);
});

test('배송 준비 → 송장 등록 → 배송 완료 → 구매 확정', async ({ page }) => {
  page.on('dialog', dialog => dialog.accept());
  await page.goto('/admin/commerce/deliveries?status=PREPARING');
  const deliveryRow = page.locator('#delivery-rows tr').filter({ has: page.locator('[data-dispatch-open]') }).first();
  await expect(deliveryRow).toBeVisible();
  const orderNo = (await deliveryRow.locator('td').first().innerText()).match(/ORD-[A-Z0-9-]+/)?.[0];
  expect(orderNo).toBeTruthy();
  const deliveryId = await deliveryRow.locator('[data-dispatch-open]').getAttribute('data-dispatch-open');
  await deliveryRow.locator('[data-dispatch-open]').click();
  await page.locator(`[data-tracking="${deliveryId}"]`).fill(`E2E${Date.now().toString().slice(-9)}`);
  const dispatchResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().includes(`/deliveries/${deliveryId}/dispatch`));
  await page.locator(`[data-dispatch-confirm="${deliveryId}"]`).click();
  const dispatchResponse = await dispatchResponsePromise;
  expect(dispatchResponse.ok(), await dispatchResponse.text()).toBeTruthy();
  await page.goto(`/admin/commerce/deliveries?deliveryId=${deliveryId}`);
  await page.locator('#delivery-status-filter').selectOption('');
  await expect(page.locator(`[data-complete="${deliveryId}"]`)).toBeVisible();
  await page.locator(`[data-complete="${deliveryId}"]`).click();
  await expect(page.locator('#delivery-rows tr').filter({ hasText: orderNo })).toContainText('배송 완료');

  await page.goto(`/admin/commerce/orders?keyword=${encodeURIComponent(orderNo)}`);
  await expect(page.locator('#orderRows tr').filter({ hasText: orderNo })).toContainText('구매 확정');
});
