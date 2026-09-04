(function () {
  const $ = id => document.getElementById(id);
  const LABEL = CommerceStatusCatalog.stockCount.labels;
  const TONE = { IN_PROGRESS: 'is-warning', COMPLETED: 'is-success', CANCELED: 'is-danger' };
  let rows = [], stores = [], pagination, sorter, currentDetail = null;

  document.addEventListener('DOMContentLoaded', () => {
    pagination = AdminPagination.mount($('sc-pagination'), { total: 0, size: 20, onChange: render });
    sorter = AdminTableSort.attach(document.querySelector('.sc-table'), { defaultKey: 'countNo', defaultDir: 'desc', onSort: render });
    $('sc-keyword').addEventListener('input', debounce(() => { pagination.reset(); render(); }, 250));
    $('sc-status-filter').onchange = () => { pagination.reset(); render(); };
    $('sc-filter-reset').onclick = () => { $('sc-keyword').value = ''; $('sc-status-filter').value = ''; pagination.reset(); render(); };
    $('sc-new').onclick = openStart;
    $('sc-start-save').onclick = start;
    load();
  });

  async function load() {
    try {
      const [countRows, storeList] = await Promise.all([
        apiGet('/admin/api/commerce/stock-counts'),
        apiGet('/admin/api/commerce/stores'),
      ]);
      rows = countRows || []; stores = (storeList || []).filter(s => s.active);
      renderKpis();
      render();
    } catch (e) { AppToast.error(e.message || '실사 목록을 조회하지 못했습니다.'); }
  }

  function renderKpis() {
    $('kpi-progress').textContent = rows.filter(r => r.status === 'IN_PROGRESS').length;
    $('kpi-completed').textContent = rows.filter(r => r.status === 'COMPLETED').length;
    const last = rows.find(r => r.status === 'COMPLETED');
    $('kpi-diff').textContent = last ? (last.netDifference > 0 ? '+' : '') + last.netDifference : '0';
  }

  function filtered() {
    const kw = $('sc-keyword').value.trim().toLowerCase();
    const st = $('sc-status-filter').value;
    return rows.filter(r => {
      if (st && r.status !== st) return false;
      if (!kw) return true;
      return [r.countNo, r.storeName].some(v => String(v || '').toLowerCase().includes(kw));
    });
  }

  function render() {
    const list = sorter ? sorter.apply(filtered()) : filtered();
    pagination.setTotal(list.length);
    $('sc-empty').hidden = list.length > 0;
    const rowStart = (pagination.getPage() - 1) * pagination.getSize();
    $('sc-rows').innerHTML = pagination.slice(list).map((r, i) => `<tr data-open="${r.id}" class="clickable-row">
      <td class="row-index">${rowStart + i + 1}</td>
      <td><strong>${escapeHtml(r.countNo)}</strong></td>
      <td>${escapeHtml(r.storeName)}</td>
      <td class="number">${r.lineCount}</td>
      <td class="number">${r.countedCount}</td>
      <td class="number">${r.diffCount}</td>
      <td class="number">${r.netDifference > 0 ? '+' : ''}${r.netDifference}</td>
      <td><span class="status-indicator ${TONE[r.status] || ''}">${LABEL[r.status] || r.status}</span></td>
      <td>${r.createdAt ? new Date(r.createdAt).toLocaleDateString('ko-KR') : '-'}</td>
    </tr>`).join('');
    document.querySelectorAll('[data-open]').forEach(tr => tr.onclick = () => openDetail(Number(tr.dataset.open)));
  }

  function openStart() {
    $('sc-store').innerHTML = stores.map(s => `<option value="${s.id}">${escapeHtml(s.storeName)}</option>`).join('');
    $('sc-scope').value = 'ALL'; $('sc-memo').value = '';
    $('sc-start-dialog').showModal();
  }
  async function start() {
    try {
      const created = await apiPost('/admin/api/commerce/stock-counts', {
        storeId: Number($('sc-store').value), scope: $('sc-scope').value, memo: $('sc-memo').value.trim() || null,
      }, 'POST');
      $('sc-start-dialog').close();
      await load();
      openDetail(created.id);
    } catch (e) { AppToast.error(e.message || '실사 시작에 실패했습니다.'); }
  }

  async function openDetail(id) {
    try {
      currentDetail = await apiGet(`/admin/api/commerce/stock-counts/${id}`);
      const d = currentDetail;
      const editable = d.status === 'IN_PROGRESS';
      $('sc-detail-title').textContent = `실사 상세 · ${d.countNo}`;
      $('sc-detail-hint').textContent = editable
        ? '실사 수량을 입력하고 "반영(완료)"을 누르면 차이만큼 재고가 조정됩니다.'
        : `${LABEL[d.status]} · 순차이 ${d.netDifference > 0 ? '+' : ''}${d.netDifference}`;
      $('sc-lines').innerHTML = d.lines.map(l => `<tr data-line="${l.id}">
        <td><strong>${escapeHtml(l.productName)}</strong><br><small class="mono">${escapeHtml(l.sku)}</small>${l.optionSummary && l.optionSummary !== '기본' ? ` · ${escapeHtml(l.optionSummary)}` : ''}</td>
        <td>${escapeHtml(l.category || '-')}</td>
        <td class="number">${l.systemQuantity}</td>
        <td>${editable
          ? `<input type="number" min="0" value="${l.countedQuantity != null ? l.countedQuantity : ''}" placeholder="미입력" data-count style="width:80px">`
          : (l.countedQuantity != null ? l.countedQuantity : '<small>미입력</small>')}</td>
        <td class="number ${l.differenceQuantity < 0 ? 'neg' : ''}">${l.counted ? (l.differenceQuantity > 0 ? '+' : '') + l.differenceQuantity : '-'}</td>
      </tr>`).join('');
      if (editable) {
        document.querySelectorAll('#sc-lines [data-count]').forEach(inp => inp.onchange = () => saveLine(inp));
      }
      $('sc-detail-actions').innerHTML = editable
        ? '<button class="btn btn-light" value="cancel">닫기</button><button class="btn btn-light" id="sc-act-cancel" type="button">실사 취소</button><button class="btn btn-primary" id="sc-act-complete" type="button">반영(완료)</button>'
        : '<button class="btn btn-light" value="cancel">닫기</button>';
      if (editable) {
        $('sc-act-complete').onclick = complete;
        $('sc-act-cancel').onclick = () => { if (confirm('이 실사를 취소하시겠습니까? 재고는 반영되지 않습니다.')) cancel(); };
      }
      $('sc-detail-dialog').showModal();
    } catch (e) { AppToast.error(e.message || '실사 상세를 불러오지 못했습니다.'); }
  }

  async function saveLine(inp) {
    const lineId = Number(inp.closest('tr').dataset.line);
    const value = inp.value.trim();
    if (value === '') return;
    try {
      currentDetail = await apiPost(`/admin/api/commerce/stock-counts/${currentDetail.id}/lines/${lineId}`, { countedQuantity: Number(value) }, 'PUT');
      const row = currentDetail.lines.find(l => l.id === lineId);
      const diffCell = inp.closest('tr').querySelector('td:last-child');
      diffCell.textContent = (row.differenceQuantity > 0 ? '+' : '') + row.differenceQuantity;
      diffCell.classList.toggle('neg', row.differenceQuantity < 0);
    } catch (e) { AppToast.error(e.message || '카운트 저장에 실패했습니다.'); }
  }
  async function complete() {
    try {
      await apiPost(`/admin/api/commerce/stock-counts/${currentDetail.id}/complete`, {}, 'POST');
      $('sc-detail-dialog').close();
      AppToast.success('실사를 반영했습니다. 차이만큼 재고가 조정되었습니다.');
      await load();
    } catch (e) { AppToast.error(e.message || '반영에 실패했습니다.'); }
  }
  async function cancel() {
    try {
      await apiPost(`/admin/api/commerce/stock-counts/${currentDetail.id}/cancel`, {}, 'POST');
      $('sc-detail-dialog').close();
      await load();
    } catch (e) { AppToast.error(e.message || '취소에 실패했습니다.'); }
  }

  function debounce(fn, wait) { let t; return () => { clearTimeout(t); t = setTimeout(fn, wait); }; }
})();
