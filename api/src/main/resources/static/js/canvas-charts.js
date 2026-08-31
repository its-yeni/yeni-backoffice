window.CanvasCharts = (function () {
  const font = '11px "Pretendard", "Noto Sans KR", sans-serif';
  const color = { primary: "#ad5835", line: "#667085", grid: "#eef0f3", axis: "#dfe3e8", text: "#8a94a3", negative: "#e4a5a0" };

  function create(host, height, label) {
    host.innerHTML = "";
    const canvas = document.createElement("canvas");
    const width = Math.max(320, host.clientWidth || 720);
    const ratio = Math.max(1, window.devicePixelRatio || 1);
    canvas.className = "app-canvas-chart";
    canvas.setAttribute("role", "img");
    canvas.setAttribute("aria-label", label || "데이터 차트");
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    canvas.style.width = width + "px";
    canvas.style.height = height + "px";
    host.appendChild(canvas);
    const ctx = canvas.getContext("2d");
    ctx.scale(ratio, ratio);
    ctx.font = font;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    return { ctx, width, height };
  }

  function label(ctx, value, x, y, align) {
    ctx.fillStyle = color.text;
    ctx.textAlign = align || "left";
    ctx.fillText(String(value), x, y);
  }

  function column(host, rows, options) {
    const opts = options || {};
    const H = opts.height || 220, top = 18, bottom = 28, side = 8;
    const chart = create(host, H, opts.ariaLabel || "기간별 추이 차트"), ctx = chart.ctx, W = chart.width;
    const barOf = opts.barValue || (row => Number(row.bar || 0));
    const lineOf = opts.lineValue || (row => row.line == null ? null : Number(row.line));
    const maxBar = Math.max(1, ...rows.map(row => Math.abs(barOf(row))));
    const hasLine = rows.some(row => lineOf(row) != null);
    const maxLine = hasLine ? Math.max(1, ...rows.map(row => lineOf(row) || 0)) : 1;
    const base = H - bottom, plot = base - top, step = (W - side * 2) / rows.length;
    const barWidth = Math.max(1, Math.min(30, step - 6));
    ctx.strokeStyle = color.grid; ctx.lineWidth = 1;
    [0.25, 0.5, 0.75, 1].forEach(f => { const y = base - f * plot; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke(); });
    ctx.strokeStyle = color.axis; ctx.beginPath(); ctx.moveTo(0, base); ctx.lineTo(W, base); ctx.stroke();
    rows.forEach((row, i) => {
      const value = barOf(row), h = Math.max(1, Math.abs(value) / maxBar * plot);
      const x = side + i * step + (step - barWidth) / 2;
      ctx.fillStyle = value < 0 ? color.negative : (opts.barColor || color.primary);
      ctx.fillRect(x, value < 0 ? base : base - h, barWidth, h);
    });
    if (hasLine) {
      const points = rows.map((row, i) => [side + i * step + step / 2, base - (lineOf(row) || 0) / maxLine * plot]);
      ctx.strokeStyle = opts.lineColor || color.line; ctx.lineWidth = 1.6; ctx.beginPath();
      points.forEach(([x, y], i) => i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)); ctx.stroke();
      points.forEach(([x, y]) => { ctx.fillStyle = "#fff"; ctx.beginPath(); ctx.arc(x, y, 2.7, 0, Math.PI * 2); ctx.fill(); ctx.stroke(); });
    }
    const ticks = rows.length <= 1 ? [0] : [...new Set([0, Math.floor(rows.length / 2), rows.length - 1])];
    ticks.forEach(i => label(ctx, (opts.label || (row => row.label))(rows[i]), side + i * step + step / 2, H - 7, i === 0 ? "left" : i === rows.length - 1 ? "right" : "center"));
    if (opts.topLeft) label(ctx, opts.topLeft(maxBar), 2, 11, "left");
    if (hasLine && opts.topRight) label(ctx, opts.topRight(maxLine), W - 2, 11, "right");
  }

  function horizontal(host, rows, options) {
    const opts = options || {}, rowH = 30, H = rows.length * rowH + 8;
    const chart = create(host, H, opts.ariaLabel || "항목별 비교 차트"), ctx = chart.ctx, W = chart.width;
    const max = Math.max(1, ...rows.map(row => Math.abs(Number(row.value || 0))));
    const total = opts.total || rows.reduce((sum, row) => sum + Number(row.value || 0), 0);
    const labelW = Math.min(130, W * .25), valueW = Math.min(140, W * .28), barMax = W - labelW - valueW - 8;
    rows.forEach((row, i) => {
      const y = i * rowH + 4, value = Number(row.value || 0), width = Math.max(1, Math.abs(value) / max * barMax);
      ctx.fillStyle = "#475467"; ctx.textAlign = "left"; ctx.fillText(String(row.label), 4, y + 19);
      ctx.fillStyle = opts.barColor || color.primary; ctx.fillRect(4 + labelW, y + 5, width, rowH - 14);
      const share = total ? ` (${(value / total * 100).toFixed(1)}%)` : "";
      ctx.fillStyle = "#344054"; ctx.textAlign = "right"; ctx.fillText((opts.valueLabel ? opts.valueLabel(value) : value.toLocaleString("ko-KR")) + share, W - 4, y + 19);
    });
  }

  function area(host, rows, options) {
    const opts = options || {}, H = opts.height || 150, top = 12, bottom = 25, side = 16;
    const chart = create(host, H, opts.ariaLabel || "매출 흐름 차트"), ctx = chart.ctx, W = chart.width;
    const valueOf = opts.value || (row => Number(row.value || 0)), max = Math.max(1, ...rows.map(valueOf));
    const step = (W - side * 2) / Math.max(rows.length - 1, 1), base = H - bottom;
    const points = rows.map((row, i) => [side + i * step, base - valueOf(row) / max * (base - top)]);
    ctx.beginPath(); points.forEach(([x, y], i) => i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)); ctx.lineTo(points.at(-1)[0], base); ctx.lineTo(points[0][0], base); ctx.closePath(); ctx.fillStyle = "rgba(173,88,53,.08)"; ctx.fill();
    ctx.beginPath(); points.forEach(([x, y], i) => i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)); ctx.strokeStyle = opts.lineColor || color.primary; ctx.lineWidth = 2; ctx.stroke();
    points.forEach(([x, y]) => { ctx.fillStyle = "#fff"; ctx.beginPath(); ctx.arc(x, y, 3, 0, Math.PI * 2); ctx.fill(); ctx.stroke(); });
    if (rows.length) { label(ctx, opts.label(rows[0]), side, H - 6, "left"); label(ctx, opts.label(rows.at(-1)), W - side, H - 6, "right"); }
  }
  return { column, horizontal, area };
})();
