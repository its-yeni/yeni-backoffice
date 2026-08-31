(function () {
  const $ = id => document.getElementById(id);
  const won = v => Number(v || 0).toLocaleString('ko-KR') + '원';
  const LABEL = { DRAFT: '작성 중', ORDERED: '발주 완료', PARTIALLY_RECEIVED: '부분 입고', RECEIVED: '입고 완료', CANCELED: '취소' };
  const TONE = { DRAFT: '', ORDERED: 'is-warning', PARTIALLY_RECEIVED: 'is-warning', RECEIVED: 'is-success', CANCELED: 'is-danger' };
  let rows = [], suppliers = [], stores = [], variants = [], pagination, sorter;
  let createLines = [], currentDetail = null;

  document.addEventListener('DOMContentLoaded', () => {
    pagination = AdminPagination.mount($('po-pagination'), { total: 0, size: 20, onChange: render });
    sorter = AdminTableSort.attach(document.querySelector('.po-table'), { defaultKey: 'poNo', defaultDir: 'desc', onSort: render });
    $('po-keyword').addEventListener('input', debounce(() => { pagination.reset(); render(); }, 250));
    $('po-status-filter').onchange = () => { pagination.reset(); render(); };
    $('po-filter-reset').onclick = () => { $('po-keyword').value = ''; $('po-status-filter').value = ''; pagination.reset(); render(); };
    $('po-new').onclick = openCreate;
    $('po-line-add-btn').onclick = addCreateLine;
    $('po-create-save').onclick = saveCreate;
    $('po-receive-save').onclick = saveReceive;
    load();
  });

  async function load() {
    try {
      const [poRows, supplierList, storeList, variantList, summary] = await Promise.all([
        apiGet('/admin/api/commerce/purchase-orders'),
        apiGet('/admin/api/commerce/suppliers'),
        apiGet('/admin/api/commerce/stores'),
        apiGet('/admin/api/commerce/variants/inventory'),
        apiGet('/admin/api/commerce/purchase-orders/summary'),
      ]);
      rows = poRows || []; suppliers = supplierList || []; stores = (storeList || []).filter(s => s.active);
      variants = variantList || [];
      renderSummary(summary || {});
      render();
    } catch (e) { AppToast.error(e.message || '발주 목록을 조회하지 못했습니다.'); }
  }

  function renderSummary(s) {
    $('kpi-draft').textContent = (s.draftCount || 0).toLocaleString('ko-KR');
    $('kpi-ordered').textContent = (s.orderedCount || 0).toLocaleString('ko-KR');
    $('kpi-partial').textContent = (s.partialCount || 0).toLocaleString('ko-KR');
    $('kpi-overdue').textContent = (s.overdueCount || 0).toLocaleString('ko-KR');
    $('kpi-open-amount').textContent = won(s.openOrderAmount);
  }

  function filtered() {
    const kw = $('po-keyword').value.trim().toLowerCase();
    const st = $('po-status-filter').value;
    return rows.filter(r => {
      if (st && r.status !== st) return false;
      if (!kw) return true;
      return [r.poNo, r.supplierName, r.storeName].some(v => String(v || '').toLowerCase().includes(kw));
    });
  }

  function render() {
    const list = sorter ? sorter.apply(filtered()) : filtered();
    pagination.setTotal(list.length);
    $('po-empty').hidden = list.length > 0;
    const rowStart = (pagination.getPage() - 1) * pagination.getSize();
    $('po-rows').innerHTML = pagination.slice(list).map((r, i) => {
      const pct = r.orderedTotal ? Math.round(r.receivedTotal / r.orderedTotal * 100) : 0;
      return `<tr data-open="${r.id}" class="clickable-row">
        <td class="row-index">${rowStart + i + 1}</td>
        <td><strong>${escapeHtml(r.poNo)}</strong></td>
        <td>${escapeHtml(r.supplierName)}</td>
        <td>${escapeHtml(r.storeName)}</td>
        <td>${r.expectedArrivalDate || '-'}${r.overdue ? ' <span class="status-indicator is-danger">초과</span>' : ''}</td>
        <td><div class="po-progress"><span style="width:${pct}%"></span></div><small>${r.receivedTotal} / ${r.orderedTotal} (${pct}%)</small></td>
        <td class="number">${won(r.totalAmount)}</td>
        <td><span class="status-indicator ${TONE[r.status] || ''}">${LABEL[r.status] || r.status}</span></td>
      </tr>`;
    }).join('');
    document.querySelectorAll('[data-open]').forEach(tr => tr.onclick = () => openDetail(Number(tr.dataset.open)));
  }

  /* ---- 발주서 작성 ---- */
  function variantOption(v) {
    const opt = v.optionSummary && v.optionSummary !== 'BASE' && v.optionSummary !== '기본' ? ` · ${v.optionSummary}` : '';
    return `${v.productName}${opt} — ${v.sku}`;
  }
  function openCreate() {
    createLines = [];
    $('po-supplier').innerHTML = suppliers.filter(s => s.active).map(s => `<option value="${s.id}">${escapeHtml(s.name)} (리드 ${s.leadTimeDays}일)</option>`).join('');
    $('po-store').innerHTML = stores.map(s => `<option value="${s.id}">${escapeHtml(s.storeName)}</option>`).join('');
    $('po-line-variant').innerHTML = variants.map(v => `<option value="${v.variantId}">${escapeHtml(variantOption(v))}</option>`).join('');
    $('po-eta').value = ''; $('po-memo').value = '';
    renderCreateLines();
    $('po-create-dialog').showModal();
  }
  function addCreateLine() {
    const id = Number($('po-line-variant').value);
    const v = variants.find(x => x.variantId === id);
    if (!v) return;
    if (createLines.some(l => l.variantId === id)) return AppToast.error('이미 추가된 SKU입니다.');
    createLines.push({ variantId: id, label: variantOption(v), orderedQuantity: Number($('po-line-qty').value) || 1, unitCost: Number($('po-line-cost').value) || 0 });
    renderCreateLines();
  }
  function renderCreateLines() {
    $('po-lines-empty').hidden = createLines.length > 0;
    $('po-lines').innerHTML = createLines.map((l, i) => `<tr>
      <td>${escapeHtml(l.label)}</td>
      <td class="number"><input type="number" min="1" value="${l.orderedQuantity}" data-qty="${i}" style="width:64px"></td>
      <td class="number"><input type="number" min="0" value="${l.unitCost}" data-cost="${i}" style="width:90px"></td>
      <td class="number">${won(l.orderedQuantity * l.unitCost)}</td>
      <td><button type="button" class="row-icon-btn" data-del="${i}">삭제</button></td>
    </tr>`).join('');
    document.querySelectorAll('[data-qty]').forEach(inp => inp.onchange = () => { createLines[inp.dataset.qty].orderedQuantity = Number(inp.value) || 1; renderCreateLines(); });
    document.querySelectorAll('[data-cost]').forEach(inp => inp.onchange = () => { createLines[inp.dataset.cost].unitCost = Number(inp.value) || 0; renderCreateLines(); });
    document.querySelectorAll('[data-del]').forEach(b => b.onclick = () => { createLines.splice(Number(b.dataset.del), 1); renderCreateLines(); });
  }
  async function saveCreate() {
    if (!createLines.length) return AppToast.error('발주 품목을 1개 이상 추가해 주세요.');
    try {
      await apiPost('/admin/api/commerce/purchase-orders', {
        supplierId: Number($('po-supplier').value),
        storeId: Number($('po-store').value),
        expectedArrivalDate: $('po-eta').value || null,
        memo: $('po-memo').value.trim() || null,
        items: createLines.map(l => ({ variantId: l.variantId, orderedQuantity: l.orderedQuantity, unitCost: l.unitCost })),
      }, 'POST');
      $('po-create-dialog').close();
      AppToast.success('발주서를 저장했습니다. 상세에서 "발주 확정"을 눌러 주세요.');
      await load();
    } catch (e) { AppToast.error(e.message || '발주서 저장에 실패했습니다.'); }
  }

  /* ---- 상세 / 상태 전이 ---- */
  async function openDetail(id) {
    try {
      currentDetail = await apiGet(`/admin/api/commerce/purchase-orders/${id}`);
      const d = currentDetail;
      $('po-detail-title').textContent = `발주 상세 · ${d.poNo}`;
      $('po-detail-body').innerHTML = `
        <div class="po-detail-meta">
          <div><span>공급처</span><strong>${escapeHtml(d.supplierName)}</strong></div>
          <div><span>입고 매장</span><strong>${escapeHtml(d.storeName)}</strong></div>
          <div><span>예정일</span><strong>${d.expectedArrivalDate || '-'}${d.overdue ? ' (초과)' : ''}</strong></div>
          <div><span>상태</span><strong>${LABEL[d.status] || d.status}</strong></div>
          <div><span>발주금액</span><strong>${won(d.totalAmount)}</strong></div>
          <div><span>메모</span><strong>${escapeHtml(d.memo || '-')}</strong></div>
        </div>
        <table class="data-table"><thead><tr><th>상품 / SKU</th><th class="number">발주</th><th class="number">입고</th><th class="number">잔여</th><th class="number">단가</th><th class="number">금액</th></tr></thead>
        <tbody>${d.items.map(i => `<tr>
          <td><strong>${escapeHtml(i.productName)}</strong><br><small class="mono">${escapeHtml(i.sku)}</small></td>
          <td class="number">${i.orderedQuantity}</td><td class="number">${i.receivedQuantity}</td>
          <td class="number">${i.outstandingQuantity}</td><td class="number">${won(i.unitCost)}</td><td class="number">${won(i.lineAmount)}</td>
        </tr>`).join('')}</tbody></table>`;
      $('po-detail-actions').innerHTML = detailActions(d);
      wireDetailActions(d);
      $('po-detail-dialog').showModal();
    } catch (e) { AppToast.error(e.message || '발주 상세를 불러오지 못했습니다.'); }
  }
  function detailActions(d) {
    const buttons = ['<button class="btn btn-light" value="cancel">닫기</button>'];
    if (d.status === 'DRAFT') buttons.push('<button class="btn btn-primary" id="po-act-place" type="button">발주 확정</button>');
    if (d.status === 'DRAFT' || d.status === 'ORDERED') buttons.push('<button class="btn btn-light" id="po-act-cancel" type="button">발주 취소</button>');
    if (d.status === 'ORDERED' || d.status === 'PARTIALLY_RECEIVED') buttons.push('<button class="btn btn-primary" id="po-act-receive" type="button">입고 처리</button>');
    return buttons.join('');
  }
  function wireDetailActions(d) {
    const place = $('po-act-place'), cancel = $('po-act-cancel'), receive = $('po-act-receive');
    if (place) place.onclick = () => transition(d.id, 'place', '발주를 확정했습니다.');
    if (cancel) cancel.onclick = () => { if (confirm('이 발주를 취소하시겠습니까?')) transition(d.id, 'cancel', '발주를 취소했습니다.'); };
    if (receive) receive.onclick = () => openReceive(d);
  }
  async function transition(id, action, msg) {
    try {
      await apiPost(`/admin/api/commerce/purchase-orders/${id}/${action}`, {}, 'POST');
      $('po-detail-dialog').close();
      AppToast.success(msg);
      await load();
    } catch (e) { AppToast.error(e.message || '처리에 실패했습니다.'); }
  }

  /* ---- 입고 처리 ---- */
  function openReceive(d) {
    const open = d.items.filter(i => i.outstandingQuantity > 0);
    if (!open.length) return AppToast.error('입고할 잔여 수량이 없습니다.');
    $('po-receive-rows').innerHTML = open.map(i => `<tr data-item="${i.id}">
      <td><strong>${escapeHtml(i.productName)}</strong><br><small class="mono">${escapeHtml(i.sku)}</small></td>
      <td class="number">${i.orderedQuantity}</td><td class="number">${i.receivedQuantity}</td>
      <td><input type="number" min="0" max="${i.outstandingQuantity}" value="${i.outstandingQuantity}" data-rcv-qty></td>
      <td><input type="date" data-rcv-mfg></td>
      <td><input type="date" data-rcv-exp></td>
    </tr>`).join('');
    $('po-receive-dialog').showModal();
  }
  async function saveReceive() {
    const lines = [];
    document.querySelectorAll('#po-receive-rows tr').forEach(tr => {
      const qty = Number(tr.querySelector('[data-rcv-qty]').value) || 0;
      if (qty <= 0) return;
      lines.push({
        itemId: Number(tr.dataset.item), quantity: qty,
        manufacturedDate: tr.querySelector('[data-rcv-mfg]').value || null,
        expirationDate: tr.querySelector('[data-rcv-exp]').value || null,
      });
    });
    if (!lines.length) return AppToast.error('이번 입고 수량을 1개 이상 입력해 주세요.');
    try {
      await apiPost(`/admin/api/commerce/purchase-orders/${currentDetail.id}/receive`, { lines }, 'POST');
      $('po-receive-dialog').close();
      $('po-detail-dialog').close();
      AppToast.success('입고를 반영했습니다. LOT 번호가 자동 발번되었습니다.');
      await load();
    } catch (e) { AppToast.error(e.message || '입고 처리에 실패했습니다.'); }
  }

  function debounce(fn, wait) { let t; return () => { clearTimeout(t); t = setTimeout(fn, wait); }; }
})();
