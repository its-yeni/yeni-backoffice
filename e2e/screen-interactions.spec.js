const { test, expect } = require('@playwright/test');

test('반품 필터·검색·초기화·새로고침 상태가 일관되게 동작한다', async ({ page }) => {
  await page.goto('/admin/commerce/returns?status=REQUESTED', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('#return-status-filter')).toHaveValue('REQUESTED');
  await expect(page.locator('#return-rows tr').first()).toBeVisible();
  await expect.poll(async () => {
    const labels = await page.locator('#return-rows tr .status-indicator').allTextContents();
    return labels.length > 0 && labels.every(label => label.trim() === '접수');
  }).toBe(true);

  await page.reload({ waitUntil: 'domcontentloaded' });
  await expect(page.locator('#return-status-filter')).toHaveValue('REQUESTED');

  const rows = await (await page.request.get('/admin/api/commerce/returns')).json();
  const target = rows.find(row => row.status === 'REQUESTED');
  expect(target).toBeTruthy();
  await page.locator('#return-keyword').fill(target.orderNo);
  await expect(page.locator('#return-rows tr')).toHaveCount(1);
  await expect(page.locator('#return-rows tr').first()).toContainText(target.orderNo);

  await page.locator('#return-filter-reset').click();
  await expect(page.locator('#return-keyword')).toHaveValue('');
  await expect(page.locator('#return-status-filter')).toHaveValue('');
  await expect(page.locator('#return-rows tr').first()).toBeVisible();
});

test('반품 접수 필수값 오류가 사용자에게 표시된다', async ({ page }) => {
  await page.goto('/admin/commerce/returns', { waitUntil: 'domcontentloaded' });
  await page.locator('#return-new-open').click();
  await expect(page.locator('#return-create-modal')).toHaveClass(/open/);
  await page.locator('#return-create-submit').click();
  await expect(page.locator('.app-toast.error').last()).toContainText('주문 번호를 입력해 주세요');

  await page.locator('#return-order-id').fill('1');
  await page.locator('#return-create-submit').click();
  await expect(page.locator('.app-toast.error').last()).toContainText('반품 사유를 입력해 주세요');
  await expect(page.locator('#return-create-modal')).toHaveClass(/open/);
});
