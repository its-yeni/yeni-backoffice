(function () {
  const $ = (id) => document.getElementById(id);
  let rows = [], pagination;
  const variantId = new URLSearchParams(location.search).get('variantId');
  const TYPE_LABEL = { RECEIPT: '입고', RESERVE: '주문 예약', RELEASE: '예약 해제', SHIPMENT: '출고 완료', ADJUST_IN: '증가 조정', ADJUST_OUT: '감소 조정', TRANSFER_IN: '이동 입고', TRANSFER_OUT: '이동 출고' };
  const REFERENCE_LABEL = { ORDER: '주문', MANUAL: '수동 작업', SHIPMENT: '출고', PURCHASE_ORDER: '발주', STOCK_COUNT: '재고 실사', RETURN: '반품', TRANSFER: '재고 이동' };

  let sorter;
  document.addEventListener('DOMContentLoaded', () => {
    pagination = AdminPagination.mount($('tx-pagination'), { total: 0, size: 20, onChange: render });
    sorter = AdminTableSort.attach(document.querySelector('.inventory-audit-data-table'), { defaultKey: 'createdAt', defaultDir: 'desc', onSort: render });
    $('tx-keyword').addEventListener('input', debounce(resetAndRender, 250));
    $('type-filter').onchange = resetAndRender;
    $('actor-filter').onchange = resetAndRender;
    if ($('ref-filter')) $('ref-filter').onchange = resetAndRender;
    $('tx-filter-reset').onclick = () => {
      $('tx-keyword').value = ''; $('type-filter').value = ''; $('actor-filter').value = '';
      if ($('ref-filter')) $('ref-filter').value = '';
      resetAndRender();
    };
    load();
  });

  async function load() {
    try {
      const query = variantId ? '?variantId=' + encodeURIComponent(variantId) : '';
      rows = await apiGet('/admin/api/commerce/inventory-transactions' + query) || [];
      render();
    } catch (error) {
      AppToast.error(error.message || '재고 이동 이력을 조회하지 못했습니다.');
    }
  }

  function resetAndRender() { pagination.setPage(1); render(); }
  function filtered() {
    const keyword = $('tx-keyword').value.trim().toLowerCase();
    const type = $('type-filter').value, actor = $('actor-filter').value;
    const ref = $('ref-filter') ? $('ref-filter').value : '';
    return rows.filter((row) => {
      if (type && row.type !== type) return false;
      if (actor && row.actor !== actor) return false;
      if (ref && row.referenceType !== ref) return false;
      if (!keyword) return true;
      return [row.productName, row.productCode, row.sku, row.referenceType, row.referenceId, row.reason]
        .some((value) => String(value || '').toLowerCase().includes(keyword));
    });
  }

  function render() {
    const list = sorter ? sorter.apply(filtered()) : filtered();
    pagination.setTotal(list.length);
    $('empty-tx').hidden = list.length > 0;
    const rowStart = (pagination.getPage() - 1) * pagination.getSize();
    $('tx-rows').innerHTML = pagination.slice(list).map((row, i) => {
      const stockDelta = row.stockAfter - row.stockBefore;
      const reservedDelta = row.reservedAfter - row.reservedBefore;
      const primaryDelta = stockDelta !== 0 ? stockDelta : reservedDelta;
      const reference = referenceHtml(row);
      return `<tr>
        <td class="row-index">${rowStart + i + 1}</td>
        <td>${formatDate(row.createdAt)}</td>
        <td><strong>${escapeHtml(row.productName)}</strong><small>${escapeHtml(row.productCode || '-')} · ${escapeHtml(row.sku)}</small></td>
        <td><span class="inventory-type">${TYPE_LABEL[row.type] || escapeHtml(row.type)}</span></td>
        <td class="number inventory-delta ${primaryDelta > 0 ? 'increase' : primaryDelta < 0 ? 'decrease' : ''}">${primaryDelta > 0 ? '+' : ''}${primaryDelta.toLocaleString('ko-KR')}</td>
        <td class="number inventory-change"><span>${row.stockBefore.toLocaleString('ko-KR')}</span><i>→</i><strong>${row.stockAfter.toLocaleString('ko-KR')}</strong></td>
        <td class="number inventory-change"><span>${row.reservedBefore.toLocaleString('ko-KR')}</span><i>→</i><strong>${row.reservedAfter.toLocaleString('ko-KR')}</strong></td>
        <td>${reference}</td><td>${row.actor === 'SYSTEM' ? '시스템 자동' : row.actor === 'ADMIN' ? '관리자' : escapeHtml(row.actor || '-')}</td><td class="inventory-reason">${escapeHtml(row.reason || '-')}</td>
      </tr>`;
    }).join('');
    $('tx-total').textContent = `${list.length.toLocaleString('ko-KR')}건`;
    $('tx-stock-delta').textContent = `${signed(list.reduce((sum, row) => sum + row.stockAfter - row.stockBefore, 0))}개`;
    $('tx-reserved-delta').textContent = `${signed(list.reduce((sum, row) => sum + row.reservedAfter - row.reservedBefore, 0))}개`;
    $('tx-system-count').textContent = `${list.filter((row) => row.actor === 'SYSTEM').length.toLocaleString('ko-KR')}건`;
  }

  function referenceHtml(row) {
    if (!row.referenceType) return '-';
    const text = `${REFERENCE_LABEL[row.referenceType] || row.referenceType}${row.referenceId ? ' #' + row.referenceId : ''}`;
    return row.referenceType === 'ORDER' && row.referenceId
      ? `<a class="inventory-reference" href="/admin/commerce/orders?orderId=${row.referenceId}">${escapeHtml(text)}</a>`
      : `<span>${escapeHtml(text)}</span>`;
  }
  const signed = (value) => `${value > 0 ? '+' : ''}${value.toLocaleString('ko-KR')}`;
  function formatDate(value) { return value ? new Date(value).toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-'; }
  function debounce(fn, wait) { let timer; return () => { clearTimeout(timer); timer = setTimeout(fn, wait); }; }
})();
