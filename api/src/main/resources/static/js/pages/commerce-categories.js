(function () {
  const $ = id => document.getElementById(id);
  let categories = [], products = [], pagination, dragId = null;
  document.addEventListener("DOMContentLoaded", () => {
    pagination = AdminPagination.mount($("category-pagination"), { total: 0, size: 20, onChange: render });
    $("category-create-form").onsubmit = create;
    $("category-keyword").oninput = resetAndRender;
    $("category-exposure-filter").onchange = resetAndRender;
    $("category-filter-reset").onclick = () => { $("category-keyword").value = ""; $("category-exposure-filter").value = ""; resetAndRender(); };
    if ($("category-search-btn")) $("category-search-btn").onclick = resetAndRender;
    load();
  });
  function resetAndRender() { pagination.setPage(1); render(); }
  async function load() {
    const store = encodeURIComponent(activeStoreCode());
    const [categoryResponse, productResponse] = await Promise.all([fetch(`/admin/api/commerce/categories?storeCode=${store}`), fetch(`/admin/api/commerce/products?size=200&storeCode=${store}`)]);
    categories = await categoryResponse.json();
    products = (await productResponse.json()).items || [];
    render();
  }
  function renderKpis() {
    const set = (id, n) => { const el = $(id); if (el) el.innerHTML = `${Number(n).toLocaleString("ko-KR")}<em>개</em>`; };
    const linked = c => products.filter(p => p.category === c.categoryName).length;
    set("kpi-cat-total", categories.length);
    set("kpi-cat-exposed", categories.filter(c => c.exposed).length);
    set("kpi-cat-linked", categories.reduce((s, c) => s + linked(c), 0));
    set("kpi-cat-empty", categories.filter(c => linked(c) === 0).length);
  }
  function render() {
    renderKpis();
    const keyword = $("category-keyword").value.trim().toLowerCase(), exposure = $("category-exposure-filter").value;
    const filtered = categories.filter(c => (!keyword || c.categoryName.toLowerCase().includes(keyword)) && (!exposure || String(c.exposed) === exposure));
    pagination.setTotal(filtered.length);
    if ($("category-total")) $("category-total").textContent = filtered.length.toLocaleString("ko-KR");
    const start = (pagination.getPage() - 1) * pagination.getSize(), visible = filtered.slice(start, start + pagination.getSize());
    $("category-empty").hidden = visible.length > 0;
    // 정렬(드래그)은 필터링 없이 전체 순서가 화면 순서와 같을 때만 켠다 — 검색/필터가 걸려 있으면
    // 화면에 보이는 이웃과 실제 저장 순서상의 이웃이 달라서 드래그 결과가 기대와 어긋날 수 있다.
    const canReorder = !keyword && !exposure;
    $("category-rows").innerHTML = visible.map(c => {
      const count = products.filter(p => p.category === c.categoryName).length;
      return `<tr class="is-editable-row" draggable="${canReorder}" data-row-id="${c.id}"><td class="drag-column">${canReorder ? '<span class="drag-handle" title="드래그해서 순서 변경">⠿</span>' : ""}</td><td><button type="button" class="category-name-button" data-edit="${c.id}">${escapeHtml(c.categoryName)}</button></td><td class="number">${count.toLocaleString("ko-KR")}</td><td><label class="switch" title="고객 노출"><input type="checkbox" data-exposure="${c.id}" ${c.exposed ? "checked" : ""}><span></span></label></td><td class="number">${c.sortOrder}</td><td>${formatDate(c.updatedAt)}</td><td class="actions"><button type="button" class="category-edit-action" data-edit="${c.id}">수정</button></td></tr>`;
    }).join("");
    document.querySelectorAll("[data-edit]").forEach(b => b.onclick = () => edit(b));
    document.querySelectorAll("#category-rows [data-row-id]").forEach(row => row.onclick = event => {
      if (event.target.closest("button,a,input,select,textarea,label,.drag-handle")) return;
      const button = row.querySelector("[data-edit]"); if (button) edit(button);
    });
    document.querySelectorAll("[data-exposure]").forEach(b => b.onchange = () => { const c = categories.find(x => x.id === Number(b.dataset.exposure)); update(c.id, c.categoryName, b.checked); });
    if (canReorder) document.querySelectorAll("tr[draggable=true]").forEach(row => {
      row.addEventListener("dragstart", () => { dragId = Number(row.dataset.rowId); row.classList.add("dragging"); });
      row.addEventListener("dragend", () => row.classList.remove("dragging"));
      row.addEventListener("dragover", e => e.preventDefault());
      row.addEventListener("drop", e => { e.preventDefault(); if (dragId != null) reorder(dragId, Number(row.dataset.rowId)); });
    });
  }
  // 백엔드가 "한 칸 위로/아래로"만 지원해서, 드래그로 옮긴 거리만큼 그 호출을 반복한다.
  async function reorder(id, targetId) {
    const ids = categories.map(c => c.id), from = ids.indexOf(id), to = ids.indexOf(targetId);
    if (from < 0 || to < 0 || from === to) return;
    const direction = to > from ? "DOWN" : "UP", steps = Math.abs(to - from);
    for (let i = 0; i < steps; i++) if (!(await request(`/admin/api/commerce/categories/${id}/order`, "PATCH", { direction }))) break;
    await load();
  }
  function edit(button) {
    const c = categories.find(x => x.id === Number(button.dataset.edit)), cell = button.closest("td");
    cell.innerHTML = `<form class="category-inline-form"><input maxlength="100" value="${escapeHtml(c.categoryName)}"><button class="btn btn-primary btn-sm">저장</button><button type="button" class="btn btn-text btn-sm">취소</button></form>`;
    const form = cell.querySelector("form"), input = form.querySelector("input");
    form.onsubmit = e => { e.preventDefault(); saveInline(c, input.value); }; form.querySelector("[type=button]").onclick = render; input.focus(); input.select();
  }
  async function saveInline(c, name) { name = name.trim(); if (!name) return show("카테고리명을 입력해 주세요."); await update(c.id, name, c.exposed); }
  async function create(e) { e.preventDefault(); const name = $("new-category-name").value.trim(); if (name && await request("/admin/api/commerce/categories", "POST", { categoryName: name, exposed: true, storeCode: activeStoreCode() })) { $("new-category-name").value = ""; load(); } }
  async function update(id, categoryName, exposed) { if (await request(`/admin/api/commerce/categories/${id}`, "PUT", { categoryName, exposed })) load(); }
  async function request(url, method, body) { const r = await fetch(url, { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }), d = await r.json(); if (!r.ok) { show(d.message || "처리에 실패했습니다."); return false; } show(""); return true; }
  function show(message) { $("category-message").textContent = message; }
  function formatDate(value) { return value ? new Intl.DateTimeFormat("ko-KR", { year:"numeric", month:"2-digit", day:"2-digit", hour:"2-digit", minute:"2-digit" }).format(new Date(value)) : "-"; }
})();
