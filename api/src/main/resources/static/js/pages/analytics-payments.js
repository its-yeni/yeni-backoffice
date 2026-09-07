(function () {
  const A = window.Analytics, $ = A.$;
  let allRows = [], channel = "";
  A.init(render);

  document.addEventListener("DOMContentLoaded", () => {
    const seg = $("an-channel-seg");
    if (!seg) return;
    seg.querySelectorAll("button").forEach(btn => btn.onclick = () => {
      channel = btn.dataset.channel;
      seg.querySelectorAll("button").forEach(b => b.classList.toggle("is-on", b === btn));
      paint();
    });
  });

  const CH_LABEL = { WEB: "온라인", POS: "매장(POS)" };

  async function render() {
    $("an-refresh").textContent = new Date().toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" });
    try {
      allRows = await A.get("payments");
      paint();
    } catch (error) {
      $("an-kpis").innerHTML = `<div class="an-empty" style="grid-column:1/-1">${A.esc(error.message)}</div>`;
    }
  }

  function paint() {
    const payments = channel ? allRows.filter(r => (r.channelType || "WEB") === channel) : allRows;
    renderKpi(payments);
    renderChannelMatrix(allRows);
    renderApprovalTrend(payments);
    renderMethodAmount(payments);
    renderMethodCount(payments);
  }

  // 채널 × 결제수단 승인금액 교차표 — 채널 필터와 무관하게 항상 전체를 보여준다.
  function renderChannelMatrix(rows) {
    const table = $("an-channel-matrix");
    if (!table) return;
    const channels = [...new Set(rows.map(r => r.channelType || "WEB"))].sort();
    const methods = [...new Set(rows.map(r => r.paymentMethod || "기타"))].sort();
    const cell = (mth, ch) => rows.filter(r => (r.paymentMethod || "기타") === mth && (r.channelType || "WEB") === ch)
      .reduce((s, r) => s + Number(r.approvalAmount || 0), 0);
    const colTotal = ch => rows.filter(r => (r.channelType || "WEB") === ch).reduce((s, r) => s + Number(r.approvalAmount || 0), 0);
    const grand = rows.reduce((s, r) => s + Number(r.approvalAmount || 0), 0);
    table.querySelector("thead").innerHTML = `<tr><th>결제수단</th>${channels.map(c => `<th class="num">${A.esc(CH_LABEL[c] || c)}</th>`).join("")}<th class="num">합계</th></tr>`;
    table.querySelector("tbody").innerHTML = methods.length ? methods.map(m => {
      const rowTotal = channels.reduce((s, c) => s + cell(m, c), 0);
      return `<tr><td>${A.esc(m)}</td>${channels.map(c => `<td class="num">${cell(m, c) ? A.won(cell(m, c)) : "—"}</td>`).join("")}<td class="num">${A.won(rowTotal)}</td></tr>`;
    }).join("") : `<tr><td colspan="${channels.length + 2}" class="an-empty">결제 데이터가 없습니다.</td></tr>`;
    table.querySelector("tfoot").innerHTML = methods.length
      ? `<tr><td>합계</td>${channels.map(c => `<td class="num">${A.won(colTotal(c))}</td>`).join("")}<td class="num">${A.won(grand)}</td></tr>`
      : "";
  }

  function totals(rows) {
    return rows.reduce((s, r) => ({
      req: s.req + r.paymentRequestCount, ap: s.ap + r.approvalCount, amt: s.amt + Number(r.approvalAmount || 0),
      cancel: s.cancel + r.cancelCount, cancelAmt: s.cancelAmt + Number(r.cancelAmount || 0), fail: s.fail + r.failureCount,
    }), { req: 0, ap: 0, amt: 0, cancel: 0, cancelAmt: 0, fail: 0 });
  }

  function renderKpi(payments) {
    const t = totals(payments);
    const apRate = t.req ? (t.ap / t.req * 100) : 0;
    const cxRate = t.ap ? (t.cancel / t.ap * 100) : 0;
    $("an-kpis").innerHTML = `
      <div class="an-kpi"><span>결제 요청</span><strong>${A.num(t.req)}건</strong></div>
      <div class="an-kpi"><span>승인</span><strong>${A.num(t.ap)}건</strong><small>${A.won(t.amt)}</small></div>
      <div class="an-kpi"><span>승인율</span><strong>${apRate.toFixed(1)}%</strong></div>
      <div class="an-kpi"><span>취소</span><strong>${A.num(t.cancel)}건</strong><small>${A.won(t.cancelAmt)}</small></div>
      <div class="an-kpi"><span>취소율 / 실패</span><strong>${cxRate.toFixed(1)}%</strong><small>실패 ${A.num(t.fail)}건</small></div>`;
  }

  function byDate(rows) {
    const m = new Map();
    rows.forEach(r => {
      const a = m.get(r.date) || { req: 0, ap: 0, amt: 0 };
      a.req += r.paymentRequestCount; a.ap += r.approvalCount; a.amt += Number(r.approvalAmount || 0);
      m.set(r.date, a);
    });
    return [...m.entries()].sort((x, y) => x[0] < y[0] ? -1 : 1);
  }
  function renderApprovalTrend(payments) {
    const rows = byDate(payments);
    A.columnChart($("an-approval-trend"), rows.map(([d, a]) => ({ label: d, bar: a.amt, line: a.req ? Math.round(a.ap / a.req * 100) : 0 })), { lineUnit: "%", empty: "기간 내 결제가 없습니다." });
  }

  function byMethod(payments) {
    const m = new Map();
    payments.forEach(r => {
      const k = r.paymentMethod || "기타";
      const a = m.get(k) || { req: 0, ap: 0, amt: 0, cancel: 0, fail: 0 };
      a.req += r.paymentRequestCount; a.ap += r.approvalCount; a.amt += Number(r.approvalAmount || 0);
      a.cancel += r.cancelCount; a.fail += r.failureCount;
      m.set(k, a);
    });
    return [...m.entries()].sort((x, y) => y[1].amt - x[1].amt);
  }
  function renderMethodAmount(payments) {
    A.barChart($("an-method-amount"), byMethod(payments).map(([label, a]) => ({ label, value: a.amt })), { empty: "데이터 없음" });
  }
  function renderMethodCount(payments) {
    const rows = byMethod(payments);
    const t = totals(payments);
    const tbody = $("an-method-count").querySelector("tbody");
    tbody.innerHTML = rows.length ? rows.map(([k, a]) => `<tr>
      <td>${A.esc(k)}</td><td class="num">${A.num(a.req)}</td><td class="num">${A.num(a.ap)}</td>
      <td class="num">${A.num(a.cancel)}</td><td class="num${a.fail ? " warn" : ""}">${A.num(a.fail)}</td>
      <td class="num">${a.req ? (a.ap / a.req * 100).toFixed(1) : "0.0"}%</td></tr>`).join("")
      : `<tr><td colspan="6" class="an-empty">결제 데이터가 없습니다.</td></tr>`;
    $("an-method-count").querySelector("tfoot").innerHTML = rows.length ?
      `<tr><td>합계</td><td class="num">${A.num(t.req)}</td><td class="num">${A.num(t.ap)}</td><td class="num">${A.num(t.cancel)}</td><td class="num">${A.num(t.fail)}</td><td class="num">${t.req ? (t.ap / t.req * 100).toFixed(1) : "0.0"}%</td></tr>` : "";
  }
})();
