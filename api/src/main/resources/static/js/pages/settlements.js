(function () {
  let statements = [], pgImports = [], storeSummary = [], current = null, reconciliation = null, pagination;
  let sortKey = 'settlementDate', sortDir = 'desc';
  const selected = new Set();
  const $ = (id) => document.getElementById(id);
  const drawer = $('settlement-detail'), backdrop = $('settlement-backdrop'), next = $('settlement-next');
  const STATUS = {
    DRAFT: { label: '초안', cls: 'draft' },
    CONFIRMED: { label: '확정', cls: 'confirmed' },
    PAID: { label: '지급 완료', cls: 'paid' },
  };
  const statusLabel = (v) => (STATUS[v] || { label: v }).label;
  const signedMoney = (v) => `${Number(v) > 0 ? '+' : ''}${money(v)}`;
  function today(offset) { const d = new Date(); d.setDate(d.getDate() + offset); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }
  function statusBadge(s) {
    if (s.settlementStatus === 'CONFIRMED' && s.scheduledPayoutDate) return '<span class="st-badge ready">지급 준비</span>';
    const st = STATUS[s.settlementStatus] || { label: s.settlementStatus, cls: 'confirmed' };
    return `<span class="st-badge ${st.cls}">${st.label}</span>`;
  }

  pagination = AdminPagination.mount($('settlement-pagination'), { total: 0, size: 20, onChange: render });
  $('settlement-start').value = today(0); $('settlement-end').value = today(0);
  const requestedStatus = new URLSearchParams(location.search).get('status');
  if (STATUS[requestedStatus]) $('settlement-status').value = requestedStatus;

  $('settlement-search').onclick = load;
  $('settlement-run').onclick = run;
  $('settlement-status').onchange = resetAndRender;
  $('settlement-keyword').oninput = resetAndRender;
  $('settlement-reset').onclick = () => { $('settlement-keyword').value = ''; $('settlement-status').value = ''; $('settlement-start').value = today(0); $('settlement-end').value = today(0); load(); };
  $('settlement-detail-close').onclick = close; backdrop.onclick = close;
  $('settlement-select-all').onchange = (e) => { visibleRows().forEach(s => e.target.checked ? selected.add(s.id) : selected.delete(s.id)); render(); };
  $('download-list-csv').onclick = () => downloadCsv();
  $('download-store-csv').onclick = () => downloadStoreCsv();
  document.querySelectorAll('.settlement-table th.sortable').forEach(th => th.onclick = () => {
    const k = th.dataset.sort;
    if (sortKey === k) sortDir = sortDir === 'asc' ? 'desc' : 'asc'; else { sortKey = k; sortDir = 'asc'; }
    render();
  });

  async function load() {
    const params = new URLSearchParams({ startDate: $('settlement-start').value, endDate: $('settlement-end').value });
    const [statementResult, importResult] = await Promise.all([apiGet('/admin/api/settlements?' + params), apiGet('/admin/api/pg-reconciliation').catch(() => [])]);
    statements = statementResult || []; pgImports = importResult || [];
    render(); syncRunButton(); renderAlert(); renderDemoCompletion();
    apiGet('/admin/api/settlements/store-summary?' + params).then(rows => { storeSummary = rows || []; renderStoreSummary(); }).catch(() => {});
    const requested = Number(new URLSearchParams(location.search).get('statementId'));
    if (requested && statements.some((it) => it.id === requested)) open(requested);
  }

  const todayStatement = () => statements.find((it) => it.settlementDate === today(0));
  function syncRunButton() { $('settlement-run').textContent = todayStatement() ? '오늘 정산 보기' : '오늘 정산 초안 생성'; }
  function resetAndRender() { pagination.setPage(1); render(); }
  function relevantImports() { const s = $('settlement-start').value, e = $('settlement-end').value; return pgImports.filter(i => (!s || i.businessDate >= s) && (!e || i.businessDate <= e)); }
  function externalFor(st) { return pgImports.find(i => i.businessDate === st.settlementDate && i.mid === st.mid) || null; }
  const unresolvedOf = (i) => Number(i.unresolvedCount ?? i.mismatchCount ?? 0);

  function renderAlert() {
    const imports = relevantImports();
    const mismatches = imports.reduce((sum, i) => sum + unresolvedOf(i), 0);
    const dates = imports.filter(i => unresolvedOf(i) > 0).map(i => i.businessDate);
    const alert = $('settlement-workflow-alert');
    if (!imports.length) { alert.hidden = false; alert.innerHTML = '<strong>외부 PG 자료가 없습니다.</strong><span>정산일과 MID가 같은 CSV를 먼저 가져와 내부 원장과 비교하세요.</span><a href="/admin/payment-operations/settlements/reconciliation">PG 파일 가져오기 →</a>'; }
    else if (mismatches) { alert.hidden = false; alert.innerHTML = `<strong>PG 대사 미해결 ${mismatches}건 (영업일 ${dates.join(', ')})</strong><span>해당 영업일 정산만 대사 처리 후 확정할 수 있습니다. 다른 영업일에는 영향 없음.</span><a href="/admin/payment-operations/settlements/reconciliation">대사 처리 →</a>`; }
    else alert.hidden = true;
  }

  function renderStoreSummary() {
    const tb = $('store-summary-rows'); if (!tb) return;
    tb.innerHTML = storeSummary.length ? storeSummary.map(r => `<tr>
      <td><strong>${escapeHtml(r.storeName)}</strong></td>
      <td class="amount">${r.statementCount}건</td>
      <td class="amount">${money(r.grossAmount)}</td>
      <td class="amount">${money(Number(r.feeAmount) + Number(r.vatAmount))}</td>
      <td class="amount"><strong>${money(r.netAmount)}</strong></td>
      <td class="amount">${money(r.paidAmount)}</td>
      <td><small>초안 ${r.draftCount} · 확정 ${r.confirmedCount} · 지급 ${r.paidCount}</small></td></tr>`).join('')
      : '<tr><td colspan="7"><small>조회 기간에 정산 명세가 없습니다.</small></td></tr>';
  }

  function visibleRows() {
    const status = $('settlement-status').value, keyword = $('settlement-keyword').value.trim().toLowerCase();
    let list = statements.filter((it) => (!status || it.settlementStatus === status)
      && (!keyword || [it.id, it.mid, it.pgCompany].some((v) => String(v || '').toLowerCase().includes(keyword))));
    const dir = sortDir === 'asc' ? 1 : -1;
    list = [...list].sort((a, b) => {
      const av = a[sortKey], bv = b[sortKey];
      if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * dir;
      return String(av).localeCompare(String(bv), 'ko') * dir;
    });
    return list;
  }

  function render() {
    document.querySelectorAll('.settlement-table th.sortable').forEach(th => {
      th.classList.toggle('asc', th.dataset.sort === sortKey && sortDir === 'asc');
      th.classList.toggle('desc', th.dataset.sort === sortKey && sortDir === 'desc');
    });
    const visible = visibleRows();
    pagination.setTotal(visible.length);
    const start = (pagination.getPage() - 1) * pagination.getSize();
    const pageRows = visible.slice(start, start + pagination.getSize());
    $('settlement-rows').innerHTML = pageRows.map((it, i) => `<tr data-statement="${it.id}" class="${selected.has(it.id) ? 'is-selected' : ''}">
      <td class="checkbox-col"><input type="checkbox" data-sel="${it.id}" ${selected.has(it.id) ? 'checked' : ''}></td>
      <td class="row-index">${start + i + 1}</td>
      <td>${it.settlementDate}</td>
      <td>#${it.id}</td>
      <td>${escapeHtml(it.pgCompany)}</td>
      <td class="mono">${escapeHtml(it.mid)}</td>
      <td class="amount">${money(it.saleAmount)}</td>
      <td class="amount">${money(it.cancelAmount)}</td>
      <td class="amount">${money(Number(it.feeAmount) + Number(it.vatAmount))}</td>
      <td class="amount"><strong>${money(it.netAmount)}</strong></td>
      <td>${statusBadge(it)}</td>
      <td class="row-menu-col">${rowMenu(it)}</td>
    </tr>`).join('');
    $('settlement-empty').hidden = visible.length > 0;
    $('settlement-total-label').textContent = `총 ${visible.length.toLocaleString('ko-KR')}건` + (selected.size ? ` · ${selected.size}건 선택` : '');
    $('settlement-count').textContent = visible.length.toLocaleString('ko-KR') + '건';
    $('settlement-sale').textContent = money(visible.reduce((s, it) => s + Number(it.saleAmount || 0), 0));
    $('settlement-fee').textContent = money(visible.reduce((s, it) => s + Number(it.feeAmount || 0) + Number(it.vatAmount || 0), 0));
    $('settlement-net').textContent = money(visible.reduce((s, it) => s + Number(it.netAmount || 0), 0));
    $('settlement-select-all').checked = pageRows.length > 0 && pageRows.every(s => selected.has(s.id));

    $('settlement-rows').querySelectorAll('[data-statement]').forEach((row) => row.onclick = (e) => {
      if (e.target.closest('.row-menu') || e.target.closest('input')) return;
      open(Number(row.dataset.statement));
    });
    $('settlement-rows').querySelectorAll('[data-sel]').forEach(box => box.onchange = () => {
      const id = Number(box.dataset.sel);
      box.checked ? selected.add(id) : selected.delete(id);
      render();
    });
    bindRowMenus();
  }

  function rowMenu(it) {
    const actions = ['<button type="button" data-act="open">상세 보기</button>'];
    if (it.settlementStatus === 'DRAFT') actions.push('<button type="button" class="primary" data-act="open">정산 확정하기</button>');
    if (it.settlementStatus === 'CONFIRMED') actions.push('<button type="button" class="primary" data-act="open">지급 처리하기</button>');
    actions.push('<button type="button" data-act="csv">이 명세 CSV</button>');
    return `<div class="row-menu"><button type="button" data-menu="${it.id}">⋮</button><div hidden>${actions.join('')}</div></div>`;
  }
  function bindRowMenus() {
    $('settlement-rows').querySelectorAll('[data-menu]').forEach(btn => btn.onclick = (e) => {
      e.stopPropagation();
      const panel = btn.nextElementSibling;
      document.querySelectorAll('.row-menu > div').forEach(d => { if (d !== panel) d.hidden = true; });
      panel.hidden = !panel.hidden;
      panel.querySelectorAll('button').forEach(b => b.onclick = (ev) => {
        ev.stopPropagation(); panel.hidden = true;
        const id = Number(btn.dataset.menu);
        if (b.dataset.act === 'open') open(id);
        else if (b.dataset.act === 'csv') downloadCsv(statements.filter(s => s.id === id));
      });
    });
    document.addEventListener('click', () => document.querySelectorAll('.row-menu > div').forEach(d => d.hidden = true), { once: true });
  }

  /* ---------- CSV 다운로드 (클라이언트 생성) ---------- */
  function csvDownload(name, header, rows) {
    const csv = '﻿' + [header.join(','), ...rows.map(r => r.map(v => `"${String(v ?? '').replace(/"/g, '""')}"`).join(','))].join('\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    a.download = name; a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }
  function downloadCsv(rows) {
    const list = rows || visibleRows();
    if (!list.length) return AppToast.error('내보낼 정산 명세가 없습니다.');
    csvDownload(`settlements-${today(0)}.csv`, ['정산일', '정산ID', 'PG', 'MID', '승인매출', '취소금액', '수수료VAT', '최종정산액', '상태'],
      list.map(it => [it.settlementDate, it.id, it.pgCompany, it.mid, it.saleAmount, it.cancelAmount, Number(it.feeAmount) + Number(it.vatAmount), it.netAmount, statusLabel(it.settlementStatus)]));
  }
  function downloadStoreCsv() {
    if (!storeSummary.length) return AppToast.error('매장별 요약 데이터가 없습니다.');
    csvDownload(`settlement-store-summary-${today(0)}.csv`, ['매장', '명세건수', '정산대상매출', '수수료VAT', '최종정산액', '지급완료액', '초안', '확정', '지급'],
      storeSummary.map(r => [r.storeName, r.statementCount, r.grossAmount, Number(r.feeAmount) + Number(r.vatAmount), r.netAmount, r.paidAmount, r.draftCount, r.confirmedCount, r.paidCount]));
  }

  async function run() {
    const button = $('settlement-run'), existing = todayStatement();
    if (existing) return open(existing.id);
    button.disabled = true;
    try {
      const statement = await apiPost('/admin/api/settlements/batch/run', {}, 'POST'); await load();
      AppToast.success('오늘 정산 초안을 생성했습니다.'); await open(statement.id);
    } catch (error) {
      if (error.response?.code === 'SETTLEMENT_DUPLICATE_EXECUTION') { await load(); AppToast.show('오늘 정산 배치가 이미 생성되어 기존 명세를 표시합니다.'); if (todayStatement()) await open(todayStatement().id); return; }
      AppToast.error(error.message);
    } finally { button.disabled = false; }
  }

  async function open(id) {
    const page = await apiGet(`/admin/api/settlements/${id}`);
    current = page.statement; reconciliation = page.reconciliation;
    const s = current, r = reconciliation, fees = page.feeDetails || [], details = page.details || [], logs = page.logs || [];
    const reasons = r?.blockingReasons || [], external = externalFor(current), categories = page.categoryBreakdown || [];
    const externalUnresolved = external ? unresolvedOf(external) : 0;
    $('settlement-detail-title').textContent = `정산 #${s.id}`;
    $('settlement-detail-body').innerHTML = `
      <section class="drawer-summary"><span class="transaction-status ${s.settlementStatus === 'PAID' ? 'success' : s.settlementStatus === 'CONFIRMED' ? 'neutral' : 'warning'}">${statusLabel(s.settlementStatus)}</span><strong>${money(s.netAmount)}</strong><p>${s.settlementDate} · ${escapeHtml(s.mid)}</p></section>
      <section class="settlement-progress" aria-label="정산 처리 단계">${['DRAFT', 'CONFIRMED', 'PAID'].map((state, i) => `<div class="${state === s.settlementStatus ? 'current' : ''} ${['DRAFT', 'CONFIRMED', 'PAID'].indexOf(s.settlementStatus) > i ? 'done' : ''}"><span>${i + 1}</span><strong>${statusLabel(state)}</strong></div>`).join('')}</section>
      <section><div class="drawer-section-heading"><h3>내부 계산 검증</h3><strong class="reconciliation-result ${r?.confirmable ? 'matched' : 'mismatched'}">${r?.confirmable ? '계산 일치' : '확정 차단'}</strong></div>
        <div class="reconciliation-grid"><div><span>매출 원장</span><strong>${r?.ledgerMatched ? '일치' : '불일치'}</strong><small>차액 ${signedMoney(r?.grossDifference || 0)}</small></div><div><span>수수료 정책</span><strong>${r?.feeMatched ? '일치' : '불일치'}</strong><small>차액 ${signedMoney(Number(r?.feeDifference || 0) + Number(r?.vatDifference || 0))}</small></div><div><span>최종 지급액</span><strong>${r?.netMatched ? '일치' : '불일치'}</strong><small>차액 ${signedMoney(r?.netDifference || 0)}</small></div></div>
        ${reasons.length ? `<div class="reconciliation-block"><strong>내부 계산을 확인해야 합니다.</strong>${reasons.map((x) => `<p>${escapeHtml(x)}</p>`).join('')}</div>` : '<p class="reconciliation-help">내부 원장·수수료 정책·지급액 계산이 일치합니다.</p>'}</section>
      <section><div class="drawer-section-heading"><h3>외부 PG 파일 대사</h3><a href="/admin/payment-operations/settlements/reconciliation">대사 화면 →</a></div>${external ? `<div class="external-recon-status ${externalUnresolved ? 'has-error' : 'is-ready'}"><strong>${externalUnresolved ? `미해결 ${externalUnresolved}건` : '전체 거래 일치'}</strong><span>${escapeHtml(external.fileName)} · ${external.totalCount}건 · ${new Date(external.createdAt).toLocaleString('ko-KR')}</span></div>` : '<div class="external-recon-status is-missing"><strong>해당 정산일의 PG 파일이 없습니다.</strong><span>정산일과 MID가 일치하는 외부 자료를 가져와야 확정 근거가 완성됩니다.</span></div>'}</section>
      <section><h3>계산 근거</h3><div class="settlement-calculation"><div><span>승인 매출</span><strong>${money(s.saleAmount)}</strong></div><div><span>취소 반영</span><strong>${money(s.cancelAmount)}</strong></div><div><span>원장 합계</span><strong>${money(r?.ledgerGrossAmount ?? s.grossAmount)}</strong></div><div><span>PG 수수료</span><strong>− ${money(s.feeAmount)}</strong></div><div><span>수수료 VAT</span><strong>− ${money(s.vatAmount)}</strong></div><div><span>가감 조정</span><strong>${signedMoney(s.adjustmentAmount || 0)}</strong></div><div><span>지급 보류</span><strong>− ${money(s.holdAmount || 0)}</strong></div><div class="total"><span>최종 정산액</span><strong>${money(s.netAmount)}</strong></div></div></section>
      ${s.settlementStatus === 'DRAFT' ? `<section class="settlement-edit"><div class="drawer-section-heading"><h3>지급 조정</h3><span>초안에서만 변경 가능</span></div><div class="settlement-form-grid"><label>가감 조정액(원)<input id="settlement-adjustment" type="number" step="1" value="${Number(s.adjustmentAmount || 0)}"></label><label>지급 보류액(원)<input id="settlement-hold" type="number" min="0" step="1" value="${Number(s.holdAmount || 0)}"></label><label>지급 예정일<input id="settlement-payout-date" type="date" value="${s.scheduledPayoutDate || ''}"></label><label class="wide">조정 사유<input id="settlement-adjustment-reason" maxlength="200" placeholder="예: PG 프로모션 차감, 이전 정산 오차 보정"></label></div><button class="btn btn-secondary" id="settlement-adjustment-save">조정 반영</button></section>`
        : `<section><h3>지급 정보</h3><dl class="drawer-meta"><div><dt>지급 예정일</dt><dd>${s.scheduledPayoutDate || '미지정'}</dd></div><div><dt>지급 참조번호</dt><dd>${escapeHtml(s.payoutReference || '지급 전')}</dd></div><div><dt>지급 계좌</dt><dd>${escapeHtml(s.payoutAccountMasked || '미입력')}</dd></div><div><dt>실제 지급시각</dt><dd>${s.paidAt ? new Date(s.paidAt).toLocaleString('ko-KR') : '지급 전'}</dd></div></dl>
        ${s.settlementStatus === 'CONFIRMED' ? `<div class="payout-form">
          <label>지급 참조번호 *<input id="settlement-payout-reference" maxlength="80" placeholder="예: 20260828-TRX-004821"><span class="field-hint">실제 송금 시 은행에서 받은 이체(거래) 번호 또는 지급 배치 ID. 정산금이 실제로 나갔다는 증빙입니다. (3자 이상)</span></label>
          <label>지급 계좌(마스킹)<input id="settlement-payout-account" maxlength="60" placeholder="예: 신한 ***-**-1234"><span class="field-hint">전체 계좌번호가 아니라 마스킹된 형태로만 기록합니다. (선택)</span></label>
        </div>` : ''}</section>`}
      <section><div class="drawer-section-heading"><h3>포함 거래</h3><span>${r?.transactionCount || details.length}건</span></div><div class="settlement-detail-table"><div class="head"><span>원장 ID</span><span>구분</span><span>거래금액</span><span>정산액</span></div>${details.map((it) => `<a href="/admin/payment-operations/sales-ledger?ledgerId=${it.salesId}"><span>#${it.salesId}</span><span>${it.saleType}</span><span>${money(it.saleAmount)}</span><span>${money(it.netAmount)}</span></a>`).join('') || '<p>포함된 거래가 없습니다.</p>'}</div></section>
      ${categories.length ? `<section><div class="drawer-section-heading"><h3>분류별 정산</h3><span>${categories.length}개 분류</span></div><div class="settlement-detail-table"><div class="head"><span>분류</span><span>매출</span><span>수수료</span><span>정산액</span></div>${categories.map((c) => `<div class="row"><span>${escapeHtml(c.categoryName)}</span><span>${money(c.saleAmount)}</span><span>− ${money(c.feeAmount)}</span><span>${money(c.netAmount)}</span></div>`).join('')}</div></section>` : ''}
      <section><h3>수수료 정책 스냅샷</h3><dl class="drawer-meta">${fees.map((f) => `<div><dt>정책 #${f.feePolicyId} · ${Number(f.feeRate)}%</dt><dd>${money(Number(f.feeAmount) + Number(f.vatAmount))}</dd></div>`).join('') || '<div><dt>적용 수수료</dt><dd>없음</dd></div>'}</dl></section>
      <section><div class="drawer-section-heading"><h3>처리 이력</h3><span>최근순</span></div><ol class="settlement-history">${logs.map((log) => `<li><time>${new Date(log.loggedAt).toLocaleString('ko-KR')}</time><strong>${escapeHtml(log.actionType)}</strong><p>${escapeHtml(log.message || '')}</p></li>`).join('') || '<li><p>기록된 처리 이력이 없습니다.</p></li>'}</ol></section>`;

    next.hidden = s.settlementStatus === 'PAID';
    next.textContent = s.settlementStatus === 'DRAFT' ? '대사 확인 후 정산 확정' : '지급 완료 처리';
    const blocked = s.settlementStatus === 'DRAFT' && (!r?.confirmable || !external || externalUnresolved > 0);
    next.disabled = blocked;
    next.title = blocked ? (!external ? '외부 PG 파일 대사가 필요합니다.' : externalUnresolved ? `PG 대사 미해결 ${externalUnresolved}건을 먼저 해소/제외해야 합니다.` : reasons.join(' ')) : '';
    next.onclick = advance;
    const adj = $('settlement-adjustment-save'); if (adj) adj.onclick = saveAdjustment;
    backdrop.hidden = false; drawer.classList.add('open');
  }

  async function advance() {
    if (!current) return;
    const action = current.settlementStatus === 'DRAFT' ? 'confirm' : 'pay';
    if (action === 'confirm' && !reconciliation?.confirmable) return AppToast.error('내부 계산 불일치를 먼저 해소해야 정산을 확정할 수 있습니다.');
    const external = externalFor(current);
    if (action === 'confirm' && !external) return AppToast.error('같은 정산일과 MID의 외부 PG 파일을 먼저 대사해 주세요.');
    if (action === 'confirm' && unresolvedOf(external) > 0) return AppToast.error(`PG 대사 미해결 ${unresolvedOf(external)}건을 PG 정산 대사 화면에서 해결 또는 제외 처리해야 합니다.`);

    let payload = {};
    if (action === 'pay') {
      const ref = ($('settlement-payout-reference')?.value || '').trim();
      const acc = ($('settlement-payout-account')?.value || '').trim();
      const refInput = $('settlement-payout-reference');
      if (ref.length < 3) { refInput?.classList.add('invalid'); return AppToast.error('지급 참조번호를 3자 이상 입력해 주세요. (은행 이체번호 또는 지급 배치 ID)'); }
      if (/\d{6,}/.test(acc) && !acc.includes('*')) return AppToast.error('지급 계좌는 전체 번호가 아니라 마스킹된 형태로 입력해 주세요. (예: 신한 ***-**-1234)');
      refInput?.classList.remove('invalid');
      payload = { payoutReference: ref, payoutAccountMasked: acc };
    }
    const message = action === 'confirm' ? '대사 결과가 일치합니다. 확정 후에는 대상 거래와 금액을 변경할 수 없습니다. 계속할까요?' : '실제 송금을 확인한 뒤에만 완료 처리해야 합니다. 지급 완료 상태로 변경할까요?';
    if (!confirm(message)) return;
    next.disabled = true;
    try {
      await apiPost(`/admin/api/settlements/${current.id}/${action}`, payload, 'POST');
      AppToast.success(action === 'confirm' ? '정산을 확정했습니다.' : '지급 완료로 처리했습니다.');
      await load(); await open(current.id);
    } catch (e) { AppToast.error(e.message); }
    finally { next.disabled = false; }
  }

  async function saveAdjustment() {
    const button = $('settlement-adjustment-save');
    const payload = {
      adjustmentAmount: Number($('settlement-adjustment').value || 0),
      holdAmount: Number($('settlement-hold').value || 0),
      scheduledPayoutDate: $('settlement-payout-date').value || null,
      reason: $('settlement-adjustment-reason').value.trim(),
    };
    if (!Number.isFinite(payload.adjustmentAmount) || !Number.isFinite(payload.holdAmount)) return AppToast.error('조정액·보류액은 숫자만 입력할 수 있습니다.');
    if (payload.holdAmount < 0) return AppToast.error('지급 보류액은 0원 이상이어야 합니다.');
    if (!payload.reason) return AppToast.error('조정 사유를 입력해 주세요. (감사 기록에 남습니다)');
    button.disabled = true;
    try {
      await apiPost(`/admin/api/settlements/${current.id}/adjust`, payload, 'POST');
      AppToast.success('지급 조정을 반영했습니다.'); await load(); await open(current.id);
    } catch (e) { AppToast.error(e.message); }
    finally { button.disabled = false; }
  }

  function close() { drawer.classList.remove('open'); backdrop.hidden = true; current = null; reconciliation = null; }

  /* ---------- 데모 완료 패널 (기존 로직 유지) ---------- */
  async function renderDemoCompletion() {
    const params = new URLSearchParams(location.search);
    let demo; try { demo = JSON.parse(sessionStorage.getItem('yeni-demo-flow') || 'null'); } catch (e) { demo = null; }
    let panel = document.getElementById('demo-completion');
    if (params.get('demo') !== '1' || !demo?.orderNo) { panel?.remove(); return; }
    if (!panel) { panel = document.createElement('section'); panel.id = 'demo-completion'; panel.className = 'demo-completion'; $('settlement-workflow-alert').before(panel); }
    panel.innerHTML = '<div class="demo-completion-loading">시연 거래의 운영 반영 결과를 확인하고 있습니다.</div>';
    const safeGet = (url, fb) => apiGet(url).catch(() => fb);
    const [orders, payments, ledgerPage] = await Promise.all([
      safeGet('/admin/api/commerce/orders', []),
      safeGet('/admin/api/payment-operations/payments?includeUnassigned=true', []),
      safeGet('/admin/api/sales-ledger?' + new URLSearchParams({ startDate: demo.businessDate || $('settlement-start').value, endDate: demo.businessDate || $('settlement-end').value, keyword: demo.orderNo, page: '0', size: '20' }), { data: [] }),
    ]);
    const order = (orders || []).find(x => x.orderNo === demo.orderNo);
    const payment = (payments || []).find(x => String(x.id) === String(demo.paymentId));
    const ledger = (ledgerPage?.data || []).find(x => x.orderNo === demo.orderNo);
    const imports = pgImports.filter(x => !demo.businessDate || x.businessDate === demo.businessDate);
    const matchedImport = imports.find(x => unresolvedOf(x) === 0);
    const mismatchedImport = imports.find(x => unresolvedOf(x) > 0);
    const statement = statements.find(x => !demo.businessDate || x.settlementDate === demo.businessDate);
    const step = (kind, label, description, href, action) => ({ kind, label, description, href, action });
    const steps = [
      step(order ? 'done' : 'attention', '주문 생성', order ? `${order.orderNo}` : '주문 원본을 찾지 못했습니다.', `/admin/commerce/orders?orderId=${order?.id || ''}`, '주문 원본'),
      step(payment ? 'done' : 'attention', 'Mock 결제', payment ? `결제 #${payment.id}` : '결제 거래를 찾지 못했습니다.', `/admin/payment-operations?paymentId=${demo.paymentId || ''}`, 'PG 거래'),
      step(ledger ? 'done' : 'attention', '매출 원장', ledger ? `SALE 원장 #${ledger.id}` : '매출 원장 반영을 확인해 주세요.', `/admin/payment-operations/sales-ledger?keyword=${encodeURIComponent(demo.orderNo)}`, '원장 확인'),
      step(matchedImport ? 'done' : mismatchedImport ? 'attention' : 'pending', '외부 PG 대사', matchedImport ? `${matchedImport.fileName} · 전체 일치` : mismatchedImport ? `미해결 ${unresolvedOf(mismatchedImport)}건` : '같은 영업일 PG 파일이 필요합니다.', `/admin/payment-operations/settlements/reconciliation`, '대사 확인'),
      step(statement ? (statement.settlementStatus === 'DRAFT' ? 'pending' : 'done') : 'pending', '정산 명세', statement ? `#${statement.id} · ${statusLabel(statement.settlementStatus)}` : '정산 초안 생성 필요', statement ? `/admin/payment-operations/settlements?demo=1&statementId=${statement.id}` : '/admin/payment-operations/settlements?demo=1', statement ? '명세 열기' : '정산 생성'),
    ];
    const completed = steps.filter(x => x.kind === 'done').length;
    panel.innerHTML = `<header><div><small>PORTFOLIO DEMO RESULT</small><h2>${completed === steps.length ? '거래가 정산까지 연결되었습니다.' : '생성한 거래를 운영 데이터에서 추적합니다.'}</h2></div><div class="demo-completion-score"><strong>${completed}/${steps.length}</strong><span>확인 완료</span></div></header>
      <ol>${steps.map((x, i) => `<li class="${x.kind}"><span class="demo-step-state">${x.kind === 'done' ? '완료' : x.kind === 'attention' ? '확인 필요' : '대기'}</span><div><small>0${i + 1}</small><strong>${x.label}</strong><p>${escapeHtml(x.description)}</p></div><a href="${x.href}">${x.action} →</a></li>`).join('')}</ol>`;
  }

  load();
})();
