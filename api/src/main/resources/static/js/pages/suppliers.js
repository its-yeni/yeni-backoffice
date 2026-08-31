(function () {
  const $ = id => document.getElementById(id);
  let rows = [], pagination, sorter, editingId = null;

  document.addEventListener('DOMContentLoaded', () => {
    pagination = AdminPagination.mount($('supplier-pagination'), { total: 0, size: 20, onChange: render });
    sorter = AdminTableSort.attach(document.querySelector('.supplier-table'), { defaultKey: 'name', defaultDir: 'asc', onSort: render });
    $('supplier-keyword').addEventListener('input', debounce(() => { pagination.reset(); render(); }, 250));
    $('supplier-active-filter').onchange = () => { pagination.reset(); render(); };
    $('supplier-filter-reset').onclick = () => { $('supplier-keyword').value = ''; $('supplier-active-filter').value = ''; pagination.reset(); render(); };
    $('supplier-new').onclick = () => openDialog(null);
    $('supplier-save').onclick = save;
    load();
  });

  async function load() {
    try { rows = await apiGet('/admin/api/commerce/suppliers') || []; render(); }
    catch (e) { AppToast.error(e.message || '공급처 목록을 조회하지 못했습니다.'); }
  }

  function renderKpis() {
    $('kpi-total').textContent = rows.length.toLocaleString('ko-KR');
    const active = rows.filter(r => r.active);
    $('kpi-active').textContent = active.length.toLocaleString('ko-KR');
    const avg = active.length ? Math.round(active.reduce((s, r) => s + r.leadTimeDays, 0) / active.length) : 0;
    $('kpi-lead').innerHTML = avg + '<em>일</em>';
  }

  function filtered() {
    const kw = $('supplier-keyword').value.trim().toLowerCase();
    const act = $('supplier-active-filter').value;
    return rows.filter(r => {
      if (act === 'Y' && !r.active) return false;
      if (act === 'N' && r.active) return false;
      if (!kw) return true;
      return [r.supplierCode, r.name, r.managerName].some(v => String(v || '').toLowerCase().includes(kw));
    });
  }

  function render() {
    renderKpis();
    const list = sorter ? sorter.apply(filtered()) : filtered();
    pagination.setTotal(list.length);
    $('supplier-empty').hidden = list.length > 0;
    const rowStart = (pagination.getPage() - 1) * pagination.getSize();
    $('supplier-rows').innerHTML = pagination.slice(list).map((r, i) => `<tr>
      <td class="row-index">${rowStart + i + 1}</td>
      <td class="mono">${escapeHtml(r.supplierCode)}</td>
      <td><strong>${escapeHtml(r.name)}</strong>${r.memo ? `<br><small>${escapeHtml(r.memo)}</small>` : ''}</td>
      <td>${escapeHtml(r.managerName || '-')}</td>
      <td>${escapeHtml(r.contact || '-')}</td>
      <td class="number">${r.leadTimeDays}일</td>
      <td><span class="status-indicator ${r.active ? 'is-success' : 'is-danger'}">${r.active ? '사용 중' : '미사용'}</span></td>
      <td class="actions">
        <button type="button" class="row-icon-btn" data-edit="${r.id}">수정</button>
        <button type="button" class="row-icon-btn" data-toggle="${r.id}">${r.active ? '중지' : '재개'}</button>
      </td>
    </tr>`).join('');
    document.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => openDialog(rows.find(r => r.id === Number(b.dataset.edit))));
    document.querySelectorAll('[data-toggle]').forEach(b => b.onclick = () => toggle(rows.find(r => r.id === Number(b.dataset.toggle))));
  }

  function openDialog(supplier) {
    editingId = supplier ? supplier.id : null;
    $('supplier-dialog-title').textContent = supplier ? '공급처 수정' : '공급처 등록';
    $('supplier-code').value = supplier ? supplier.supplierCode : '';
    $('supplier-code').disabled = !!supplier;
    $('supplier-name').value = supplier ? supplier.name : '';
    $('supplier-manager').value = supplier ? (supplier.managerName || '') : '';
    $('supplier-contact').value = supplier ? (supplier.contact || '') : '';
    $('supplier-lead').value = supplier ? supplier.leadTimeDays : 14;
    $('supplier-memo').value = supplier ? (supplier.memo || '') : '';
    $('supplier-dialog').showModal();
  }

  async function save() {
    const body = {
      supplierCode: $('supplier-code').value.trim() || null,
      name: $('supplier-name').value.trim(),
      managerName: $('supplier-manager').value.trim(),
      contact: $('supplier-contact').value.trim(),
      leadTimeDays: Number($('supplier-lead').value) || 14,
      memo: $('supplier-memo').value.trim(),
    };
    if (!body.name) return AppToast.error('공급처명을 입력해 주세요.');
    try {
      await apiPost(editingId ? `/admin/api/commerce/suppliers/${editingId}` : '/admin/api/commerce/suppliers', body, editingId ? 'PUT' : 'POST');
      $('supplier-dialog').close();
      AppToast.success('저장했습니다.');
      await load();
    } catch (e) { AppToast.error(e.message || '저장에 실패했습니다.'); }
  }

  async function toggle(supplier) {
    try {
      await apiPost(`/admin/api/commerce/suppliers/${supplier.id}/active`, { active: !supplier.active }, 'PATCH');
      await load();
    } catch (e) { AppToast.error(e.message || '처리에 실패했습니다.'); }
  }

  function debounce(fn, wait) { let t; return () => { clearTimeout(t); t = setTimeout(fn, wait); }; }
})();
