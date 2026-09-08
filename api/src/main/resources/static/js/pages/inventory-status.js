document.addEventListener('DOMContentLoaded', () => {
  const $ = id => document.getElementById(id);
  let rows = [];
  const expanded = new Set();
  const pagination = AdminPagination.mount($('inventory-pagination'), { total: 0, size: 20, onChange: render });
  const sorter = AdminTableSort.attach(document.querySelector('.inventory-status-table'), { onSort: render });
  const money = value => Math.round(Number(value || 0)).toLocaleString('ko-KR') + '원';
  const number = value => Number(value || 0).toLocaleString('ko-KR');
  const incoming = row => Number(row.inTransitQuantity || 0) + Number(row.onOrderQuantity || 0);
  const lotMismatch = row => Number(row.lotAvailableTotal || 0) > 0 && Number(row.lotAvailableTotal || 0) !== Number(row.stockQuantity || 0);
  function health(row) { if (row.availableQuantity === 0) return 'SOLD_OUT'; if (row.availableQuantity <= row.safetyStock) return 'LOW'; if (incoming(row) > 0) return 'IN_TRANSIT'; return 'NORMAL'; }
  function healthText(h) { return ({ SOLD_OUT: '품절', LOW: '재고 부족', IN_TRANSIT: '입고 예정', NORMAL: '정상' })[h]; }
  const HEALTH_RANK = { SOLD_OUT: 0, LOW: 1, IN_TRANSIT: 2, NORMAL: 3 };
  const requestedHealth = new URLSearchParams(location.search).get('health');
  if (requestedHealth && HEALTH_RANK[requestedHealth] !== undefined) $('inventory-health-filter').value = requestedHealth;
  const lotBadge = row => lotMismatch(row) ? ` <span class="inventory-state low" title="LOT 잔량 합 ${number(row.lotAvailableTotal)} ≠ 현재재고 ${number(row.stockQuantity)}">LOT</span>` : '';
  const incomingCell = q => q ? `<a href="/admin/commerce/purchase-orders">${number(q)}</a>` : '0';
  function safetyCell(row) { return `<input type="number" min="0" value="${row.safetyStock}" data-safety="${row.inventoryId}" class="inventory-safety-input">`; }

  async function load() {
    try {
      const [inventory, stores] = await Promise.all([apiGet('/admin/api/commerce/location-inventory'), apiGet('/admin/api/commerce/stores')]);
      rows = inventory || [];
      $('inventory-store-filter').innerHTML = '<option value="">위치 전체</option>' + (stores || []).map(store => `<option value="${store.id}">${escapeHtml(store.storeName)}</option>`).join('');
      // 기본값을 상단에서 선택한 매장으로 맞춘다 — "위치 전체"는 그대로 선택 가능.
      const current = (stores || []).find(s => s.storeCode === activeStoreCode());
      if (current && !$('inventory-store-filter').value) $('inventory-store-filter').value = String(current.id);
      renderSummary(); render();
    } catch (error) { rows = []; renderSummary(); render(); AppToast.error(error.message || '재고 현황을 불러오지 못했습니다.'); }
    renderOpsQueue();
  }

  // 상단 "지금 처리할 것" — 발주 필요 / 입고 대기 / 지연 발주 / 품절 / 유통기한 임박.
  // 재고 목록과 별개 데이터라 실패해도 화면 나머지는 유지한다.
  async function renderOpsQueue() {
    const host = $('inv-ops-strip');
    if (!host) return;
    try {
      const [repl, poSummary, planSummary] = await Promise.all([
        apiGet('/admin/api/commerce/inventory-planning/replenishments').catch(() => []),
        apiGet('/admin/api/commerce/purchase-orders/summary').catch(() => ({})),
        apiGet('/admin/api/commerce/inventory-planning/summary').catch(() => ({}))
      ]);
      const reorder = (repl || []).filter(r => Number(r.suggestedOrderQuantity || 0) > 0).length;
      const awaiting = Number(poSummary.orderedCount || 0) + Number(poSummary.partialCount || 0);
      const overdue = Number(poSummary.overdueCount || 0);
      const soldOut = Number(planSummary.soldOutCount || 0);
      const expiring = Number(planSummary.expiringSoonCount || 0);
      const seg = (label, count, href, tone) => `<a href="${href}"${tone ? ` class="seg-${tone}"` : ''}><span>${label}</span><strong>${number(count)}</strong></a>`;
      const cells = [
        seg('발주 필요', reorder, '/admin/commerce/inventory/replenishment', reorder ? 'warn' : ''),
        seg('입고 대기', awaiting, '/admin/commerce/receiving', ''),
        seg('지연 발주', overdue, '/admin/commerce/purchase-orders', overdue ? 'bad' : ''),
        seg('품절 SKU', soldOut, '/admin/commerce/inventory?health=SOLD_OUT', soldOut ? 'bad' : ''),
        seg('유통기한 임박', expiring, '/admin/commerce/inventory/lots', expiring ? 'warn' : '')
      ];
      host.innerHTML = cells.join('');
      host.hidden = false;
    } catch (ignore) { host.hidden = true; }
  }
  function filtered() {
    const keyword = $('inventory-keyword').value.trim().toLowerCase(), store = $('inventory-store-filter').value, state = $('inventory-health-filter').value;
    return rows.filter(row => (!keyword || [row.productName, row.productCode, row.sku, row.optionSummary].some(value => String(value || '').toLowerCase().includes(keyword))) && (!store || String(row.storeId) === store) && (!state || health(row) === state));
  }
  function renderSummary() {
    const scope = filtered();
    const available = scope.reduce((s, r) => s + Number(r.availableQuantity || 0), 0), inbound = scope.reduce((s, r) => s + incoming(r), 0), assets = scope.reduce((s, r) => s + Number(r.inventoryAssetValue || 0), 0);
    $('summary-total').textContent = number(scope.length); $('summary-available').textContent = number(available); $('summary-transit').textContent = number(inbound); $('summary-low').textContent = number(scope.filter(r => ['LOW', 'SOLD_OUT'].includes(health(r))).length); $('summary-assets').textContent = money(assets);
  }

  /** 같은 상품코드 + 같은 재고 위치의 SKU들을 한 묶음으로 집계한다. */
  function groupRows(list) {
    const groups = new Map();
    list.forEach(row => {
      const key = row.productCode + '|' + row.storeId;
      let g = groups.get(key);
      if (!g) { g = { key, productName: row.productName, productCode: row.productCode, storeName: row.storeName, storeCode: row.storeCode, stockQuantity: 0, reservedQuantity: 0, availableQuantity: 0, inTransitQuantity: 0, incomingQuantity: 0, inventoryAssetValue: 0, worst: 'NORMAL', members: [] }; groups.set(key, g); }
      g.stockQuantity += Number(row.stockQuantity || 0);
      g.reservedQuantity += Number(row.reservedQuantity || 0);
      g.availableQuantity += Number(row.availableQuantity || 0);
      g.inTransitQuantity += Number(row.inTransitQuantity || 0);
      g.incomingQuantity += incoming(row);
      g.inventoryAssetValue += Number(row.inventoryAssetValue || 0);
      if (HEALTH_RANK[health(row)] < HEALTH_RANK[g.worst]) g.worst = health(row);
      g.members.push(row);
    });
    return [...groups.values()];
  }

  function detailRow(row) {
    return `<tr class="inventory-sub-row"><td class="row-index"></td><td class="sub-sku"><span class="mono">${escapeHtml(row.sku)}</span> · ${escapeHtml(row.optionSummary || '기본')}</td><td></td><td class="number">${number(row.stockQuantity)}</td><td class="number">${number(row.reservedQuantity)}</td><td class="number">${number(row.availableQuantity)}</td><td class="number">${safetyCell(row)}</td><td class="number">${number(incoming(row))}</td><td class="number">${money(row.averageUnitCost)}</td><td class="number">${money(row.inventoryAssetValue)}</td><td><span class="inventory-state ${health(row).toLowerCase()}">${healthText(health(row))}</span>${lotBadge(row)}</td><td><a class="row-icon-btn" href="/admin/commerce/inventory/transactions?variantId=${row.variantId}">이력</a></td></tr>`;
  }
  function wireSafetyInputs() {
    document.querySelectorAll('[data-safety]').forEach(inp => inp.onchange = async () => {
      try {
        await apiPost(`/admin/api/commerce/location-inventory/${inp.dataset.safety}/safety-stock`, { safetyStock: Number(inp.value) || 0 }, 'PATCH');
        const row = rows.find(r => String(r.inventoryId) === inp.dataset.safety);
        if (row) row.safetyStock = Number(inp.value) || 0;
        AppToast.success('안전재고를 저장했습니다.');
        renderSummary();
      } catch (e) { AppToast.error(e.message || '안전재고 저장에 실패했습니다.'); }
    });
  }

  function render() {
    renderSummary();
    const list = sorter ? sorter.apply(filtered()) : filtered();
    const grouped = $('inventory-group-toggle').checked;
    $('empty-inventory').hidden = list.length > 0;

    const rowStart = (pagination.getPage() - 1) * pagination.getSize();
    if (!grouped) {
      pagination.setTotal(list.length);
      $('inventory-rows').innerHTML = pagination.slice(list).map((row, i) => `<tr><td class="row-index">${rowStart + i + 1}</td><td><strong>${escapeHtml(row.productName)}</strong><br><small class="mono">${escapeHtml(row.sku)}</small> · <small>${escapeHtml(row.optionSummary || '기본')}</small></td><td><strong>${escapeHtml(row.storeName)}</strong><br><small>${escapeHtml(row.storeCode)}</small></td><td class="number">${number(row.stockQuantity)}</td><td class="number">${number(row.reservedQuantity)}</td><td class="number"><strong>${number(row.availableQuantity)}</strong></td><td class="number">${safetyCell(row)}</td><td class="number">${incomingCell(incoming(row))}</td><td class="number">${money(row.averageUnitCost)}</td><td class="number"><strong>${money(row.inventoryAssetValue)}</strong></td><td><span class="inventory-state ${health(row).toLowerCase()}">${healthText(health(row))}</span>${lotBadge(row)}</td><td><a class="row-icon-btn" href="/admin/commerce/inventory/transactions?variantId=${row.variantId}">이력</a></td></tr>`).join('');
      wireSafetyInputs();
      return;
    }

    const groups = groupRows(list);
    pagination.setTotal(groups.length);
    $('inventory-rows').innerHTML = pagination.slice(groups).map((g, i) => {
      const open = expanded.has(g.key);
      const head = `<tr class="inventory-group-row ${open ? 'open' : ''}" data-group="${escapeHtml(g.key)}">
        <td class="row-index">${rowStart + i + 1}</td>
        <td><button type="button" class="inventory-group-caret">${open ? '▾' : '▸'}</button><strong>${escapeHtml(g.productName)}</strong><br><small class="mono">${escapeHtml(g.productCode)}</small> · <small>${g.members.length}개 SKU</small></td>
        <td><strong>${escapeHtml(g.storeName)}</strong><br><small>${escapeHtml(g.storeCode)}</small></td>
        <td class="number">${number(g.stockQuantity)}</td><td class="number">${number(g.reservedQuantity)}</td>
        <td class="number"><strong>${number(g.availableQuantity)}</strong></td>
        <td class="number">-</td>
        <td class="number">${number(g.incomingQuantity)}</td>
        <td class="number">-</td><td class="number"><strong>${money(g.inventoryAssetValue)}</strong></td>
        <td><span class="inventory-state ${g.worst.toLowerCase()}">${healthText(g.worst)}</span></td><td></td></tr>`;
      return head + (open ? g.members.map(detailRow).join('') : '');
    }).join('');

    $('inventory-rows').querySelectorAll('[data-group]').forEach(tr => tr.onclick = e => {
      if (e.target.closest('a')) return;
      const key = tr.dataset.group;
      if (expanded.has(key)) expanded.delete(key); else expanded.add(key);
      render();
    });
  }

  ['inventory-keyword', 'inventory-store-filter', 'inventory-health-filter'].forEach(id => $(id).addEventListener(id === 'inventory-keyword' ? 'input' : 'change', () => { pagination.setPage(1); render(); }));
  $('inventory-group-toggle').addEventListener('change', () => { expanded.clear(); pagination.setPage(1); render(); });
  $('inventory-filter-reset').onclick = () => { $('inventory-keyword').value = ''; $('inventory-store-filter').value = ''; $('inventory-health-filter').value = ''; pagination.setPage(1); render(); };
  // 운영 기준 문구는 공통 "도움말" 토글로 이관됨(common.js initializePageHelp). 버튼이 남아 있으면만 바인딩.
  if ($('inventory-policy-toggle') && $('inventory-policy')) $('inventory-policy-toggle').onclick = () => { $('inventory-policy').hidden = !$('inventory-policy').hidden; $('inventory-policy-toggle').textContent = $('inventory-policy').hidden ? '운영 기준 보기' : '운영 기준 닫기'; };
  load();
});
