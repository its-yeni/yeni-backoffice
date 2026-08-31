(function () {
  const $ = id => document.getElementById(id);
  let rows = [], stores = [], variants = [], pagination, sorter;
  const LABEL = { REQUESTED: '이동 요청', IN_TRANSIT: '이동 중', RECEIVED: '입고 완료', CANCELLED: '취소' };
  const TONE = { REQUESTED: '', IN_TRANSIT: 'is-warning', RECEIVED: 'is-success', CANCELLED: 'is-danger' };

  document.addEventListener('DOMContentLoaded', () => {
    pagination = AdminPagination.mount($('transfer-pagination'), { total: 0, size: 20, onChange: render });
    sorter = AdminTableSort.attach(document.querySelector('.transfer-table'), { defaultKey: 'createdAt', defaultDir: 'desc', onSort: render });
    $('transfer-keyword').addEventListener('input', debounce(() => { pagination.reset(); render(); }, 250));
    $('transfer-status-filter').onchange = () => { pagination.reset(); render(); };
    $('transfer-filter-reset').onclick = () => {
      $('transfer-keyword').value = ''; $('transfer-status-filter').value = '';
      pagination.reset(); render();
    };
    $('transfer-new').onclick = () => $('transfer-dialog').showModal();
    $('transfer-save').onclick = save;
    load();
  });

  async function load() {
    try {
      const [transferRows, storeList, variantList] = await Promise.all([
        apiGet('/admin/api/commerce/stock-transfers'),
        apiGet('/admin/api/commerce/stores'),
        apiGet('/admin/api/commerce/variants/inventory'),
      ]);
      rows = transferRows || []; stores = storeList || []; variants = variantList || [];
      fillDialog();
      render();
    } catch (error) { AppToast.error(error.message || '재고 이동 목록을 조회하지 못했습니다.'); }
  }

  function renderKpis() {
    const set = (id, n) => { const el = $(id); if (el) el.innerHTML = Number(n).toLocaleString('ko-KR') + '<em>건</em>'; };
    set('requested-count', rows.filter(r => r.status === 'REQUESTED').length);
    set('transit-count', rows.filter(r => r.status === 'IN_TRANSIT').length);
    set('received-count', rows.filter(r => r.status === 'RECEIVED').length);
  }

  function filtered() {
    const keyword = $('transfer-keyword').value.trim().toLowerCase();
    const status = $('transfer-status-filter').value;
    return rows.filter(row => {
      if (status && row.status !== status) return false;
      if (!keyword) return true;
      return [row.transferNo, row.sourceStoreName, row.destinationStoreName].some(v => String(v || '').toLowerCase().includes(keyword));
    });
  }

  function render() {
    renderKpis();
    const list = sorter ? sorter.apply(filtered()) : filtered();
    pagination.setTotal(list.length);
    $('transfer-empty').hidden = list.length > 0;
    const rowStart = (pagination.getPage() - 1) * pagination.getSize();
    $('transfer-rows').innerHTML = pagination.slice(list).map((row, i) => `<tr>
      <td class="row-index">${rowStart + i + 1}</td>
      <td><strong>${escapeHtml(row.transferNo)}</strong><br><small>${escapeHtml(row.reason || '-')}</small></td>
      <td>${escapeHtml(row.sourceStoreName)}</td>
      <td>${escapeHtml(row.destinationStoreName)}</td>
      <td>${row.items.map(i => {
        const opt = i.optionSummary && i.optionSummary !== 'BASE' ? ` · ${escapeHtml(i.optionSummary)}` : '';
        return `<strong>${escapeHtml(i.productName || i.sku || '-')}</strong>${opt}<br><small class="mono">${escapeHtml(i.sku)}</small> · ${Number(i.quantity).toLocaleString('ko-KR')}개`;
      }).join('<br>')}</td>
      <td><span class="status-indicator ${TONE[row.status] || ''}">${LABEL[row.status] || row.status}</span></td>
      <td>${row.createdAt ? new Date(row.createdAt).toLocaleString('ko-KR') : '-'}</td>
      <td class="actions">${actionCell(row)}</td>
    </tr>`).join('');
    document.querySelectorAll('[data-ship]').forEach(b => b.onclick = () => act(b.dataset.ship, 'ship'));
    document.querySelectorAll('[data-receive]').forEach(b => b.onclick = () => act(b.dataset.receive, 'receive'));
  }

  function actionCell(row) {
    if (row.status === 'REQUESTED') return `<button type="button" class="row-icon-btn" data-ship="${row.id}">출고 확정</button>`;
    if (row.status === 'IN_TRANSIT') return `<button type="button" class="row-icon-btn" data-receive="${row.id}">입고 확인</button>`;
    return '<small>-</small>';
  }

  function fillDialog() {
    const options = stores.map(s => `<option value="${s.id}">${escapeHtml(s.storeName)}</option>`).join('');
    $('source-store').innerHTML = options;
    $('destination-store').innerHTML = options;
    $('transfer-variant').innerHTML = variants.map(v => {
      const opt = v.optionSummary && v.optionSummary !== 'BASE' ? ` · ${v.optionSummary}` : '';
      return `<option value="${v.variantId}">${escapeHtml(v.productName)}${escapeHtml(opt)} — ${escapeHtml(v.sku)} (가용 ${v.availableQuantity}개)</option>`;
    }).join('');
  }

  async function act(id, action) {
    try { await apiPost(`/admin/api/commerce/stock-transfers/${id}/${action}`, {}, 'POST'); await load(); }
    catch (error) { AppToast.error(error.message || '처리에 실패했습니다.'); }
  }

  async function save() {
    try {
      await apiPost('/admin/api/commerce/stock-transfers', {
        sourceStoreId: Number($('source-store').value),
        destinationStoreId: Number($('destination-store').value),
        reason: $('transfer-reason').value,
        items: [{ variantId: Number($('transfer-variant').value), quantity: Number($('transfer-quantity').value) }],
      }, 'POST');
      $('transfer-dialog').close();
      await load();
    } catch (error) { AppToast.error(error.message || '이동 전표 생성에 실패했습니다.'); }
  }

  function debounce(fn, wait) { let timer; return () => { clearTimeout(timer); timer = setTimeout(fn, wait); }; }
})();
