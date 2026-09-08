(function () {
  const $ = id => document.getElementById(id);
  const won = AppFormat.money.bind(AppFormat);
  const wonOrDash = v => Number(v) ? won(v) : "-";
  const SRC = { SALES_TRANSACTION: "매출 원장", SETTLEMENT_STATEMENT: "정산 명세" };
  const TYPE = { ASSET: "자산", LIABILITY: "부채", EQUITY: "자본", REVENUE: "수익", EXPENSE: "비용" };

  const today = new Date();
  const iso = d => d.toISOString().slice(0, 10);
  const monthStart = iso(new Date(today.getFullYear(), today.getMonth(), 1));
  $("ac-j-end").value = iso(today);
  $("ac-j-start").value = iso(new Date(today.getTime() - 7 * 864e5));
  $("ac-t-asof").value = iso(today);
  $("ac-i-end").value = iso(today); $("ac-i-start").value = monthStart;
  $("ac-s-end").value = iso(today); $("ac-s-start").value = monthStart;

  let jPagination = AdminPagination.mount($("ac-j-pagination"), { total: 0, size: 20, onChange: loadJournal });

  // 상단 매장 선택이 걸려 있으면(withOperationalStore가 storeId를 붙임) 스코프를 안내한다.
  function refreshScopeNote(storeName) {
    const note = $("ac-scope-note");
    if (storeName) { note.textContent = `매장 한정: ${storeName} — 상단 매장 선택을 "전체 매장"으로 바꾸면 전사 기준으로 봅니다.`; note.hidden = false; }
    else { note.hidden = true; }
  }

  // 탭 전환
  $("ac-tabs").querySelectorAll("button").forEach(btn => btn.onclick = () => {
    $("ac-tabs").querySelectorAll("button").forEach(b => b.classList.toggle("active", b === btn));
    document.querySelectorAll(".ac-panel").forEach(p => p.hidden = p.dataset.panel !== btn.dataset.tab);
    if (btn.dataset.tab === "trial") loadTrial();
    if (btn.dataset.tab === "income") loadIncome();
    if (btn.dataset.tab === "storeincome") loadStoreIncome();
  });

  $("ac-post").onclick = async () => {
    $("ac-post").disabled = true;
    try {
      const r = await apiPost("/admin/api/gl/post-pending", {}, "POST");
      const made = r.createdFromSales + r.createdFromSettlements;
      AppToast.success(made > 0
        ? `분개 ${made}건 전기 완료 (매출 ${r.createdFromSales} · 정산 ${r.createdFromSettlements}, 기전기 ${r.alreadyPosted})`
        : `새로 전기할 분개가 없습니다 (기전기 ${r.alreadyPosted}건)`);
      if (r.unbalancedSkipped) AppToast.error(`대차 불일치로 ${r.unbalancedSkipped}건 건너뜀`);
      await loadJournal();
    } catch (e) {
      AppToast.error(e.message || "분개 전기에 실패했습니다.");
    } finally {
      $("ac-post").disabled = false;
    }
  };

  $("ac-j-search").onclick = () => { jPagination.reset(); loadJournal(); };
  $("ac-t-search").onclick = loadTrial;
  $("ac-i-search").onclick = loadIncome;
  $("ac-s-search").onclick = loadStoreIncome;

  let autoPosted = false;
  async function loadJournal() {
    const p = new URLSearchParams({ from: $("ac-j-start").value, to: $("ac-j-end").value, page: jPagination.getPage() - 1, size: jPagination.getSize() });
    let res = await apiGet("/admin/api/gl/journal-entries?" + p);
    // 전표가 하나도 없으면(데모 첫 진입 등) 이미 확정된 매출·정산을 자동으로 한 번 전기한다. 멱등하므로 안전.
    if (res.totalCount === 0 && !autoPosted) {
      autoPosted = true;
      try {
        const posted = await apiPost("/admin/api/gl/post-pending", {}, "POST");
        if (posted.createdFromSales + posted.createdFromSettlements > 0) {
          res = await apiGet("/admin/api/gl/journal-entries?" + p);
        }
      } catch (e) { /* 실패해도 빈 화면 + 안내로 폴백 */ }
    }
    jPagination.setTotal(res.totalCount);
    const rows = [];
    (res.data || []).forEach(entry => {
      (entry.lines || []).forEach((line, i) => {
        rows.push(`<tr class="${i === 0 ? "ac-j-head" : ""}${line.debit && Number(line.debit) ? "" : " ac-j-credit"}">
          <td>${i === 0 ? entry.entryDate : ""}</td>
          <td>${i === 0 ? `<small>${escapeHtml(entry.storeName || "")}</small>` : ""}</td>
          <td>${i === 0 ? escapeHtml(entry.description) + (entry.balanced ? "" : ' <span class="ac-warn">대차불일치</span>') : ""}</td>
          <td><span class="ac-acct">${escapeHtml(line.accountName)}</span></td>
          <td class="amount">${wonOrDash(line.debit)}</td>
          <td class="amount">${wonOrDash(line.credit)}</td>
          <td>${i === 0 ? `<small>${SRC[entry.sourceType] || entry.sourceType} #${entry.sourceId}</small>` : ""}</td></tr>`);
      });
    });
    $("ac-j-rows").innerHTML = rows.join("");
    $("ac-j-empty").hidden = rows.length > 0;
  }

  async function loadTrial() {
    const res = await apiGet("/admin/api/gl/trial-balance?asOf=" + $("ac-t-asof").value);
    refreshScopeNote(res.storeName);
    $("ac-t-rows").innerHTML = (res.rows || []).map(r => `<tr>
      <td>${escapeHtml(r.accountCode)}</td>
      <td>${escapeHtml(r.accountName)}</td>
      <td><small>${TYPE[r.accountType] || r.accountType}</small></td>
      <td class="amount">${wonOrDash(r.debitTotal)}</td>
      <td class="amount">${wonOrDash(r.creditTotal)}</td>
      <td class="amount"><strong>${wonOrDash(r.debitBalance)}</strong></td>
      <td class="amount"><strong>${wonOrDash(r.creditBalance)}</strong></td></tr>`).join("");
    $("ac-t-empty").hidden = (res.rows || []).length > 0;
    const tf = $("ac-t-total").querySelectorAll("th.amount");
    tf[0].textContent = won(res.totalDebit); tf[1].textContent = won(res.totalCredit);
    tf[2].textContent = ""; tf[3].textContent = "";
    $("ac-t-balance").innerHTML = res.balanced
      ? `<span class="ac-ok">✓ 차변 = 대변 (${won(res.totalDebit)})</span>`
      : `<span class="ac-bad">✗ 대차 불일치: 차변 ${won(res.totalDebit)} / 대변 ${won(res.totalCredit)}</span>`;
  }

  async function loadIncome() {
    const res = await apiGet("/admin/api/gl/income-statement?from=" + $("ac-i-start").value + "&to=" + $("ac-i-end").value);
    refreshScopeNote(res.storeName);
    const line = (label, amount, cls) => `<div class="ac-inc-line ${cls || ""}"><span>${escapeHtml(label)}</span><b>${won(amount)}</b></div>`;
    const sub = rows => rows.map(r => line(r.accountName, r.amount, "ac-inc-sub")).join("");
    $("ac-income").innerHTML = `
      ${res.storeName ? `<p class="ac-inc-scope">매장 한정: <b>${escapeHtml(res.storeName)}</b></p>` : ""}
      <div class="ac-inc-group"><h3>수익</h3>${sub(res.revenues)}${line("매출액 합계", res.totalRevenue, "ac-inc-total")}</div>
      <div class="ac-inc-group"><h3>비용</h3>${sub(res.expenses)}${line("비용 합계", res.totalExpense, "ac-inc-total")}</div>
      <div class="ac-inc-net ${res.netIncome >= 0 ? "pos" : "neg"}"><span>당기순${res.netIncome >= 0 ? "이익" : "손실"}</span><b>${won(Math.abs(res.netIncome))}</b></div>
      <p class="ac-inc-note">${res.from} ~ ${res.to} 기준. 전기된 분개만 반영합니다.</p>`;
  }

  async function loadStoreIncome() {
    const res = await apiGet("/admin/api/gl/income-by-store?from=" + $("ac-s-start").value + "&to=" + $("ac-s-end").value);
    $("ac-s-rows").innerHTML = (res.stores || []).map(s => `<tr>
      <td>${escapeHtml(s.storeName)}</td>
      <td class="amount">${won(s.revenue)}</td>
      <td class="amount">${won(s.expense)}</td>
      <td class="amount"><strong class="${s.netIncome >= 0 ? "ac-pos" : "ac-neg"}">${won(s.netIncome)}</strong></td></tr>`).join("");
    $("ac-s-empty").hidden = (res.stores || []).length > 0;
    const tf = $("ac-s-total").querySelectorAll("th.amount");
    tf[0].textContent = won(res.totalRevenue);
    tf[1].textContent = won(res.totalExpense);
    tf[2].textContent = won(res.totalNetIncome);
  }

  loadJournal();
})();
