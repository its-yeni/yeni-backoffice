(function () {
  const $ = id => document.getElementById(id);
  const DONUT_COLORS = ["#c2703d", "#e0a878", "#7c9a92", "#b8b0a4", "#8a94a6", "#d9cfc2"];
  let categories = [], products = [], catPage, prodPage;

  const today = new Date();
  $("sa-end").value = today.toISOString().slice(0, 10);
  $("sa-start").value = new Date(today.getTime() - 30 * 864e5).toISOString().slice(0, 10);

  catPage = AdminPagination.mount($("sa-category-pagination"), { total: 0, size: 10, onChange: renderCategories });
  prodPage = AdminPagination.mount($("sa-product-pagination"), { total: 0, size: 10, onChange: renderProducts });
  $("sa-search").onclick = load;
  $("sa-confirmed").onchange = load;

  function pct(v) { return Number(v || 0).toFixed(1) + "%"; }
  const qty = AppFormat.number.bind(AppFormat);
  const won = AppFormat.money.bind(AppFormat);
  function compact(v) {
    const n = Number(v || 0);
    if (Math.abs(n) >= 1e8) return (n / 1e8).toFixed(1).replace(/\.0$/, "") + "억";
    return AppFormat.compactNumber(n);
  }

  async function load() {
    const base = new URLSearchParams({ startDate: $("sa-start").value, endDate: $("sa-end").value });
    if ($("sa-confirmed").checked) base.set("confirmedYn", "true");
    const dateOnly = new URLSearchParams({ startDate: $("sa-start").value, endDate: $("sa-end").value });

    const [summary, breakdown, consistency] = await Promise.all([
      apiGet("/admin/api/sales-analytics/summary?" + base.toString()),
      apiGet("/admin/api/sales-analytics/breakdown?" + base.toString()),
      apiGet("/admin/api/sales-analytics/consistency?" + dateOnly.toString()),
    ]);

    renderSummary(summary);
    renderTrend(summary.daily || []);
    renderDonut(breakdown.categories || []);
    renderConsistency(consistency);

    categories = breakdown.categories || [];
    products = (breakdown.products || []).slice(0, 20);
    catPage.setTotal(categories.length); catPage.setPage(1); renderCategories();
    prodPage.setTotal(products.length); prodPage.setPage(1); renderProducts();
  }

  function renderSummary(s) {
    $("sa-kpi-net").textContent = won(s.netSales);
    $("sa-kpi-orders").textContent = qty(s.orderCount) + "건";
    $("sa-kpi-avg").textContent = won(s.avgOrderAmount);
    $("sa-kpi-cancel").textContent = pct(s.cancelRate);
  }

  /** 일별 매출 콤보 차트: 막대 = 순매출, 선 = 주문건수 (이중 축, 인라인 SVG) */
  function renderTrend(points) {
    const box = $("sa-trend");
    if (points.length && window.CanvasCharts) {
      CanvasCharts.column(box, points, {
        ariaLabel: "일별 매출 및 주문 건수 추이",
        barValue: point => Number(point.netAmount || 0),
        lineValue: point => Number(point.orderCount || 0),
        label: point => String(point.date).slice(5).replace("-", "."),
        lineColor: "#d9954f",
        topLeft: value => compact(value),
        topRight: value => value.toLocaleString("ko-KR") + "건"
      });
      return;
    }
    if (!points.length) { box.innerHTML = '<p class="sa-trend-empty">기간 내 매출 데이터가 없습니다.</p>'; return; }
    const W = 720, H = 210, padT = 12, padB = 22, padL = 4, padR = 4;
    const maxAmt = Math.max(1, ...points.map(p => Math.abs(Number(p.netAmount || 0))));
    const maxCnt = Math.max(1, ...points.map(p => Number(p.orderCount || 0)));
    const baseY = H - padB, plotH = baseY - padT;
    const step = (W - padL - padR) / points.length;
    const bw = Math.max(1, Math.min(26, step - 6));

    const gridY = [0.25, 0.5, 0.75, 1].map(f => {
      const y = baseY - f * plotH;
      return `<line class="grid" x1="0" y1="${y.toFixed(1)}" x2="${W}" y2="${y.toFixed(1)}"/>`;
    }).join("");
    const bars = points.map((p, i) => {
      const v = Number(p.netAmount || 0);
      const h = Math.max(1, (Math.abs(v) / maxAmt) * plotH);
      const x = padL + i * step + (step - bw) / 2;
      const y = v >= 0 ? baseY - h : baseY;
      return `<rect class="bar${v < 0 ? " neg" : ""}" x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${bw.toFixed(1)}" height="${h.toFixed(1)}"><title>${p.date} · 순매출 ${won(v)}</title></rect>`;
    }).join("");
    const linePts = points.map((p, i) => {
      const x = padL + i * step + step / 2;
      const y = baseY - (Number(p.orderCount || 0) / maxCnt) * plotH;
      return [x, y];
    });
    const poly = `<polyline class="oline" points="${linePts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" ")}"/>`;
    const dots = linePts.map(([x, y], i) => `<circle class="odot" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="2.5"><title>${points[i].date} · 주문 ${points[i].orderCount}건</title></circle>`).join("");
    const xLabels = points.length <= 1 ? [0] : [0, Math.floor(points.length / 2), points.length - 1];
    const labels = [...new Set(xLabels)].map(i => {
      const x = padL + i * step + step / 2;
      const anchor = i === 0 ? "start" : i === points.length - 1 ? "end" : "middle";
      return `<text class="tick" x="${x.toFixed(1)}" y="${H - 6}" text-anchor="${anchor}">${String(points[i].date).slice(5).replace("-", ".")}</text>`;
    }).join("");

    box.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" role="img" aria-label="일별 매출 추이">
      ${gridY}
      <line class="axis" x1="0" y1="${baseY}" x2="${W}" y2="${baseY}"/>
      ${bars}${poly}${dots}${labels}
      <text class="tick" x="2" y="${padT}">${compact(maxAmt)}</text>
      <text class="tick" x="${W - 2}" y="${padT}" text-anchor="end">${maxCnt}건</text>
    </svg>`;
  }

  /** 분류별 매출 도넛 (conic-gradient) + 범례 */
  function renderDonut(rows) {
    const box = $("sa-donut");
    const positive = rows.filter(r => Number(r.netAmount || 0) > 0);
    if (!positive.length) { box.innerHTML = '<p class="sa-donut-empty">집계할 매출이 없습니다.</p>'; return; }
    const top = positive.slice(0, 5);
    const restAmount = positive.slice(5).reduce((s, r) => s + Number(r.netAmount || 0), 0);
    const segments = top.map((r, i) => ({ name: r.categoryName, amount: Number(r.netAmount || 0), color: DONUT_COLORS[i] }));
    if (restAmount > 0) segments.push({ name: "기타", amount: restAmount, color: DONUT_COLORS[5] });
    const total = segments.reduce((s, seg) => s + seg.amount, 0);

    let acc = 0;
    const stops = segments.map(seg => {
      const from = (acc / total) * 100;
      acc += seg.amount;
      const to = (acc / total) * 100;
      return `${seg.color} ${from.toFixed(2)}% ${to.toFixed(2)}%`;
    }).join(", ");
    const legend = segments.map(seg => `<div><i style="background:${seg.color}"></i><span>${escapeHtml(seg.name)}</span><b>${((seg.amount / total) * 100).toFixed(1)}%</b></div>`).join("");
    box.innerHTML = `<div class="sa-donut-wrap"><div class="sa-donut-ring" style="background:conic-gradient(${stops})"></div><div class="sa-donut-legend">${legend}</div></div>`;
  }

  function renderConsistency(report) {
    const issues = report.issues || [];
    const summary = $("sa-consistency-summary");
    const wrap = $("sa-consistency-wrap");
    if (issues.length === 0) {
      summary.className = "inline-message is-success";
      summary.textContent = `정합성 OK — 매출 헤더 ${report.checkedHeaders}건, 주문 ${report.checkedOrders}건, 정산서 ${report.checkedStatements}건 모두 금액 일치.`;
      wrap.hidden = true;
      return;
    }
    summary.className = "inline-message is-error";
    summary.textContent = `${issues.length}건의 금액 불일치가 발견되었습니다.`;
    wrap.hidden = false;
    $("sa-consistency-rows").innerHTML = issues.map(i => `<tr>
      <td>${escapeHtml(i.scope)}</td><td class="mono">${escapeHtml(i.reference)}</td><td>${escapeHtml(i.description)}</td>
      <td class="amount">${money(i.expected)}</td><td class="amount">${money(i.actual)}</td>
      <td class="amount"><strong>${money(i.difference)}</strong></td></tr>`).join("");
  }

  function renderCategories() {
    $("sa-category-empty").hidden = categories.length > 0;
    $("sa-category-total").textContent = categories.length.toLocaleString("ko-KR");
    $("sa-category-rows").innerHTML = catPage.slice(categories).map(r => `<tr>
      <td><strong>${escapeHtml(r.categoryName)}</strong></td>
      <td class="amount">${qty(r.quantity)}</td>
      <td class="amount">${money(r.netAmount)}</td>
      <td class="amount">${pct(r.share)}</td>
      <td class="amount">${qty(r.lineCount)}</td></tr>`).join("");
  }

  function renderProducts() {
    $("sa-product-empty").hidden = products.length > 0;
    $("sa-product-total").textContent = products.length.toLocaleString("ko-KR");
    $("sa-product-rows").innerHTML = prodPage.slice(products).map(r => `<tr>
      <td><strong>${escapeHtml(r.productName || "-")}</strong>${r.productId ? ` <span class="mono">#${r.productId}</span>` : ""}</td>
      <td class="amount">${qty(r.quantity)}</td>
      <td class="amount">${money(r.netAmount)}</td>
      <td class="amount">${qty(r.lineCount)}</td></tr>`).join("");
  }

  load();
})();
