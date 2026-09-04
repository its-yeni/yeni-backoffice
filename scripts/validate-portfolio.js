const { chromium } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

(async () => {
  const root = path.resolve(__dirname, '..');
  const output = path.join(root, 'docs', 'portfolio');
  const html = path.join(output, 'Yeni_Backoffice_Portfolio.html');
  if (!fs.existsSync(html)) throw new Error('포트폴리오 HTML이 없습니다. 먼저 portfolio:pdf를 실행하세요.');

  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1403, height: 992 }, deviceScaleFactor: 1 });
    await page.goto(pathToFileURL(html).href, { waitUntil: 'networkidle' });
    const layout = await page.locator('.page').evaluateAll(pages => pages.map((item, index) => ({
      page: index + 1,
      overflowX: item.scrollWidth > item.clientWidth + 1,
      overflowY: item.scrollHeight > item.clientHeight + 1
    })));
    const failed = layout.filter(item => item.overflowX || item.overflowY);
    if (failed.length) throw new Error(`페이지 영역 초과: ${JSON.stringify(failed)}`);

    for (const index of [0, 4, 9, 12, 13]) {
      await page.locator('.page').nth(index).screenshot({ path: path.join(output, `preview-${index + 1}.png`) });
    }
    console.log(`${layout.length} pages validated; no layout overflow.`);
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
