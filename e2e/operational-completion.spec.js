const { test, expect } = require('@playwright/test');

test('반품 접수 → 검수 → 완료 → 환불 결과가 연결된다', async ({ page }) => {
  await page.goto('/admin/commerce/returns', { waitUntil: 'domcontentloaded' });
  const initial = await (await page.request.get('/admin/api/commerce/returns')).json();
  const target = initial.find(row => row.status === 'REQUESTED');
  expect(target, '검수할 반품 접수 데이터').toBeTruthy();

  const inspect = page.locator(`[data-inspect="${target.id}"]`);
  await expect(inspect).toBeVisible();
  await Promise.all([
    page.waitForResponse(response => response.url().includes(`/returns/${target.id}/inspect`) && response.ok()),
    inspect.dblclick()
  ]);

  const inspecting = await (await page.request.get('/admin/api/commerce/returns')).json();
  expect(inspecting.find(row => row.id === target.id)?.status).toBe('INSPECTING');

  const complete = page.locator(`[data-complete-open="${target.id}"]`);
  await expect(complete).toBeVisible();
  await complete.click();
  await Promise.all([
    page.waitForResponse(response => response.url().includes(`/returns/${target.id}/complete`) && response.ok()),
    page.locator(`[data-complete-confirm="${target.id}"]`).click()
  ]);

  await expect.poll(async () => {
    const rows = await (await page.request.get('/admin/api/commerce/returns')).json();
    const row = rows.find(item => item.id === target.id);
    return `${row?.status}:${row?.refundStatus}`;
  }, { timeout: 15_000 }).toMatch(/^COMPLETED:(SUCCESS|NOT_REQUIRED)$/);
});

test('정산 초안 → 확정 → 지급 완료 상태를 검증한다', async ({ page }) => {
  const statements = await (await page.request.get('/admin/api/settlements')).json();
  let target;
  for (const statement of statements.filter(row => row.settlementStatus === 'DRAFT')) {
    const detail = await (await page.request.get(`/admin/api/settlements/${statement.id}`)).json();
    if (detail.reconciliation?.confirmable) { target = statement; break; }
  }
  expect(target, '확정 가능한 정산 초안').toBeTruthy();

  const confirmedResponse = await page.request.post(`/admin/api/settlements/${target.id}/confirm`);
  expect(confirmedResponse.ok()).toBeTruthy();
  expect((await confirmedResponse.json()).settlementStatus).toBe('CONFIRMED');

  page.on('dialog', dialog => dialog.accept());
  await page.goto('/admin/payment-operations/settlements', { waitUntil: 'domcontentloaded' });
  const row = page.locator(`[data-statement="${target.id}"]`);
  await expect(row).toBeVisible();
  await row.click();
  await expect(page.locator('#settlement-detail')).toHaveClass(/open/);

  const next = page.locator('#settlement-next');
  await next.click();
  await expect(page.locator('.app-toast.error').last()).toContainText('지급 참조번호를 3자 이상');
  await expect(page.locator('#settlement-payout-reference')).toHaveClass(/invalid/);
  const beforePay = await (await page.request.get(`/admin/api/settlements/${target.id}`)).json();
  expect(beforePay.statement.settlementStatus).toBe('CONFIRMED');

  await page.locator('#settlement-payout-reference').fill(`E2E-${target.id}`);
  await page.locator('#settlement-payout-account').fill('신한 ***-**-1234');
  await next.click();

  await expect.poll(async () => {
    const detail = await (await page.request.get(`/admin/api/settlements/${target.id}`)).json();
    return detail.statement.settlementStatus;
  }).toBe('PAID');
  await expect(page.locator('#settlement-detail-body .transaction-status')).toContainText('지급 완료');
});
