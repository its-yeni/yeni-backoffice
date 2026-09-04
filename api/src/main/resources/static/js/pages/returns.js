(function () {
  const $ = id => document.getElementById(id);
  let rows = [], pagination;
  const processing = new Set();
  const requestedRefundStatus = new URLSearchParams(location.search).get('refundStatus');
  const {
    labels: STATUS_LABEL,
    tones: STATUS_TONE,
    responsibilityLabels: RESPONSIBILITY_LABEL,
    refundLabels: REFUND_STATUS_LABEL,
    refundTones: REFUND_STATUS_TONE
  } = CommerceStatusCatalog.returns;
  // 접수/검수 중 상태가 이 시간(시간 단위)보다 오래 머물러 있으면 "지연" 배지를 붙인다. 실제 SLA 값이 아니라
  // 데모용 기준값이라, 운영에 맞게 바꿔 쓸 수 있게 상수 하나로 뺐다.
  const STALE_HOURS = 48;

  let sorter;
  document.addEventListener('DOMContentLoaded', () => {
    pagination = AdminPagination.mount($('return-pagination'), { total: 0, size: 20, onChange: render });
    sorter = AdminTableSort.attach(document.querySelector('.return-table'), { onSort: render });
    $('return-keyword').addEventListener('input', debounce(() => { pagination.reset(); render(); }, 250));
    $('return-status-filter').onchange = () => { pagination.reset(); render(); };
    $('return-stale-only').onchange = () => { pagination.reset(); render(); };
    $('return-filter-reset').onclick = () => {
      $('return-keyword').value = ''; $('return-status-filter').value = ''; $('return-stale-only').checked = false;
      pagination.reset(); render();
    };
    $('return-new-open').onclick = () => openCreateModal();
    document.querySelectorAll('[data-close-return-create]').forEach(el => el.onclick = closeCreateModal);
    document.addEventListener('keydown', event => { if (event.key === 'Escape') closeCreateModal(); });
    $('return-create-submit').onclick = submitCreate;
    applyQueryParams();
    load();
  });

  // 운영 대시보드의 예외 큐("반품 검수 대기" 등)에서 필터가 적용된 채로 들어올 수 있게 쿼리스트링을 읽는다.
  function applyQueryParams() {
    const status = new URLSearchParams(location.search).get('status');
    if (status && STATUS_LABEL[status]) $('return-status-filter').value = status;
  }

  async function load() {
    try {
      rows = await apiGet('/admin/api/commerce/returns') || [];
      render();
      maybeOpenFromQueryParams();
    } catch (error) { AppToast.error(error.message || '반품 목록을 조회하지 못했습니다.'); }
  }

  // 주문 상세 화면의 "반품 접수 →" 링크로 들어온 경우, 주문ID·배송ID를 직접 입력하지 않아도 되도록
  // 쿼리스트링에서 읽어와 모달을 미리 채운 채로 열어준다. 한 번 처리하면 주소를 정리해서 새로고침 시
  // 다시 뜨지 않게 한다.
  function maybeOpenFromQueryParams() {
    const params = new URLSearchParams(location.search);
    const orderId = params.get('orderId');
    if (!orderId) return;
    openCreateModal({ orderId, deliveryId: params.get('deliveryId') });
    history.replaceState(null, '', location.pathname);
  }

  function ageHours(isoString) {
    if (!isoString) return 0;
    return (Date.now() - new Date(isoString).getTime()) / 3600000;
  }
  function isStale(row) {
    if (row.status === 'REQUESTED') return ageHours(row.requestedAt) > STALE_HOURS;
    if (row.status === 'INSPECTING') return ageHours(row.inspectedAt) > STALE_HOURS;
    return false;
  }

  function filtered() {
    const keyword = $('return-keyword').value.trim().toLowerCase();
    const status = $('return-status-filter').value;
    const staleOnly = $('return-stale-only').checked;
    return rows.filter(row => {
      if (status && row.status !== status) return false;
      if (requestedRefundStatus && row.refundStatus !== requestedRefundStatus) return false;
      if (staleOnly && !isStale(row)) return false;
      if (!keyword) return true;
      return [row.orderNo, row.buyerName].some(v => (v || '').toLowerCase().includes(keyword));
    });
  }

  function renderKpis() {
    const set = (id, n) => { const el = $(id); if (el) el.innerHTML = Number(n).toLocaleString('ko-KR') + '<em>건</em>'; };
    set('kpi-rt-requested', rows.filter(r => r.status === 'REQUESTED').length);
    set('kpi-rt-inspecting', rows.filter(r => r.status === 'INSPECTING').length);
    set('kpi-rt-completed', rows.filter(r => r.status === 'COMPLETED').length);
    set('kpi-rt-rejected', rows.filter(r => r.status === 'REJECTED').length);
  }

  function render() {
    renderKpis();
    const list = sorter ? sorter.apply(filtered()) : filtered();
    pagination.setTotal(list.length);
    $('empty-return').hidden = list.length > 0;
    $('return-rows').innerHTML = pagination.slice(list).map(row => `<tr>
      <td>${escapeHtml(row.orderNo)}</td>
      <td>${escapeHtml(row.buyerName)}</td>
      <td>${escapeHtml(operationalStoreLabel(row.storeId))}</td>
      <td>${escapeHtml(row.reason)}${row.status === 'REJECTED' && row.rejectReason ? `<br><small class="delivery-request">반려: "${escapeHtml(row.rejectReason)}"</small>` : ''}</td>
      <td>${escapeHtml(RESPONSIBILITY_LABEL[row.responsibility] || row.responsibility)}</td>
      <td>${formatWon(row.returnShippingFee)}</td>
      <td>${row.refundAmount != null ? formatWon(row.refundAmount) : '<small>확정 전</small>'}${row.refundStatus ? `<br><span class="status-indicator ${REFUND_STATUS_TONE[row.refundStatus] || ''}">${REFUND_STATUS_LABEL[row.refundStatus] || row.refundStatus}</span>` : ''}${row.refundStatus === 'FAILED' && row.refundFailureReason ? `<br><small class="delivery-request" title="${escapeHtml(row.refundFailureReason)}">사유 보기</small>` : ''}</td>
      <td><span class="status-indicator ${STATUS_TONE[row.status] || ''}">${STATUS_LABEL[row.status] || row.status}</span>${isStale(row) ? `<br><small class="stale-badge">⚠ ${Math.floor(ageHours(row.status === 'REQUESTED' ? row.requestedAt : row.inspectedAt) / 24)}일 경과</small>` : ''}</td>
      <td class="actions">${actionCell(row)}</td>
    </tr>`).join('');
    document.querySelectorAll('[data-inspect]').forEach(btn => btn.onclick = () => startInspection(btn.dataset.inspect));
    document.querySelectorAll('[data-complete-open]').forEach(btn => btn.onclick = () => openCompleteRow(btn.dataset.completeOpen));
    document.querySelectorAll('[data-complete-confirm]').forEach(btn => btn.onclick = () => confirmComplete(btn.dataset.completeConfirm));
    document.querySelectorAll('[data-reject-open]').forEach(btn => btn.onclick = () => openRejectRow(btn.dataset.rejectOpen));
    document.querySelectorAll('[data-reject-confirm]').forEach(btn => btn.onclick = () => confirmReject(btn.dataset.rejectConfirm));
    document.querySelectorAll('[data-retry-refund]').forEach(btn => btn.onclick = () => retryRefund(btn.dataset.retryRefund));
  }

  function actionCell(row) {
    if (row.status === 'REQUESTED') return `<button type="button" class="row-icon-btn" data-inspect="${row.id}">검수 시작</button>`;
    if (row.status === 'INSPECTING') return `<button type="button" class="row-icon-btn" data-complete-open="${row.id}">검수 완료</button> <button type="button" class="row-icon-btn" data-reject-open="${row.id}">반려</button>`;
    if (row.status === 'COMPLETED' && row.refundStatus === 'FAILED') return `<button type="button" class="row-icon-btn" data-retry-refund="${row.id}">환불 재시도</button>`;
    return '<small>처리 완료</small>';
  }

  async function retryRefund(id) {
    if (processing.has(String(id))) return;
    processing.add(String(id));
    try {
      await apiPost(`/admin/api/commerce/returns/${id}/retry-refund`, {});
      AppToast.success('환불을 재시도했습니다.');
      await load();
    } catch (error) { AppToast.error(error.message || '환불 재시도에 실패했습니다.'); }
    finally { processing.delete(String(id)); }
  }

  async function startInspection(id) {
    if (processing.has(String(id))) return;
    processing.add(String(id));
    try {
      await apiPost(`/admin/api/commerce/returns/${id}/inspect`, {});
      AppToast.success('검수를 시작했습니다.');
      await load();
    } catch (error) { AppToast.error(error.message || '검수 시작에 실패했습니다.'); }
    finally { processing.delete(String(id)); }
  }

  function openCompleteRow(id) {
    const cell = document.querySelector(`[data-complete-open="${id}"]`)?.closest('td');
    if (!cell) return;
    cell.innerHTML = `<div class="delivery-dispatch-form">
      <input type="number" min="0" data-complete-fee="${id}" placeholder="반송비(원, 비우면 접수값 유지)">
      <button type="button" class="row-icon-btn" data-complete-confirm="${id}">확인</button>
    </div>`;
    document.querySelector(`[data-complete-confirm="${id}"]`).onclick = () => confirmComplete(id);
  }

  async function confirmComplete(id) {
    if (processing.has(String(id))) return;
    processing.add(String(id));
    const feeInput = document.querySelector(`[data-complete-fee="${id}"]`)?.value;
    const returnShippingFee = feeInput === '' || feeInput == null ? null : Number(feeInput);
    try {
      await apiPost(`/admin/api/commerce/returns/${id}/complete`, { returnShippingFee });
      AppToast.success('반품 검수를 완료했습니다. 재고가 복원되고 환불이 처리됩니다.');
      await load();
    } catch (error) { AppToast.error(error.message || '검수 완료 처리에 실패했습니다.'); }
    finally { processing.delete(String(id)); }
  }

  function openRejectRow(id) {
    const cell = document.querySelector(`[data-reject-open="${id}"]`)?.closest('td');
    if (!cell) return;
    cell.innerHTML = `<div class="delivery-dispatch-form">
      <input type="text" data-reject-reason="${id}" placeholder="반려 사유">
      <button type="button" class="row-icon-btn" data-reject-confirm="${id}">확인</button>
    </div>`;
    document.querySelector(`[data-reject-confirm="${id}"]`).onclick = () => confirmReject(id);
  }

  async function confirmReject(id) {
    const reason = document.querySelector(`[data-reject-reason="${id}"]`)?.value.trim();
    if (!reason) return AppToast.error('반려 사유를 입력해 주세요.');
    if (processing.has(String(id))) return;
    processing.add(String(id));
    try {
      await apiPost(`/admin/api/commerce/returns/${id}/reject`, { reason });
      AppToast.success('반품을 반려 처리했습니다.');
      await load();
    } catch (error) { AppToast.error(error.message || '반려 처리에 실패했습니다.'); }
    finally { processing.delete(String(id)); }
  }

  function openCreateModal(prefill) {
    $('return-order-id').value = prefill && prefill.orderId ? prefill.orderId : '';
    $('return-delivery-id').value = prefill && prefill.deliveryId ? prefill.deliveryId : '';
    $('return-reason').value = ''; $('return-responsibility').value = 'CUSTOMER_FAULT'; $('return-shipping-fee').value = '0';
    $('return-create-modal').classList.add('open');
    $('return-create-modal').setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
    if (prefill && prefill.orderId) $('return-reason').focus();
  }

  function closeCreateModal() {
    $('return-create-modal').classList.remove('open');
    $('return-create-modal').setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
  }

  async function submitCreate() {
    const orderId = Number($('return-order-id').value);
    const deliveryIdRaw = $('return-delivery-id').value;
    const reason = $('return-reason').value.trim();
    if (!orderId) return AppToast.error('주문 번호를 입력해 주세요.');
    if (!reason) return AppToast.error('반품 사유를 입력해 주세요.');
    try {
      await apiPost('/admin/api/commerce/returns', {
        orderId,
        deliveryId: deliveryIdRaw ? Number(deliveryIdRaw) : null,
        reason,
        responsibility: $('return-responsibility').value,
        returnShippingFee: Number($('return-shipping-fee').value || 0),
      });
      AppToast.success('반품을 접수했습니다.');
      closeCreateModal();
      await load();
    } catch (error) { AppToast.error(error.message || '반품 접수에 실패했습니다.'); }
  }

  function formatWon(value) { return (Number(value) || 0).toLocaleString('ko-KR') + '원'; }
  function debounce(fn, wait) { let timer; return () => { clearTimeout(timer); timer = setTimeout(fn, wait); }; }
})();
