(function () {
  const $ = id => document.getElementById(id);
  let rows = [], pagination;
  const {labels: STATUS_LABEL, tones: STATUS_TONE} = CommerceStatusCatalog.delivery;
  // 실무에서 자주 쓰는 택배사 목록 + 목록에 없는 곳은 "직접입력"으로 받는다 — 5개만 하드코딩돼 있으면
  // 목록에 없는 택배사를 쓰는 주문은 아예 배송 시작 처리를 못 하게 되는 문제가 있었다.
  const CARRIERS = ['CJ대한통운', '한진택배', '롯데택배', '우체국택배', '로젠택배', '경동택배', '대신택배', '합동택배', '일양로지스', 'CU 편의점택배', 'GS Postbox 택배', '천일택배'];
  const CUSTOM_CARRIER = '__custom__';
  // 배송 중 상태가 이 시간(시간 단위)보다 오래 머물러 있으면 "지연" 배지를 붙인다. 실제 SLA 값이 아니라
  // 데모용 기준값이라, 운영에 맞게 바꿔 쓸 수 있게 상수 하나로 뺐다.
  const STALE_HOURS = 72;

  let sorter;
  document.addEventListener('DOMContentLoaded', () => {
    pagination = AdminPagination.mount($('delivery-pagination'), { total: 0, size: 20, onChange: render });
    sorter = AdminTableSort.attach(document.querySelector('.delivery-table'), { onSort: render });
    $('delivery-keyword').addEventListener('input', debounce(() => { pagination.reset(); render(); }, 250));
    $('delivery-status-filter').onchange = () => { pagination.reset(); render(); };
    $('delivery-stale-only').onchange = () => { pagination.reset(); render(); };
    $('delivery-filter-reset').onclick = () => {
      $('delivery-keyword').value = ''; $('delivery-status-filter').value = ''; $('delivery-stale-only').checked = false;
      focusDeliveryId = null;
      pagination.reset(); render();
    };
    $('bulk-dispatch-open').onclick = openBulkDispatchModal;
    document.querySelectorAll('[data-close-bulk-dispatch]').forEach(el => el.onclick = closeBulkDispatchModal);
    document.addEventListener('keydown', event => { if (event.key === 'Escape') closeBulkDispatchModal(); });
    $('bulk-dispatch-file').onchange = handleBulkDispatchFile;
    $('bulk-dispatch-submit').onclick = submitBulkDispatch;
    applyQueryParams();
    load();
  });

  // 운영 대시보드의 예외 큐("배송 지연 건" 등)에서 필터가 적용된 채로 들어올 수 있게 쿼리스트링을 읽는다 —
  // 링크만 걸어두고 실제로는 필터가 안 걸리면 클릭해도 아무 도움이 안 되는 화면이 된다.
  let focusDeliveryId = null;
  function applyQueryParams() {
    const params = new URLSearchParams(location.search);
    const status = params.get('status');
    if (status && STATUS_LABEL[status]) $('delivery-status-filter').value = status;
    if (params.get('stale') === '1') $('delivery-stale-only').checked = true;
    // 주문 상세에서 "이 주문 배송 보기"로 넘어온 경우 — 주문번호로 필터, 특정 배송 강조
    if (params.get('orderNo')) $('delivery-keyword').value = params.get('orderNo');
    if (params.get('deliveryId')) focusDeliveryId = Number(params.get('deliveryId'));
  }

  async function load() {
    try {
      rows = await apiGet('/admin/api/commerce/deliveries') || [];
      render();
    } catch (error) { AppToast.error(error.message || '배송 목록을 조회하지 못했습니다.'); }
  }

  function ageHours(isoString) { return isoString ? (Date.now() - new Date(isoString).getTime()) / 3600000 : 0; }
  function isStale(row) { return row.status === 'IN_TRANSIT' && ageHours(row.shippedAt) > STALE_HOURS; }

  function filtered() {
    const keyword = $('delivery-keyword').value.trim().toLowerCase();
    const status = $('delivery-status-filter').value;
    const staleOnly = $('delivery-stale-only').checked;
    return rows.filter(row => {
      if (focusDeliveryId && row.id !== focusDeliveryId) return false;
      if (status && row.status !== status) return false;
      if (staleOnly && !isStale(row)) return false;
      if (!keyword) return true;
      return [row.orderNo, row.buyerName, row.receiverName, row.trackingNumber].some(v => (v || '').toLowerCase().includes(keyword));
    });
  }

  function renderKpis() {
    const set = (id, n) => { const el = $(id); if (el) el.innerHTML = Number(n).toLocaleString('ko-KR') + '<em>건</em>'; };
    set('kpi-dv-preparing', rows.filter(r => r.status === 'PREPARING').length);
    set('kpi-dv-transit', rows.filter(r => r.status === 'IN_TRANSIT').length);
    set('kpi-dv-delivered', rows.filter(r => r.status === 'DELIVERED').length);
    set('kpi-dv-stale', rows.filter(isStale).length);
  }

  function render() {
    renderKpis();
    const list = sorter ? sorter.apply(filtered()) : filtered();
    pagination.setTotal(list.length);
    $('empty-delivery').hidden = list.length > 0;
    $('delivery-rows').innerHTML = pagination.slice(list).map(row => `<tr${focusDeliveryId && row.id === focusDeliveryId ? ' class="row-focus"' : ''}>
      <td><a class="text-link" href="/admin/commerce/orders?orderId=${row.orderId}">${escapeHtml(row.orderNo)}</a><br><small>배송 #${row.id}</small></td>
      <td>${escapeHtml(row.buyerName)}</td>
      <td>${escapeHtml(operationalStoreLabel(row.storeId))}</td>
      <td><strong>${escapeHtml(row.receiverName)}</strong><br><small>${escapeHtml(row.receiverPhone)}</small></td>
      <td><small>${row.zipCode ? '(' + escapeHtml(row.zipCode) + ') ' : ''}${escapeHtml(row.address1)}${row.address2 ? ' ' + escapeHtml(row.address2) : ''}</small>${row.deliveryRequest ? `<br><small class="delivery-request">"${escapeHtml(row.deliveryRequest)}"</small>` : ''}</td>
      <td>${row.carrier ? `${escapeHtml(row.carrier)}<br><small>${escapeHtml(row.trackingNumber)}</small>` : '<small>미발급</small>'}</td>
      <td>${row.status === 'RETURNED' && row.returnReason ? `<span class="status-indicator ${STATUS_TONE[row.status] || ''}">${STATUS_LABEL[row.status] || row.status}</span><br><small class="delivery-request">"${escapeHtml(row.returnReason)}"</small>` : `<span class="status-indicator ${STATUS_TONE[row.status] || ''}">${STATUS_LABEL[row.status] || row.status}</span>`}${isStale(row) ? `<br><small class="stale-badge">⚠ ${Math.floor(ageHours(row.shippedAt) / 24)}일 경과</small>` : ''}${row.status === 'PREPARING' && row.unshippedItemCount > 0 ? `<br><small class="stale-badge" title="창고에서 아직 출고 처리되지 않은 상품이 있습니다.">⚠ 미출고 ${row.unshippedItemCount}건</small>` : ''}</td>
      <td class="actions">${actionCell(row)}</td>
    </tr>`).join('');
    document.querySelectorAll('[data-dispatch-open]').forEach(btn => btn.onclick = () => openDispatchRow(btn.dataset.dispatchOpen));
    document.querySelectorAll('[data-dispatch-confirm]').forEach(btn => btn.onclick = () => confirmDispatch(btn.dataset.dispatchConfirm));
    document.querySelectorAll('[data-complete]').forEach(btn => btn.onclick = () => completeDelivery(btn.dataset.complete));
    document.querySelectorAll('[data-carrier]').forEach(select => select.onchange = () => toggleCustomCarrierInput(select));
  }

  // 반송 처리는 "배송 관리"에서 단독 버튼으로 하지 않는다 — 반송만 찍고 재고/환불이 안 맞는 상태가 되는 걸
  // 막기 위해, 반송이 필요하면 반품 관리(/admin/commerce/returns)에서 반품을 접수·검수하도록 안내한다.
  function actionCell(row) {
    if (row.status === 'RETURNED') return '<small>처리 완료</small>';
    if (row.status === 'DELIVERED') return '<a class="text-link" href="/admin/commerce/returns">반품 관리로 이동</a>';
    if (row.status === 'IN_TRANSIT') return `<button type="button" class="row-icon-btn" data-complete="${row.id}">배송 완료</button>`;
    return `<button type="button" class="row-icon-btn" data-dispatch-open="${row.id}">배송 시작</button>`;
  }

  function toggleCustomCarrierInput(select) {
    const id = select.dataset.carrier;
    const customInput = document.querySelector(`[data-carrier-custom="${id}"]`);
    if (customInput) customInput.hidden = select.value !== CUSTOM_CARRIER;
  }

  function openDispatchRow(id) {
    const cell = document.querySelector(`[data-dispatch-open="${id}"]`)?.closest('td');
    if (!cell) return;
    cell.innerHTML = `<div class="delivery-dispatch-form">
      <select data-carrier="${id}">${CARRIERS.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('')}<option value="${CUSTOM_CARRIER}">직접입력...</option></select>
      <input type="text" data-carrier-custom="${id}" placeholder="택배사명" hidden>
      <input type="text" data-tracking="${id}" placeholder="운송장번호">
      <button type="button" class="row-icon-btn" data-dispatch-confirm="${id}">확인</button>
    </div>`;
    document.querySelector(`[data-carrier="${id}"]`).onchange = event => toggleCustomCarrierInput(event.target);
    document.querySelector(`[data-dispatch-confirm="${id}"]`).onclick = () => confirmDispatch(id);
  }

  async function confirmDispatch(id) {
    const select = document.querySelector(`[data-carrier="${id}"]`);
    const carrier = select?.value === CUSTOM_CARRIER
      ? document.querySelector(`[data-carrier-custom="${id}"]`)?.value.trim()
      : select?.value;
    const trackingNumber = document.querySelector(`[data-tracking="${id}"]`)?.value.trim();
    if (!carrier) return AppToast.error('택배사를 입력해 주세요.');
    if (!trackingNumber) return AppToast.error('운송장번호를 입력해 주세요.');
    const row = rows.find(r => String(r.id) === String(id));
    if (row && row.unshippedItemCount > 0
      && !confirm(`이 배송에 연결된 상품 중 ${row.unshippedItemCount}건이 아직 창고에서 출고 처리되지 않았습니다.\n그래도 배송을 시작하시겠습니까?`)) return;
    try {
      await apiPost(`/admin/api/commerce/deliveries/${id}/dispatch`, { carrier, trackingNumber });
      AppToast.success('배송을 시작했습니다.');
      await load();
    } catch (error) { AppToast.error(error.message || '배송 시작 처리에 실패했습니다.'); }
  }

  async function completeDelivery(id) {
    try {
      await apiPost(`/admin/api/commerce/deliveries/${id}/complete`, {});
      AppToast.success('배송 완료 처리했습니다.');
      await load();
    } catch (error) { AppToast.error(error.message || '배송 완료 처리에 실패했습니다.'); }
  }

  /* ---- 송장 일괄등록 ---- */
  function openBulkDispatchModal() {
    $('bulk-dispatch-file').value = ''; $('bulk-dispatch-text').value = ''; $('bulk-dispatch-result').innerHTML = '';
    $('bulk-dispatch-modal').classList.add('open');
    $('bulk-dispatch-modal').setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
  }
  function closeBulkDispatchModal() {
    $('bulk-dispatch-modal').classList.remove('open');
    $('bulk-dispatch-modal').setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
  }
  function handleBulkDispatchFile() {
    const file = $('bulk-dispatch-file').files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => { $('bulk-dispatch-text').value = String(reader.result || ''); };
    reader.readAsText(file, 'UTF-8');
  }
  // "주문번호,택배사,운송장번호" 한 줄씩 파싱한다. 첫 줄이 헤더처럼 보이면(주문번호 텍스트 포함) 건너뛴다.
  function parseBulkRows(text) {
    return text.split(/\r?\n/).map(line => line.trim()).filter(Boolean)
      .filter(line => !line.includes('주문번호'))
      .map(line => line.split(',').map(v => v.trim()))
      .filter(cols => cols.length >= 3 && cols[0])
      .map(cols => ({ orderNo: cols[0], carrier: cols[1], trackingNumber: cols[2] }));
  }
  async function submitBulkDispatch() {
    const parsedRows = parseBulkRows($('bulk-dispatch-text').value);
    if (!parsedRows.length) return AppToast.error('등록할 행이 없습니다. "주문번호,택배사,운송장번호" 형식으로 입력해 주세요.');
    try {
      const results = await apiPost('/admin/api/commerce/deliveries/bulk-dispatch', { rows: parsedRows });
      const successCount = results.filter(r => r.success).length;
      $('bulk-dispatch-result').innerHTML = `<p>${successCount}건 성공 / ${results.length - successCount}건 실패</p>
        <table class="data-table bulk-dispatch-result-table"><thead><tr><th>주문번호</th><th>결과</th></tr></thead>
        <tbody>${results.map(r => `<tr><td>${escapeHtml(r.orderNo)}</td><td class="${r.success ? 'bulk-success' : 'bulk-fail'}">${r.success ? '✓ 성공' : '✗ ' + escapeHtml(r.message || '실패')}</td></tr>`).join('')}</tbody></table>`;
      if (successCount) await load();
    } catch (error) { AppToast.error(error.message || '송장 일괄등록에 실패했습니다.'); }
  }

  function debounce(fn, wait) { let timer; return () => { clearTimeout(timer); timer = setTimeout(fn, wait); }; }
})();
