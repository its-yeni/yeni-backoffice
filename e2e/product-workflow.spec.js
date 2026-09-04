const { test, expect } = require('@playwright/test');

test.describe('상품 운영 핵심 흐름', () => {
  test('상품 옵션 편집을 열고 입고 관리 화면으로 이동한다', async ({ page }) => {
    await page.goto('/admin/commerce/products', { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('heading', { name: '상품 관리' })).toBeVisible();
    const firstProduct = page.locator('#product-rows tr').first();
    await expect(firstProduct).toBeVisible();

    await firstProduct.locator('[data-options]').click();
    await expect(page.locator('#product-modal')).toHaveClass(/open/);
    await expect(page.locator('#option-detail-panel')).toBeVisible();
    await page.getByRole('button', { name: '닫기', exact: true }).click();
    await expect(page.locator('#product-modal')).not.toHaveClass(/open/);
    await page.locator('.nav a[href="/admin/commerce/receiving"]').click();

    await expect(page).toHaveURL(/\/admin\/commerce\/receiving$/);
    await expect(page.getByRole('heading', { name: '입고 관리' })).toBeVisible();
    await expect(page.locator('#select-rows tr').first()).toBeVisible();
  });

  test('옵션 화면의 내부 페이지네이션이 화면 하단을 고정 점유하지 않는다', async ({ page }) => {
    await page.goto('/admin/commerce/products', { waitUntil: 'domcontentloaded' });
    const firstProduct = page.locator('#product-rows tr').first();
    await expect(firstProduct).toBeVisible();
    const productId = await firstProduct.getAttribute('data-product-row');
    await page.goto(`/admin/commerce/product-options?productId=${productId}`, { waitUntil: 'domcontentloaded' });

    const pagination = page.locator('#variant-pagination');
    await expect(pagination).toHaveClass(/inline-pagination/);
    await expect(pagination).not.toHaveClass(/pagination-footer/);
  });
});
