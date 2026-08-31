(function () {
  if (!window.Analytics || !window.CanvasCharts) return;
  Analytics.columnChart = function (element, points, options) {
    const opts = options || {};
    if (!points.length) { element.innerHTML = `<p class="an-empty">${Analytics.esc(opts.empty || "표시할 데이터가 없습니다.")}</p>`; return; }
    CanvasCharts.column(element, points, {
      ariaLabel: "기간별 금액 및 건수 추이",
      label: row => Analytics.shortDate(row.label),
      topLeft: value => Analytics.compact(value),
      topRight: value => Analytics.num(value) + (opts.lineUnit || "")
    });
  };
  Analytics.barChart = function (element, rows, options) {
    const opts = options || {};
    if (!rows.length) { element.innerHTML = `<p class="an-empty">${Analytics.esc(opts.empty || "표시할 데이터가 없습니다.")}</p>`; return; }
    CanvasCharts.horizontal(element, rows, { total: opts.total, valueLabel: Analytics.compact });
  };
})();
