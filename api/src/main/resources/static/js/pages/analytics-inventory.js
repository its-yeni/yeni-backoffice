(function () {
  const A = window.Analytics, $ = A.$;
  let lowRows = [], lowPager = null;
  A.init(render);

  async function render() {
    $("an-refresh").textContent = new Date().toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" });
    try {
      const rows = await A.get("inventory");
      renderKpi(rows);
      renderCategory(rows);
      renderSold(rows);
      renderLowTable(rows);
    } catch (error) {
      $("an-kpis").innerHTML = `<div class="an-empty" style="grid-column:1/-1">${A.esc(error.message)}</div>`;
    }
  }

  function renderKpi(rows) {
    const total = rows.length;
    const low = rows.filter(r => r.isLowStock && r.currentStock > 0).length;
    const soldOut = rows.filter(r => r.currentStock <= 0).length;
    const normal = total - low - soldOut;
    const incoming = rows.filter(r => r.incomingQuantity && r.incomingQuantity > 0).length;
    $("an-kpis").innerHTML = `
      <div class="an-kpi"><span>전체 SKU</span><strong>${A.num(total)}</strong></div>
      <div class="an-kpi"><span>정상재고</span><strong>${A.num(normal)}</strong></div>
      <div class="an-kpi"><span>재고부족</span><strong class="${low ? "" : ""}">${A.num(low)}</strong></div>
      <div class="an-kpi"><span>품절</span><strong>${A.num(soldOut)}</strong></div>
      <div class="an-kpi"><span>입고 예정</span><strong>${A.num(incoming)}</strong></div>`;
  }

  function renderCategory(rows) {
    const m = new Map();
    rows.forEach(r => m.set(r.category || "미분류", (m.get(r.category || "미분류") || 0) + Number(r.currentStock || 0)));
    A.barChart($("an-category-chart"), [...m.entries()].sort((a, b) => b[1] - a[1]).map(([label, value]) => ({ label, value })), { empty: "데이터 없음" });
  }

  function renderSold(rows) {
    const top = [...rows].filter(r => r.soldLast30Days > 0).sort((a, b) => b.soldLast30Days - a.soldLast30Days).slice(0, 10);
    A.barChart($("an-sold-chart"), top.map(r => ({
      label: (r.productName || "-").slice(0, 12) + (r.optionName && r.optionName !== "기본" ? " " + r.optionName : ""),
      value: r.soldLast30Days,
    })), { empty: "최근 30일 판매 이력이 없습니다." });
  }

  function renderLowTable(rows) {
    lowRows = rows.filter(r => r.isLowStock).sort((a, b) => a.currentStock - b.currentStock);
    $("an-low-summary").textContent = `재고부족·품절 ${lowRows.length}건 / 전체 ${rows.length} SKU`;
    if (!lowPager && window.AdminPagination && $("an-low-pagination")) {
      lowPager = AdminPagination.mount($("an-low-pagination"), { inline: true, size: 20, total: lowRows.length, onChange: paintLowRows });
    }
    if (lowPager) lowPager.setTotal(lowRows.length);
    paintLowRows();
  }

  function paintLowRows() {
    const page = lowPager ? lowPager.slice(lowRows) : lowRows;
    $("an-low-table").querySelector("tbody").innerHTML = lowRows.length ? page.map(r => `<tr>
      <td>${A.esc(r.productName)}</td><td>${A.esc(r.optionName || "-")}</td>
      <td style="font-family:monospace;font-size:11px">${A.esc(r.sku)}</td><td>${A.esc(r.category || "-")}</td><td>${A.esc(r.storeName || "-")}</td>
      <td class="num warn">${A.num(r.currentStock)}</td><td class="num">${A.num(r.safetyStock)}</td>
      <td class="num">${A.num(r.soldLast30Days)}</td><td class="num">${A.num(r.incomingQuantity || 0)}</td></tr>`).join("")
      : `<tr><td colspan="9" class="an-empty">재고 부족 상품이 없습니다.</td></tr>`;
  }
})();
