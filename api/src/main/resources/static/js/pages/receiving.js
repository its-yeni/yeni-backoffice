(function () {
  const $ = id => document.getElementById(id);
  const RECEIPT_TYPES = ["발주입고", "반품입고", "기타"];

  let variants = [], categories = [], stores = [], history = [];
  let submitting = false;
  // 옵션 없는 상품은 SKU 가 "…-BASE" 하나뿐이라 옵션 표기를 생략한다.
  const optionText = v => v.optionSummary && v.optionSummary !== "BASE" ? ` · ${escapeHtml(v.optionSummary)}` : "";
  let selectPagination;
  const checkedOnSelect = new Set();   // 1번 목록에서 체크된 variantId (추가 대기)
  const pending = new Map();           // 2번 목록: variantId -> {variant, quantity, storeId, receiptType, memo}
  const checkedOnPending = new Set();  // 2번 목록에서 체크된 variantId (일괄 삭제용)

  document.addEventListener("DOMContentLoaded", () => {
    selectPagination = AdminPagination.mount($("select-pagination"), { total: 0, size: 10, inline: true, onChange: renderSelectTable });
    unpinSelectPagination();
    $("receiving-keyword").addEventListener("input", debounce(() => { selectPagination.reset(); renderSelectTable(); }, 250));
    $("receiving-category-filter").onchange = () => { selectPagination.reset(); renderSelectTable(); };
    $("receiving-status-filter").onchange = () => { selectPagination.reset(); renderSelectTable(); };
    $("receiving-availability-filter").onchange = () => { selectPagination.reset(); renderSelectTable(); };
    $("receiving-search-btn").onclick = () => { selectPagination.reset(); renderSelectTable(); };
    $("receiving-filter-reset").onclick = () => {
      $("receiving-keyword").value = ""; $("receiving-category-filter").value = "";
      $("receiving-status-filter").value = ""; $("receiving-availability-filter").value = "";
      selectPagination.reset(); renderSelectTable();
    };
    $("select-all-page").onchange = toggleSelectAllOnPage;
    $("add-selected-btn").onclick = addCheckedToPending;
    $("pending-select-all").onchange = togglePendingSelectAll;
    $("pending-clear-selected").onclick = removeCheckedFromPending;
    $("pending-clear-all").onclick = () => { pending.clear(); checkedOnPending.clear(); renderPending(); };
    $("pending-cancel").onclick = () => { pending.clear(); checkedOnPending.clear(); renderPending(); };
    $("pending-submit").onclick = submitReceiving;

    Promise.all([loadVariants(), loadCategories(), loadStores()]).then(() => {
      renderSelectTable();
      addDeepLinkedVariant();
      renderPending();
    }).catch(error => AppToast.error(error.message || "입고 화면을 불러오지 못했습니다."));
    loadHistory();
  });

  async function loadVariants() {
    const response = await fetch("/admin/api/commerce/variants/inventory?storeCode=" + encodeURIComponent(activeStoreCode()));
    const data = await response.json();
    if (!response.ok) return AppToast.error(data.message || "SKU 목록을 조회하지 못했습니다.");
    variants = data || [];
  }

  async function loadCategories() {
    const response = await fetch("/admin/api/commerce/categories?storeCode=" + encodeURIComponent(activeStoreCode()));
    const data = response.ok ? await response.json() : [];
    categories = data || [];
    $("receiving-category-filter").innerHTML = '<option value="">카테고리 전체</option>' +
      categories.map(c => `<option value="${escapeHtml(c.categoryName)}">${escapeHtml(c.categoryName)}</option>`).join("");
  }

  async function loadStores() {
    const response = await fetch("/admin/api/commerce/stores");
    const data = response.ok ? await response.json() : [];
    stores = (data || []).filter(s => s.active);
  }

  async function loadHistory() {
    // 입고 내역은 별도 화면(/admin/commerce/receiving/history)으로 분리됨.
    if (!document.getElementById("receiving-history-rows")) return;
    const response = await fetch("/admin/api/commerce/inventory-transactions?type=RECEIPT");
    const data = await response.json();
    if (!response.ok) return AppToast.error(data.message || "입고 내역을 조회하지 못했습니다.");
    history = data || [];
    renderHistory();
  }

  // ---------- 1. 입고할 상품을 선택하세요 ----------
  function filteredVariants() {
    const keyword = $("receiving-keyword").value.trim().toLowerCase();
    const category = $("receiving-category-filter").value;
    const status = $("receiving-status-filter").value;
    const availability = $("receiving-availability-filter").value;
    return variants.filter(v => {
      if (category && v.category !== category) return false;
      if (status && v.saleStatus !== status) return false;
      if (availability === "Y" && v.saleStatus === "STOPPED") return false;
      if (availability === "N" && v.saleStatus !== "STOPPED") return false;
      if (!keyword) return true;
      return [v.productName, v.productCode, v.sku, v.barcode].some(value => (value || "").toLowerCase().includes(keyword));
    });
  }

  // 이 페이지는 테이블이 3개(선택/입고목록/최근내역)라, AdminPagination의 기본 동작인
  // "화면 맨 아래 고정"을 그대로 쓰면 위쪽 "1번 상품 선택" 표의 페이지 이동인데도
  // 맨 아래(최근 입고 내역 밑)에 붙어 있어 어느 표 것인지 헷갈린다 — 표 바로 아래 고정.
  function unpinSelectPagination() {
    const el = $("select-pagination");
    el.classList.remove("pagination-footer");
    el.classList.add("inline-pagination");
  }

  function renderSelectTable() {
    const list = filteredVariants();
    selectPagination.setTotal(list.length);
    unpinSelectPagination();
    $("select-total-count").textContent = list.length.toLocaleString("ko-KR");
    $("select-empty").hidden = list.length > 0;
    const page = selectPagination.slice(list);
    $("select-rows").innerHTML = page.map(v => `<tr>
      <td class="checkbox-col"><input type="checkbox" data-select="${v.variantId}" ${checkedOnSelect.has(v.variantId) || pending.has(v.variantId) ? "checked" : ""} ${pending.has(v.variantId) ? "disabled" : ""}></td>
      <td><small>${escapeHtml(v.category || "미분류")}</small></td>
      <td class="receiving-product-cell"><strong>${escapeHtml(v.productName)}${optionText(v)}</strong><small class="mono">${escapeHtml(v.sku)}</small></td>
      <td class="number">${v.stockQuantity.toLocaleString("ko-KR")}</td>
      <td class="number">${v.availableQuantity.toLocaleString("ko-KR")}</td>
    </tr>`).join("");
    document.querySelectorAll("[data-select]").forEach(box => box.onchange = () => {
      const id = Number(box.dataset.select);
      if (box.checked) checkedOnSelect.add(id); else checkedOnSelect.delete(id);
      syncCheckedCount();
    });
    const pageIds = page.filter(v => !pending.has(v.variantId)).map(v => v.variantId);
    $("select-all-page").checked = pageIds.length > 0 && pageIds.every(id => checkedOnSelect.has(id));
    syncCheckedCount();
  }

  function toggleSelectAllOnPage() {
    const list = filteredVariants();
    const page = selectPagination.slice(list).filter(v => !pending.has(v.variantId));
    const checkAll = $("select-all-page").checked;
    page.forEach(v => { if (checkAll) checkedOnSelect.add(v.variantId); else checkedOnSelect.delete(v.variantId); });
    renderSelectTable();
  }

  function syncCheckedCount() { $("checked-count").textContent = checkedOnSelect.size; }

  function addCheckedToPending() {
    if (checkedOnSelect.size === 0) return AppToast.error("추가할 상품을 먼저 선택해 주세요.");
    const defaultStoreId = stores.length ? stores[0].id : null;
    checkedOnSelect.forEach(id => {
      if (pending.has(id)) return;
      const variant = variants.find(v => v.variantId === id);
      if (!variant) return;
      pending.set(id, { variant, quantity: 10, storeId: defaultStoreId, receiptType: RECEIPT_TYPES[0], manufacturedDate: "", expirationDate: "", memo: "" });
    });
    checkedOnSelect.clear();
    renderSelectTable();
    renderPending();
  }

  function addDeepLinkedVariant() {
    const variantId = Number(new URLSearchParams(location.search).get("variantId"));
    if (!variantId || pending.has(variantId)) return;
    const variant = variants.find(item => item.variantId === variantId);
    if (!variant) return AppToast.error("입고할 SKU를 찾지 못했습니다.");
    pending.set(variantId, { variant, quantity: 10, storeId: stores[0]?.id || null, receiptType: RECEIPT_TYPES[0], manufacturedDate: "", expirationDate: "", memo: "상품 등록 후 초기 입고" });
    requestAnimationFrame(() => $("pending-table")?.scrollIntoView({ behavior: "smooth", block: "center" }));
  }

  // ---------- 2. 입고 상품 목록 ----------
  function renderPending() {
    const rows = [...pending.values()];
    $("pending-count").textContent = rows.length;
    $("pending-table").hidden = rows.length === 0;
    $("pending-empty").hidden = rows.length > 0;
    $("pending-rows").innerHTML = rows.map(({ variant: v, quantity, storeId, receiptType, manufacturedDate, expirationDate, memo }) => `<tr data-pending-row="${v.variantId}">
      <td class="checkbox-col"><input type="checkbox" data-pending-select="${v.variantId}" ${checkedOnPending.has(v.variantId) ? "checked" : ""}></td>
      <td class="receiving-product-cell"><strong>${escapeHtml(v.productName)}${optionText(v)}</strong><small class="mono">${escapeHtml(v.sku)}</small></td>
      <td class="number">${v.stockQuantity.toLocaleString("ko-KR")}</td>
      <td><input type="number" min="1" value="${quantity}" data-pending-qty="${v.variantId}"></td>
      <td><select data-pending-store="${v.variantId}">${stores.map(s => `<option value="${s.id}" ${s.id === storeId ? "selected" : ""}>${escapeHtml(s.storeName)}</option>`).join("")}</select></td>
      <td><span class="pending-lot-auto" title="입고 등록 시 자동 발번">자동</span></td>
      <td><input type="date" value="${manufacturedDate || ''}" data-pending-mfg="${v.variantId}"></td>
      <td><input type="date" value="${expirationDate || ''}" data-pending-exp="${v.variantId}"></td>
      <td><select data-pending-type="${v.variantId}">${RECEIPT_TYPES.map(t => `<option value="${escapeHtml(t)}" ${t === receiptType ? "selected" : ""}>${escapeHtml(t)}</option>`).join("")}</select></td>
      <td><input type="text" placeholder="메모" value="${escapeHtml(memo)}" data-pending-memo="${v.variantId}"></td>
      <td><button type="button" class="pending-row-remove" data-pending-remove="${v.variantId}" title="삭제">×</button></td>
    </tr>`).join("");
    document.querySelectorAll("[data-pending-select]").forEach(box => box.onchange = () => {
      const id = Number(box.dataset.pendingSelect);
      if (box.checked) checkedOnPending.add(id); else checkedOnPending.delete(id);
      $("pending-select-all").checked = pending.size > 0 && checkedOnPending.size === pending.size;
    });
    document.querySelectorAll("[data-pending-qty]").forEach(input => input.onchange = () => updatePending(Number(input.dataset.pendingQty), "quantity", Number(input.value)));
    document.querySelectorAll("[data-pending-store]").forEach(select => select.onchange = () => updatePending(Number(select.dataset.pendingStore), "storeId", Number(select.value)));
    document.querySelectorAll("[data-pending-mfg]").forEach(input => input.onchange = () => updatePending(Number(input.dataset.pendingMfg), "manufacturedDate", input.value));
    document.querySelectorAll("[data-pending-exp]").forEach(input => input.onchange = () => updatePending(Number(input.dataset.pendingExp), "expirationDate", input.value));
    document.querySelectorAll("[data-pending-type]").forEach(select => select.onchange = () => updatePending(Number(select.dataset.pendingType), "receiptType", select.value));
    document.querySelectorAll("[data-pending-memo]").forEach(input => input.onchange = () => updatePending(Number(input.dataset.pendingMemo), "memo", input.value));
    document.querySelectorAll("[data-pending-remove]").forEach(btn => btn.onclick = () => {
      pending.delete(Number(btn.dataset.pendingRemove));
      checkedOnPending.delete(Number(btn.dataset.pendingRemove));
      renderPending(); renderSelectTable();
    });
    $("pending-select-all").checked = pending.size > 0 && checkedOnPending.size === pending.size;
    const totalQty = rows.reduce((sum, row) => sum + (Number(row.quantity) || 0), 0);
    $("summary-sku-count").textContent = rows.length;
    $("summary-qty-sum").textContent = totalQty.toLocaleString("ko-KR");
    $("pending-submit").disabled = rows.length === 0;
  }

  function updatePending(id, key, value) { const row = pending.get(id); if (row) row[key] = value; renderPending(); }

  function togglePendingSelectAll() {
    const checkAll = $("pending-select-all").checked;
    pending.forEach((_, id) => { if (checkAll) checkedOnPending.add(id); else checkedOnPending.delete(id); });
    renderPending();
  }

  function removeCheckedFromPending() {
    if (checkedOnPending.size === 0) return AppToast.error("삭제할 상품을 먼저 선택해 주세요.");
    checkedOnPending.forEach(id => pending.delete(id));
    checkedOnPending.clear();
    renderPending();
    renderSelectTable();
  }

  // ---------- 입고 등록 ----------
  async function submitReceiving() {
    if (submitting) return;
    const rows = [...pending.values()];
    if (rows.length === 0) return;
    const invalid = rows.find(row => !row.quantity || row.quantity <= 0 || !row.storeId || !row.receiptType
      || (row.manufacturedDate && row.expirationDate && row.manufacturedDate > row.expirationDate));
    if (invalid) return AppToast.error("입고 수량·입고 위치·입고 유형을 확인해 주세요. (제조일은 유통기한보다 늦을 수 없습니다)");
    submitting = true;
    $("pending-submit").disabled = true;
    let success = 0, fail = 0;
    const failedIds = new Set();
    for (const row of rows) {
      const store = stores.find(s => s.id === row.storeId);
      const reason = `[${row.receiptType}] 입고 위치: ${store ? store.storeName : "-"}` + (row.memo ? ` - ${row.memo}` : "");
      try {
        const response = await fetch(`/admin/api/commerce/variants/${row.variant.variantId}/receive`, {
          method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ quantity: row.quantity, reason, storeId: row.storeId, manufacturedDate: row.manufacturedDate || null, expirationDate: row.expirationDate || null, memo: row.memo || null })
        });
        if (!response.ok) { const data = await response.json().catch(() => ({})); throw new Error(data.message || "입고 처리에 실패했습니다."); }
        success++;
      } catch (e) { fail++; failedIds.add(row.variant.variantId); }
    }
    if (fail === 0) AppToast.success(`${success}건 입고 등록을 완료했습니다. LOT 번호는 자동 발번되어 아래 "최근 입고 내역"과 LOT·유통기한 화면에서 확인할 수 있습니다.`);
    else AppToast.error(`${success}건 성공, ${fail}건 실패했습니다.`);
    const fromProductId = Number(new URLSearchParams(location.search).get("fromProductId"));
    if (fail === 0 && fromProductId) {
      const completed = $("receiving-flow-complete");
      completed.hidden = false;
      $("receiving-next-product").href = `/admin/commerce/products?flowProductId=${fromProductId}`;
    }
    [...pending.keys()].forEach(id => { if (!failedIds.has(id)) pending.delete(id); });
    checkedOnPending.clear(); checkedOnSelect.clear();
    renderPending();
    await Promise.all([loadVariants(), loadHistory()]);
    renderSelectTable();
    submitting = false;
    $("pending-submit").disabled = pending.size === 0;
  }

  // ---------- 최근 입고 내역 (별도 화면으로 분리) ----------
  function renderHistory() {
    if (!document.getElementById("receiving-history-rows")) return;
    $("empty-receiving-history").hidden = history.length > 0;
    $("receiving-history-rows").innerHTML = history.map(row => `<tr>
      <td>${formatDate(row.createdAt)}</td>
      <td>${escapeHtml(row.productName)}</td>
      <td>${escapeHtml(row.sku)}</td>
      <td class="number">+${row.quantity.toLocaleString("ko-KR")}</td>
      <td class="number">${row.stockAfter.toLocaleString("ko-KR")}</td>
      <td>${escapeHtml(row.reason || "-")}</td>
    </tr>`).join("");
  }

  function formatDate(value) { return value ? new Date(value).toLocaleString("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : "-"; }
  function debounce(fn, wait) { let timer; return () => { clearTimeout(timer); timer = setTimeout(fn, wait); }; }
})();
