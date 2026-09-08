(function () {
  const $ = id => document.getElementById(id);
  let all = [], pagination;

  const ymd = d => d.toISOString().slice(0, 10);
  $("rh-end").value = ymd(new Date());
  $("rh-start").value = ymd(new Date(Date.now() - 6 * 864e5));

  pagination = AdminPagination.mount($("rh-pagination"), { total: 0, size: 20, onChange: render });
  $("rh-search").onclick = load;
  $("rh-keyword").addEventListener("keydown", e => { if (e.key === "Enter") load(); });
  $("rh-reset").onclick = () => { $("rh-keyword").value = ""; $("rh-end").value = ymd(new Date()); $("rh-start").value = ymd(new Date(Date.now() - 6 * 864e5)); load(); };

  function fmt(v) { return v ? new Date(v).toLocaleString("ko-KR", { year: "2-digit", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : "-"; }

  function filtered() {
    const kw = $("rh-keyword").value.trim().toLowerCase();
    const s = $("rh-start").value, e = $("rh-end").value;
    return all.filter(r => {
      const day = (r.createdAt || "").slice(0, 10);
      if (s && day < s) return false;
      if (e && day > e) return false;
      return !kw || (r.productName || "").toLowerCase().includes(kw) || (r.sku || "").toLowerCase().includes(kw);
    });
  }

  function render() {
    const list = filtered();
    pagination.setTotal(list.length);
    $("rh-count").textContent = list.length.toLocaleString("ko-KR") + "건";
    $("rh-qty").textContent = "+" + list.reduce((s, r) => s + Number(r.quantity || 0), 0).toLocaleString("ko-KR");
    $("rh-rows").innerHTML = pagination.slice(list).map(r => `<tr>
      <td>${fmt(r.createdAt)}</td>
      <td>${escapeHtml(r.productName || "-")}</td>
      <td class="mono">${escapeHtml(r.sku || "-")}</td>
      <td class="number">+${Number(r.quantity || 0).toLocaleString("ko-KR")}</td>
      <td class="number">${Number(r.stockAfter || 0).toLocaleString("ko-KR")}</td>
      <td>${escapeHtml(r.reason || "-")}</td>
    </tr>`).join("");
    $("rh-empty").hidden = list.length > 0;
  }

  async function load() {
    try {
      all = await apiGet("/admin/api/commerce/inventory-transactions?type=RECEIPT") || [];
    } catch (e) { AppToast.error(e.message || "입고 내역을 불러오지 못했습니다."); all = []; }
    pagination.setPage(1);
    render();
  }

  load();
})();
