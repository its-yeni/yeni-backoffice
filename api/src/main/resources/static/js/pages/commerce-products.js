(function () {
  const $ = id => document.getElementById(id);
  let products = [], categories = [], optionTemplates = [], currentOptionGroups = [], currentVariants = [], pagination, autoOpenOptionDetail = false;

  document.addEventListener("DOMContentLoaded", () => {
    const initialParams = new URLSearchParams(location.search);
    if (initialParams.get("keyword")) $("product-keyword").value = initialParams.get("keyword");
    $("open-product-modal").onclick = () => openModal();
    $("open-catalog-preview").onclick = () => location.href = "/admin/commerce/preview";
    document.querySelectorAll("[data-close-modal]").forEach(el => el.onclick = closeModal);
    pagination = AdminPagination.mount($("product-pagination"), {total: 0, size: 10, onChange: renderProducts});
    $("product-keyword").addEventListener("input", debounce(() => { pagination.reset(); load(); }, 250));
    $("status-filter").onchange = () => { pagination.reset(); load(); };
    $("category-filter").onchange = () => { pagination.reset(); renderProducts(); };
    $("product-filter-reset").onclick = () => { $("product-keyword").value = ""; $("status-filter").value = ""; $("category-filter").value = ""; pagination.reset(); load(); };
    if ($("product-search-btn")) $("product-search-btn").onclick = () => { pagination.reset(); load(); };
    $("channel-exposure").onchange = syncExposure;
    $("sale-status").onchange = () => $("channel-exposure").checked = $("sale-status").value === "ON_SALE";
    $("inventory-managed").onchange = renderInventoryField;
    $("option-template-list").onchange = event => { if (event.target.matches('input[type="checkbox"]')) syncOptionWorkflow(); };
    $("option-detail-toggle").onclick = toggleOptionDetail;
    $("variant-stock-link").onclick = event => { event.preventDefault(); openOptionDetail(); };
    $("manual-product-code").onchange = renderCodePolicy;
    $("product-form").onsubmit = save;
    $("image-upload-box").onclick = () => $("image-file").click();
    $("image-file").onchange = uploadImage;
    document.addEventListener("keydown", event => { if (event.key === "Escape") closeModal(); });
    Promise.all([loadCategories(), loadOptionTemplates()])
      .then(load)
      .catch(error => AppToast.error(error.message || "상품 화면을 불러오지 못했습니다."));
  });

  async function loadCategories() {
    categories = await apiGet("/admin/api/commerce/categories?storeCode=" + encodeURIComponent(activeStoreCode()));
    $("category").innerHTML = categories.filter(item => item.exposed).map(item => `<option value="${escapeHtml(item.categoryName)}">${escapeHtml(item.categoryName)}</option>`).join("");
    $("category-filter").innerHTML = '<option value="">카테고리 전체</option>' + categories.map(item => `<option value="${escapeHtml(item.categoryName)}">${escapeHtml(item.categoryName)}</option>`).join("");
  }

  async function loadOptionTemplates() {
    optionTemplates = await apiGet("/admin/api/commerce/option-templates");
  }

  async function load() {
    const query = new URLSearchParams({keyword: $("product-keyword").value, saleStatus: $("status-filter").value, storeCode: activeStoreCode(), size: "100"});
    const data = await apiGet("/admin/api/commerce/products?" + query);
    products = (data.items || []).filter(product => !activeBrandId() || Number(product.brandId) === activeBrandId());
    renderProducts();
  }

  function renderKpis() {
    const summary = CommerceProductListModel.summarize(products);
    const set = (id, n) => { const el = $(id); if (el) el.innerHTML = `${Number(n).toLocaleString("ko-KR")}<em>개</em>`; };
    set("kpi-total", summary.total);
    set("kpi-onsale", summary.onSale);
    set("kpi-soldout", summary.soldOut);
    set("kpi-exposed", summary.exposed);
  }
  function renderProducts() {
    renderKpis();
    const category = $("category-filter").value;
    const filtered = CommerceProductListModel.filter(products, category);
    pagination.setTotal(filtered.length);
    if ($("product-total")) $("product-total").textContent = filtered.length.toLocaleString("ko-KR");
    $("empty-products").hidden = filtered.length > 0;
    const rowStart = (pagination.getPage() - 1) * pagination.getSize();
    $("product-rows").innerHTML = pagination.slice(filtered)
      .map((product, i) => CommerceProductListModel.row(product, rowStart + i + 1)).join("");
    document.querySelectorAll("[data-edit]").forEach(el => el.onclick = () => openModal(Number(el.dataset.edit)));
    document.querySelectorAll("[data-product-row]").forEach(row => row.onclick = event => {
      if (event.target.closest("button,a,input,select,textarea,label,details,summary")) return;
      openModal(Number(row.dataset.productRow));
    });
    document.querySelectorAll("[data-row-edit]").forEach(el => el.onclick = event => { event.stopPropagation(); openModal(Number(el.dataset.rowEdit)); });
    document.querySelectorAll("[data-options]").forEach(el => el.onclick = event => { event.stopPropagation(); autoOpenOptionDetail = true; openModal(Number(el.dataset.options)); });
    document.querySelectorAll("[data-preview]").forEach(el => el.onclick = event => { event.stopPropagation(); window.openProductPreview(Number(el.dataset.preview)); });
    document.querySelectorAll("#product-rows .switch input").forEach(input => input.onchange = event => changeStatus(event, input));
    const flowProductId = Number(new URLSearchParams(location.search).get("flowProductId"));
    const target = flowProductId ? document.querySelector(`[data-product-row="${flowProductId}"]`) : null;
    if (target) {
      target.classList.add("flow-target-row");
      target.scrollIntoView({ block: "center" });
      AppToast.success("입고 재고가 반영되었습니다. 판매 스위치를 켠 뒤 고객 화면을 확인하세요.");
    }
  }

  async function openModal(id) {
    $("product-form").reset(); $("product-id").value = ""; $("image-url").value = ""; $("modal-message").textContent = "";
    currentOptionGroups = []; renderImagePreview(null);
    const product = products.find(item => item.id === id);
    $("product-modal-title").textContent = product ? "상품 수정" : "상품 추가";
    document.querySelector(".modal-submit").textContent = product ? "저장" : "등록";
    $("manual-product-code").disabled = Boolean(product);
    $("option-detail-toggle").hidden = !product;
    $("option-detail-panel").hidden = true;
    $("option-detail-toggle").textContent = "옵션·조합 편집 ▾";
    if (product) {
      $("product-id").value = product.id; $("image-url").value = product.imageUrl || ""; renderImagePreview(product.imageUrl);
      $("product-code").value = product.productCode; $("manual-product-code").checked = true;
      $("product-name").value = product.productName; $("category").value = product.category || ""; $("sale-price").value = product.salePrice;
      $("stock-quantity").value = product.stockQuantity; $("inventory-managed").checked = product.inventoryManaged !== false;
      $("sale-status").value = product.saleStatus; $("channel-exposure").checked = product.saleStatus === "ON_SALE";
      await loadCurrentOptionGroups(product.id);
      if (autoOpenOptionDetail) { autoOpenOptionDetail = false; if (currentOptionGroups.length) openOptionDetail(); }
    } else {
      $("sale-price").value = 0; $("stock-quantity").value = 0; $("inventory-managed").checked = true;
      $("sale-status").value = "ON_SALE"; $("channel-exposure").checked = true; $("manual-product-code").checked = false;
    }
    renderCodePolicy(); renderOptionTemplates(); syncOptionWorkflow(); renderInventoryField();
    $("product-modal").classList.add("open"); $("product-modal").setAttribute("aria-hidden", "false"); document.body.style.overflow = "hidden";
  }

  async function loadCurrentOptionGroups(productId) {
    const groups = await apiGet(`/admin/api/commerce/products/${productId}/option-groups`);
    currentOptionGroups = [...new Map(groups.map(group => [group.groupName, group])).values()];
  }

  /* ---- 옵션값 인라인 편집 (별도 페이지 이동 없이 모달 안에서) ---- */
  function toggleOptionDetail() {
    if ($("option-detail-panel").hidden) openOptionDetail(); else closeOptionDetail();
  }
  function closeOptionDetail() {
    $("option-detail-panel").hidden = true;
    $("option-detail-toggle").textContent = "옵션·조합 편집 ▾";
  }
  async function openOptionDetail() {
    const productId = $("product-id").value;
    if (!productId) return;
    $("option-detail-panel").hidden = false;
    $("option-detail-toggle").textContent = "옵션·조합 편집 ▴";
    $("option-detail-panel").innerHTML = `<p class="odp-empty">불러오는 중…</p>`;
    await Promise.all([loadCurrentOptionGroups(Number(productId)), loadCurrentVariants(Number(productId))]);
    renderOptionDetailPanel();
    $("option-detail-panel").scrollIntoView({ behavior: "smooth", block: "nearest" });
  }
  async function loadCurrentVariants(productId) {
    currentVariants = await apiGet(`/admin/api/commerce/products/${productId}/variants`);
  }
  function productHasSkuCombos() {
    return currentOptionGroups.some(g => g.selectionType === "SINGLE" && (g.values || []).length > 0);
  }
  function renderOptionDetailPanel() {
    const panel = $("option-detail-panel");
    const hasGroups = currentOptionGroups.length > 0;
    panel.innerHTML =
      `<p class="odp-guide">${hasGroups
        ? "① 옵션값 이름·가격·판매 여부를 고치고 다른 칸을 클릭하면 자동 저장됩니다. ② 단일 선택 옵션이 있으면 아래에서 <b>조합 SKU를 생성</b>해 조합별 재고를 관리합니다."
        : "이 상품엔 아직 옵션이 없습니다. 위 목록에서 템플릿을 체크(저장 시 복사)하거나, 아래 <b>+ 옵션 그룹 직접 추가</b>로 만드세요."}</p>`
      + (hasGroups ? currentOptionGroups.map((group, index) => groupBlockHtml(group, index)).join("") : "")
      + addGroupFormHtml()
      + variantSectionHtml();
    wireOptionDetailPanel();
  }

  function groupBlockHtml(group, index) {
    return `<div class="odp-group" data-group-id="${group.id}">
      <div class="odp-group-head">
        <strong>${escapeHtml(group.groupName)}</strong>
        <span class="odp-group-order">
          <button type="button" class="odp-mini" data-move-group="${group.id}" data-dir="up" ${index === 0 ? "disabled" : ""}>▲</button>
          <button type="button" class="odp-mini" data-move-group="${group.id}" data-dir="down" ${index === currentOptionGroups.length - 1 ? "disabled" : ""}>▼</button>
        </span>
      </div>
      <div class="odp-group-settings">
        <label class="odp-gs-req"><span>필수</span><label class="switch"><input class="odp-g-req" type="checkbox" ${group.requiredOption ? "checked" : ""}><span></span></label></label>
        <label><span>선택 방식</span><select class="odp-g-type"><option value="SINGLE" ${group.selectionType === "SINGLE" ? "selected" : ""}>단일</option><option value="MULTIPLE" ${group.selectionType === "MULTIPLE" ? "selected" : ""}>복수</option></select></label>
        <label><span>선택 범위</span><span class="odp-g-range"><input class="odp-g-min" type="number" min="0" value="${group.minSelection}"><i>~</i><input class="odp-g-max" type="number" min="1" value="${group.maxSelection}"></span></label>
      </div>
      <div class="odp-values">
        <div class="odp-row odp-row-head"><span>옵션값</span><span>추가금액</span><span>판매</span></div>
        ${(group.values || []).map(value => `
          <div class="odp-row" data-value-id="${value.id}">
            <input class="odp-name" value="${escapeHtml(value.valueName)}" maxlength="60">
            <div class="odp-price"><input class="odp-add" type="number" min="0" value="${Number(value.additionalPrice || 0)}"><span>원</span></div>
            <label class="switch"><input class="odp-on" type="checkbox" ${value.saleStatus === "ON_SALE" ? "checked" : ""}><span></span></label>
          </div>`).join("")}
        <button type="button" class="odp-add-value btn btn-light" data-add-group="${group.id}">+ 옵션값 추가</button>
      </div>
    </div>`;
  }

  function addGroupFormHtml() {
    return `<details class="odp-add-group">
      <summary>+ 옵션 그룹 직접 추가</summary>
      <div class="odp-add-group-form">
        <input id="odp-ng-name" placeholder="그룹명 (예: 사이즈)" maxlength="60">
        <select id="odp-ng-type"><option value="SINGLE">단일 선택</option><option value="MULTIPLE">복수 선택</option></select>
        <label class="odp-ng-req"><input type="checkbox" id="odp-ng-req"> 필수</label>
        <input id="odp-ng-values" placeholder="옵션값 쉼표 구분 (예: S, M, L)">
        <button type="button" class="btn btn-primary" id="odp-ng-save">추가</button>
      </div>
    </details>`;
  }

  function wireOptionDetailPanel() {
    const panel = $("option-detail-panel");

    panel.querySelectorAll(".odp-row[data-value-id]").forEach(row => {
      const valueId = Number(row.dataset.valueId);
      const group = currentOptionGroups.find(g => (g.values || []).some(v => v.id === valueId));
      const value = group.values.find(v => v.id === valueId);
      const save = () => saveOptionValue(valueId, {
        valueName: row.querySelector(".odp-name").value.trim() || value.valueName,
        additionalPrice: Number(row.querySelector(".odp-add").value || 0),
        inventoryManaged: value.inventoryManaged,
        stockQuantity: value.stockQuantity,
        saleStatus: row.querySelector(".odp-on").checked ? "ON_SALE" : "STOPPED",
      }, row);
      row.querySelector(".odp-name").onblur = save;
      row.querySelector(".odp-add").onblur = save;
      row.querySelector(".odp-on").onchange = save;
    });
    panel.querySelectorAll("[data-add-group]").forEach(btn => btn.onclick = () => addOptionValue(Number(btn.dataset.addGroup)));

    panel.querySelectorAll(".odp-group[data-group-id]").forEach(box => {
      const groupId = Number(box.dataset.groupId);
      const save = () => saveGroupSettings(groupId, box);
      box.querySelector(".odp-g-req").onchange = save;
      box.querySelector(".odp-g-type").onchange = save;
      box.querySelector(".odp-g-min").onblur = save;
      box.querySelector(".odp-g-max").onblur = save;
    });
    panel.querySelectorAll("[data-move-group]").forEach(btn => btn.onclick = () => moveGroup(Number(btn.dataset.moveGroup), btn.dataset.dir));

    if ($("odp-ng-save")) $("odp-ng-save").onclick = submitNewGroup;

    const gen = panel.querySelector("[data-generate-variants]");
    if (gen) gen.onclick = generateVariants;
    panel.querySelectorAll(".odp-var-row[data-variant-id]").forEach(row => {
      const variantId = Number(row.dataset.variantId);
      const variant = currentVariants.find(v => v.id === variantId);
      const save = () => saveVariant(variantId, {
        sku: variant.sku,
        barcode: row.querySelector(".odp-var-barcode").value.trim() || null,
        additionalPrice: Number(row.querySelector(".odp-var-add").value || 0),
        stockQuantity: Number(row.querySelector(".odp-var-stock").value || 0),
        saleStatus: row.querySelector(".odp-var-on").checked ? "ON_SALE" : "STOPPED",
      }, row);
      row.querySelector(".odp-var-barcode").onblur = save;
      row.querySelector(".odp-var-add").onblur = save;
      row.querySelector(".odp-var-stock").onblur = save;
      row.querySelector(".odp-var-on").onchange = save;
    });
  }

  async function reloadOptionDetail() {
    const productId = Number($("product-id").value);
    await Promise.all([loadCurrentOptionGroups(productId), loadCurrentVariants(productId)]);
    renderOptionDetailPanel();
    renderOptionTemplates();
    syncOptionWorkflow();
  }

  async function saveGroupSettings(groupId, box) {
    const group = currentOptionGroups.find(g => g.id === groupId);
    const min = Number(box.querySelector(".odp-g-min").value || 0);
    const max = Number(box.querySelector(".odp-g-max").value || 1);
    if (min < 0 || max < 1 || min > max) { AppToast.error("선택 범위를 확인해 주세요. (최소 ≤ 최대)"); return; }
    box.classList.remove("odp-error");
    try {
      const response = await fetch(`/admin/api/commerce/option-groups/${groupId}`, {
        method: "PUT", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          groupName: group.groupName,
          selectionType: box.querySelector(".odp-g-type").value,
          requiredOption: box.querySelector(".odp-g-req").checked,
          minSelection: min, maxSelection: max, exposed: group.exposed !== false,
        }),
      });
      if (!response.ok) throw new Error((await response.json()).message || "옵션 그룹 저장 실패");
      await reloadOptionDetail();
    } catch (error) { box.classList.add("odp-error"); AppToast.error(error.message); }
  }

  async function moveGroup(groupId, dir) {
    const ids = currentOptionGroups.map(g => g.id);
    const i = ids.indexOf(groupId), j = dir === "up" ? i - 1 : i + 1;
    if (j < 0 || j >= ids.length) return;
    [ids[i], ids[j]] = [ids[j], ids[i]];
    try {
      const response = await fetch(`/admin/api/commerce/products/${$("product-id").value}/option-groups/order`, {
        method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ids }),
      });
      if (!response.ok) throw new Error((await response.json()).message || "순서 변경 실패");
      await reloadOptionDetail();
    } catch (error) { AppToast.error(error.message); }
  }

  async function submitNewGroup() {
    const name = $("odp-ng-name").value.trim();
    if (!name) return AppToast.error("그룹명을 입력해 주세요.");
    const values = ($("odp-ng-values").value || "").split(",").map(s => s.trim()).filter(Boolean)
      .map(v => ({ valueName: v, additionalPrice: 0, inventoryManaged: false, stockQuantity: 0, saleStatus: "ON_SALE" }));
    try {
      const response = await fetch(`/admin/api/commerce/products/${$("product-id").value}/option-groups`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ groupName: name, selectionType: $("odp-ng-type").value, requiredOption: $("odp-ng-req").checked, minSelection: 0, maxSelection: 1, exposed: true, values }),
      });
      if (!response.ok) throw new Error((await response.json()).message || "옵션 그룹 추가 실패");
      await reloadOptionDetail();
      AppToast.success(`"${name}" 옵션 그룹을 추가했습니다.`);
    } catch (error) { AppToast.error(error.message); }
  }

  /* ---- 옵션 조합별 재고 (SKU = ProductVariant) ---- */
  function variantSectionHtml() {
    if (!productHasSkuCombos()) return "";
    const combos = currentVariants.filter(v => v.combinationKey && v.combinationKey !== "BASE");
    const expected = currentOptionGroups
      .filter(g => g.selectionType === "SINGLE" && (g.values || []).length > 0)
      .reduce((n, g) => n * g.values.length, 1);
    const head = `<div class="odp-var-block"><div class="odp-group-head">
        <strong>옵션 조합별 재고 (SKU)</strong>
        <button type="button" class="btn btn-primary btn-sm" data-generate-variants>${combos.length ? "빠진 조합 채우기" : `조합 ${expected}개 생성`}</button>
      </div>`;
    if (!combos.length) {
      return head + `<p class="odp-var-empty">단일 선택 옵션 <b>${expected}개 조합</b>의 SKU가 아직 없습니다. 위 버튼으로 생성하면 조합별로 재고·바코드를 관리할 수 있습니다.</p></div>`;
    }
    return head +
      `<div class="odp-var-row odp-var-head"><span>조합 / SKU</span><span>바코드</span><span>추가금액</span><span>현재재고</span><span>가용</span><span>판매</span></div>
      ${combos.map(v => `
        <div class="odp-var-row" data-variant-id="${v.id}">
          <div class="odp-var-name"><strong>${escapeHtml(v.optionSummary || "조합")}</strong><small class="mono">${escapeHtml(v.sku)}</small></div>
          <input class="odp-var-barcode" value="${escapeHtml(v.barcode || "")}" placeholder="바코드" maxlength="40">
          <div class="odp-price"><input class="odp-var-add" type="number" min="0" value="${Number(v.additionalPrice || 0)}"><span>원</span></div>
          <input class="odp-var-stock" type="number" min="0" value="${v.stockQuantity}">
          <span class="odp-var-ro" title="예약 ${v.reservedQuantity}개 차감 후 남은 판매 가능 수량">${v.availableQuantity}</span>
          <label class="switch"><input class="odp-var-on" type="checkbox" ${v.saleStatus === "ON_SALE" ? "checked" : ""}><span></span></label>
        </div>`).join("")}
      <p class="odp-var-note">현재재고를 직접 고치면 실사 조정으로 기록됩니다. 정상 입고는 <a class="text-link" href="/admin/commerce/receiving">입고 관리</a>에서 하세요.</p></div>`;
  }

  async function generateVariants() {
    const productId = $("product-id").value;
    try {
      const response = await fetch(`/admin/api/commerce/products/${productId}/variants/generate`, { method: "POST" });
      if (!response.ok) throw new Error((await response.json()).message || "SKU 생성 실패");
      await reloadOptionDetail();
      AppToast.success("옵션 조합 SKU를 생성했습니다.");
    } catch (error) { AppToast.error(error.message); }
  }
  async function saveVariant(variantId, body, row) {
    row.classList.remove("odp-saved", "odp-error");
    try {
      const response = await fetch(`/admin/api/commerce/variants/${variantId}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
      if (!response.ok) throw new Error((await response.json()).message || "SKU 저장 실패");
      const updated = await response.json();
      const idx = currentVariants.findIndex(v => v.id === variantId);
      if (idx >= 0) currentVariants[idx] = updated;
      const ro = row.querySelector(".odp-var-ro");
      if (ro) ro.textContent = updated.availableQuantity;
      row.classList.add("odp-saved");
      setTimeout(() => row.classList.remove("odp-saved"), 1200);
    } catch (error) { row.classList.add("odp-error"); AppToast.error(error.message); }
  }
  async function saveOptionValue(valueId, body, row) {
    row.classList.remove("odp-saved", "odp-error");
    try {
      const response = await fetch(`/admin/api/commerce/option-values/${valueId}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
      if (!response.ok) throw new Error((await response.json()).message || "옵션값 저장 실패");
      row.classList.add("odp-saved");
      setTimeout(() => row.classList.remove("odp-saved"), 1200);
    } catch (error) { row.classList.add("odp-error"); AppToast.error(error.message); }
  }
  async function addOptionValue(groupId) {
    try {
      const response = await fetch(`/admin/api/commerce/option-groups/${groupId}/values`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ valueName: "새 옵션값", additionalPrice: 0, inventoryManaged: false, stockQuantity: 0, saleStatus: "ON_SALE" }),
      });
      if (!response.ok) throw new Error((await response.json()).message || "옵션값 추가 실패");
      await reloadOptionDetail();
    } catch (error) { AppToast.error(error.message); }
  }

  function renderOptionTemplates() {
    if (!optionTemplates.length) {
      $("option-template-list").innerHTML = '<p class="option-empty">등록된 옵션 템플릿이 없습니다. 옵션 관리에서 먼저 만들어 주세요.</p>';
      return;
    }
    const optionNameKey = name => String(name || "").replace(/\s+/g, "").replace(/(선택|옵션)$/g, "");
    const appliedNames = new Set(currentOptionGroups.map(group => optionNameKey(group.groupName)));
    $("option-template-list").innerHTML = optionTemplates.map(template => {
      const applied = appliedNames.has(optionNameKey(template.templateName));
      const type = template.selectionType === "MULTIPLE" ? "복수 선택" : "단일 선택";
      return `<label class="option-template-item ${applied ? "is-applied" : ""}"><input type="checkbox" value="${template.id}" ${applied ? "checked disabled" : ""}><span><strong>${escapeHtml(template.templateName)}</strong><small>${type} · 옵션값 ${template.values?.length || 0}개</small></span><span class="option-template-state">${applied ? "연결됨" : "연결"}</span>${template.requiredOption ? "<em>필수</em>" : ""}</label>`;
    }).join("");
    syncOptionWorkflow();
  }

  function selectedOptionDefinitions() {
    const applied = currentOptionGroups.map(group => ({name: group.groupName, selectionType: group.selectionType, count: group.values?.length || 0}));
    const newlySelected = [...document.querySelectorAll('#option-template-list input[type="checkbox"]:checked:not(:disabled)')].map(input => {
      const template = optionTemplates.find(item => item.id === Number(input.value));
      return template ? {name: template.templateName, selectionType: template.selectionType, count: template.values?.length || 0} : null;
    }).filter(Boolean);
    return [...applied, ...newlySelected];
  }

  function syncOptionWorkflow() {
    const definitions = selectedOptionDefinitions();
    const skuGroups = definitions.filter(item => item.selectionType === "SINGLE" && item.count > 0);
    const skuCount = skuGroups.length ? skuGroups.reduce((total, item) => total * item.count, 1) : 1;
    $("linked-option-count").textContent = `${definitions.length}개`;
    $("estimated-sku-count").textContent = skuGroups.length ? `${skuCount.toLocaleString("ko-KR")}개 조합` : "기본 SKU 1개";
    $("option-inventory-copy").textContent = skuGroups.length ? "단일 선택 옵션의 조합별로 SKU와 재고를 관리합니다. 복수 선택형 추가 옵션은 SKU 조합에서 제외됩니다." : "옵션이 없으면 상품당 기본 SKU 1개로 재고를 관리합니다.";
    document.querySelectorAll("#option-template-list .option-template-item").forEach(item => {
      const input = item.querySelector('input[type="checkbox"]');
      const state = item.querySelector(".option-template-state");
      if (!input || !state) return;
      state.textContent = input.disabled ? "연결됨" : input.checked ? "연결 예정" : "연결";
    });
    renderInventoryField();
  }

  function renderCodePolicy() {
    const editing = Boolean($("product-id").value);
    const manual = $("manual-product-code").checked;
    $("product-code").readOnly = editing || !manual;
    $("product-code").required = !editing && manual;
    if (!editing && !manual) $("product-code").value = "";
    $("code-policy-help").textContent = editing ? "등록 후 변경할 수 없는 내부 식별 코드입니다." : manual ? "외부 ERP·POS의 기존 코드를 사용할 때만 직접 입력하세요." : "저장 시 서버에서 자동 발번됩니다.";
  }

  async function save(event) {
    event.preventDefault();
    const id = $("product-id").value, submit = document.querySelector(".modal-submit");
    submit.disabled = true;
    const optionTemplateIds = [...document.querySelectorAll('#option-template-list input[type="checkbox"]:checked:not(:disabled)')].map(input => Number(input.value));
    const body = {productCode: $("product-code").value.trim() || null, productName: $("product-name").value.trim(), category: $("category").value.trim(), salePrice: Number($("sale-price").value), stockQuantity: $("inventory-managed").checked ? Number($("stock-quantity").value) : 0, saleStatus: $("sale-status").value, imageUrl: $("image-url").value || null, inventoryManaged: $("inventory-managed").checked, storeCode: activeStoreCode(), optionTemplateIds};
    try {
      const response = await fetch("/admin/api/commerce/products" + (id ? "/" + id : ""), {method: id ? "PUT" : "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)});
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "상품 저장에 실패했습니다.");
      if (id) { closeModal(); await load(); AppToast.success("상품 정보를 저장했습니다."); }
      else {
        AppToast.success(`상품을 등록했습니다. (${data.productCode}) 다음 단계에서 옵션과 입고 재고를 설정합니다.`);
        location.href = `/admin/commerce/product-options?productId=${data.id}&created=true`;
      }
    } catch (error) { $("modal-message").textContent = error.message; }
    finally { submit.disabled = false; }
  }

  async function changeStatus(event, input) {
    event.stopPropagation();
    input.disabled = true;
    let saleStatus = "STOPPED";
    if (input.dataset.status === "ON_SALE" && input.checked) saleStatus = "ON_SALE";
    if (input.dataset.status === "SOLD_OUT" && input.checked) saleStatus = "SOLD_OUT";
    try {
      const response = await fetch(`/admin/api/commerce/products/${input.dataset.id}/status`, {method: "PATCH", headers: {"Content-Type": "application/json"}, body: JSON.stringify({saleStatus})});
      if (!response.ok) { const data = await response.json(); throw new Error(data.message || "상태 변경에 실패했습니다."); }
      await load();
    } catch (error) { input.checked = !input.checked; AppToast.error(error.message); }
    finally { input.disabled = false; }
  }

  async function uploadImage() {
    const file = $("image-file").files[0]; if (!file) return;
    const form = new FormData(); form.append("file", file); $("image-upload-box").classList.add("uploading");
    try { const response = await fetch("/admin/api/commerce/product-images", {method: "POST", body: form}); const data = await response.json(); if (!response.ok) throw new Error(data.message || "이미지 업로드에 실패했습니다."); $("image-url").value = data.imageUrl; renderImagePreview(data.imageUrl); }
    catch (error) { $("modal-message").textContent = error.message; }
    finally { $("image-upload-box").classList.remove("uploading"); $("image-file").value = ""; }
  }

  function syncExposure() { $("sale-status").value = $("channel-exposure").checked ? "ON_SALE" : "STOPPED"; }
  function renderImagePreview(url) { $("image-preview").innerHTML = url ? `<img src="${escapeHtml(url)}" alt="상품 이미지 미리보기">` : '<b>+</b><strong>상품 이미지 등록</strong><small>JPG · PNG · WEBP · GIF / 최대 5MB</small>'; }
  function renderInventoryField() {
    const managed = $("inventory-managed").checked;
    const hasVariantOptions = selectedOptionDefinitions().some(item => item.selectionType === "SINGLE" && item.count > 0);
    $("stock-field").classList.toggle("is-hidden", !managed || hasVariantOptions);
    $("variant-stock-guide").classList.toggle("is-hidden", !managed || !hasVariantOptions);
    $("stock-quantity").required = managed && !hasVariantOptions;
    if (!managed) $("stock-quantity").value = 0;
    const id = $("product-id").value;
    $("variant-stock-link").hidden = !id;
  }
  function closeModal() { $("product-modal").classList.remove("open"); $("product-modal").setAttribute("aria-hidden", "true"); document.body.style.overflow = ""; }
  function debounce(fn, wait) { let timer; return () => { clearTimeout(timer); timer = setTimeout(fn, wait); }; }
})();
