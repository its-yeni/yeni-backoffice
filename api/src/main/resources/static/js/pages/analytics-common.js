/* 분석 화면 공통 — 필터 바 / 분석 API 호출 / 인라인 SVG 차트 헬퍼.
   각 페이지 스크립트는 window.Analytics.init(render) 를 호출한다. */
window.Analytics = (function () {
  const $ = id => document.getElementById(id);
  const esc = v => String(v ?? "").replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const won = AppFormat.money.bind(AppFormat);
  const num = AppFormat.number.bind(AppFormat);
  const pct = v => Number(v || 0).toFixed(1) + "%";
  const compact = v => {
    const n = Number(v || 0);
    if (Math.abs(n) >= 1e8) return (n / 1e8).toFixed(1).replace(/\.0$/, "") + "억";
    return AppFormat.compactNumber(n);
  };
  const ymd = d => d.toISOString().slice(0, 10);
  const shortDate = s => String(s).slice(5).replace("-", ".");

  function currentFilter() {
    const p = new URLSearchParams();
    if ($("an-start")?.value) p.set("startDate", $("an-start").value);
    if ($("an-end")?.value) p.set("endDate", $("an-end").value);
    if ($("an-channel")?.value) p.set("channel", $("an-channel").value);
    // 매장 범위는 상단바 전역 선택기(전체/매장)를 따른다. 로컬 an-store 는 그 보조.
    const localStore = $("an-store")?.value;
    const globalStore = typeof window.activeStoreId === "function" ? window.activeStoreId() : 0;
    if (localStore) p.set("storeId", localStore);
    else if (globalStore) p.set("storeId", String(globalStore));
    if ($("an-method")?.value) p.set("paymentMethod", $("an-method").value);
    return p;
  }

  async function fetchJson(url) {
    const scoped = typeof window.withOperationalStore === "function" ? window.withOperationalStore(url) : url;
    const res = await fetch(scoped);
    if (!res.ok) throw new Error("분석 데이터를 불러오지 못했습니다.");
    return res.json();
  }
  function get(resource, extra) {
    const p = currentFilter();
    if (extra) Object.entries(extra).forEach(([k, v]) => p.set(k, v));
    const qs = p.toString();
    return fetchJson("/api/analytics/" + resource + (qs ? "?" + qs : ""));
  }

  async function initFilterBar() {
    const today = new Date();
    if ($("an-end") && !$("an-end").value) $("an-end").value = ymd(today);
    if ($("an-start") && !$("an-start").value) $("an-start").value = ymd(new Date(today.getTime() - 29 * 864e5));
    if ($("an-store")) {
      // 매장은 상단바 전역 선택기로 통일한다. 중복 필터는 숨긴다.
      const wrap = $("an-store").closest("label") || $("an-store");
      wrap.style.display = "none";
      $("an-store").value = "";
    }
    if ($("an-method")) {
      try {
        const rows = await fetchJson("/api/analytics/payments");
        const methods = [...new Set(rows.map(r => r.paymentMethod).filter(Boolean))].sort();
        if (methods.length) $("an-method").innerHTML = '<option value="">전체</option>' +
          methods.map(m => `<option value="${esc(m)}">${esc(m)}</option>`).join("");
      } catch (ignore) { /* keep default options */ }
    }
  }

  function init(render) {
    document.addEventListener("DOMContentLoaded", async () => {
      await initFilterBar();
      if ($("an-filter-apply")) $("an-filter-apply").onclick = render;
      document.querySelectorAll("[data-an-days]").forEach(button => button.onclick = () => {
        const today = new Date();
        if ($("an-end")) $("an-end").value = ymd(today);
        if ($("an-start")) $("an-start").value = ymd(new Date(today.getTime() - Number(button.dataset.anDays) * 864e5));
        render();
      });
      document.querySelectorAll('[data-an-range="month"]').forEach(button => button.onclick = () => {
        const today = new Date();
        if ($("an-start")) $("an-start").value = ymd(new Date(today.getFullYear(), today.getMonth(), 1));
        if ($("an-end")) $("an-end").value = ymd(today);
        render();
      });
      if ($("an-filter-reset")) $("an-filter-reset").onclick = () => {
        const today = new Date();
        if ($("an-start")) $("an-start").value = ymd(new Date(today.getTime() - 29 * 864e5));
        if ($("an-end")) $("an-end").value = ymd(today);
        ["an-channel", "an-store", "an-method"].forEach(id => { if ($(id)) $(id).value = ""; });
        render();
      };
      render();
    });
  }

  /* ── 차트 ─────────────────────────────────────────────────────────────────── */

  /** 세로 막대(+ 선택적 라인). points: [{label, bar, line?}] */
  function columnChart(el, points, opts) {
    opts = opts || {};
    if (!points.length) { el.innerHTML = `<p class="an-empty">${esc(opts.empty || "표시할 데이터가 없습니다.")}</p>`; return; }
    const W = 720, H = 210, padT = 12, padB = 22, padX = 4;
    const maxBar = Math.max(1, ...points.map(p => Math.abs(p.bar || 0)));
    const hasLine = points.some(p => p.line != null);
    const maxLine = hasLine ? Math.max(1, ...points.map(p => p.line || 0)) : 1;
    const baseY = H - padB, plotH = baseY - padT;
    const step = (W - padX * 2) / points.length;
    const bw = Math.max(1, Math.min(30, step - 6));
    const grid = [0.25, 0.5, 0.75, 1].map(f => `<line class="grid" x1="0" y1="${(baseY - f * plotH).toFixed(1)}" x2="${W}" y2="${(baseY - f * plotH).toFixed(1)}"/>`).join("");
    const bars = points.map((p, i) => {
      const h = Math.max(1, (Math.abs(p.bar || 0) / maxBar) * plotH);
      const x = padX + i * step + (step - bw) / 2;
      return `<rect class="bar${opts.compare ? " cmp" : ""}" x="${x.toFixed(1)}" y="${(baseY - h).toFixed(1)}" width="${bw.toFixed(1)}" height="${h.toFixed(1)}"><title>${esc(p.label)} · ${won(p.bar)}</title></rect>`;
    }).join("");
    let line = "";
    if (hasLine) {
      const pts = points.map((p, i) => [padX + i * step + step / 2, baseY - (p.line || 0) / maxLine * plotH]);
      line = `<polyline class="oline" points="${pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" ")}"/>` +
        pts.map(([x, y], i) => `<circle class="odot" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="2.5"><title>${esc(points[i].label)} · ${num(points[i].line)}${opts.lineUnit || ""}</title></circle>`).join("");
    }
    const ticks = points.length <= 1 ? [0] : [...new Set([0, Math.floor(points.length / 2), points.length - 1])];
    const labels = ticks.map(i => {
      const x = padX + i * step + step / 2;
      const anchor = i === 0 ? "start" : i === points.length - 1 ? "end" : "middle";
      return `<text class="tick" x="${x.toFixed(1)}" y="${H - 6}" text-anchor="${anchor}">${esc(shortDate(points[i].label))}</text>`;
    }).join("");
    el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${grid}<line class="axis" x1="0" y1="${baseY}" x2="${W}" y2="${baseY}"/>${bars}${line}${labels}<text class="tick" x="2" y="${padT}">${esc(compact(maxBar))}</text>${hasLine ? `<text class="tick" x="${W - 2}" y="${padT}" text-anchor="end">${num(maxLine)}${esc(opts.lineUnit || "")}</text>` : ""}</svg>`;
  }

  /** 가로 막대. rows: [{label, value}] */
  function barChart(el, rows, opts) {
    opts = opts || {};
    if (!rows.length) { el.innerHTML = `<p class="an-empty">${esc(opts.empty || "표시할 데이터가 없습니다.")}</p>`; return; }
    const rowH = 30, padL = 4, padR = 4, W = 720, H = rows.length * rowH + 8;
    const max = Math.max(1, ...rows.map(r => Math.abs(r.value || 0)));
    const total = opts.total || rows.reduce((s, r) => s + Number(r.value || 0), 0);
    const labelW = 110, valueW = 120, barMax = W - padL - padR - labelW - valueW;
    const body = rows.map((r, i) => {
      const y = i * rowH + 4;
      const w = Math.max(1, (Math.abs(r.value || 0) / max) * barMax);
      const share = total ? ` (${((r.value || 0) / total * 100).toFixed(1)}%)` : "";
      return `<text class="hbar-label" x="${padL}" y="${y + rowH / 2 + 4}">${esc(r.label)}</text>
        <rect class="bar" x="${padL + labelW}" y="${y + 5}" width="${w.toFixed(1)}" height="${rowH - 14}"><title>${esc(r.label)} · ${won(r.value)}</title></rect>
        <text class="hbar-value" x="${W - padR}" y="${y + rowH / 2 + 4}" text-anchor="end">${esc(compact(r.value))}${esc(share)}</text>`;
    }).join("");
    el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" style="height:${H}px" preserveAspectRatio="none">${body}</svg>`;
  }

  return { $, esc, won, num, pct, compact, shortDate, init, get, fetchJson, columnChart, barChart };
})();
