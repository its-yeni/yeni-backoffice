(function () {
  const $ = id => document.getElementById(id);
  let rows = [], pagination, sorter;
  document.addEventListener('DOMContentLoaded', () => {
    pagination = AdminPagination.mount($('shipment-pagination'), { total: 0, size: 20, onChange: render });
    sorter = AdminTableSort.attach(document.querySelector('.shipment-table'), { onSort: render });
    $('shipment-keyword').addEventListener('input', debounce(() => { pagination.reset(); render(); }, 250));
    $('shipment-filter-reset').onclick = () => { $('shipment-keyword').value = ''; pagination.reset(); render(); };
    load();
  });
  async function load() {
    try { rows = await apiGet('/admin/api/commerce/shipments/pending') || []; render(); }
    catch (error) { rows = []; render(); AppToast.error(error.message || '출고 대기 목록을 조회하지 못했습니다.'); }
  }
  function filtered() {
    const keyword = $('shipment-keyword').value.trim().toLowerCase();
    return keyword ? rows.filter(row => [row.orderNo, row.buyerName, row.sku, row.productName].some(value => String(value || '').toLowerCase().includes(keyword))) : rows;
  }
  function renderKpis() {
    const set = (id, n, unit) => { const el = $(id); if (el) el.innerHTML = Number(n).toLocaleString('ko-KR') + `<em>${unit}</em>`; };
    set('kpi-sh-items', rows.length, '건');
    set('kpi-sh-orders', new Set(rows.map(row => row.orderNo)).size, '건');
    set('kpi-sh-qty', rows.reduce((sum, row) => sum + Number(row.quantity || 0), 0), '개');
  }
  function render() {
    renderKpis();
    const list = sorter ? sorter.apply(filtered()) : filtered();
    pagination.setTotal(list.length);
    $('empty-shipment').hidden = list.length > 0;
    $('shipment-rows').innerHTML = pagination.slice(list).map(row => `<tr data-item="${row.orderItemId}"><td>${escapeHtml(row.orderNo)}</td><td>${escapeHtml(row.buyerName)}</td><td>${escapeHtml(operationalStoreLabel(row.storeId))}</td><td>${escapeHtml(row.productName)}</td><td>${escapeHtml(row.sku)}</td><td>${escapeHtml(row.optionSummary || '-')}</td><td class="number">${Number(row.quantity).toLocaleString('ko-KR')}</td><td class="amount">${money(row.itemAmount)}</td><td class="actions"><button type="button" class="row-icon-btn" data-complete="${row.orderItemId}">출고 완료</button></td></tr>`).join('');
    document.querySelectorAll('[data-complete]').forEach(button => button.onclick = () => complete(button));
  }
  async function complete(button) {
    button.disabled = true;
    try { await apiPost(`/admin/api/commerce/shipments/${button.dataset.complete}/complete`, {}, 'POST'); AppToast.success('출고 완료 처리했습니다.'); await load(); }
    catch (error) { AppToast.error(error.message || '출고 처리에 실패했습니다.'); button.disabled = false; }
  }
  function debounce(fn, wait) { let timer; return () => { clearTimeout(timer); timer = setTimeout(fn, wait); }; }
})();
