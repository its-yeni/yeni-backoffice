(function () {
  const root = document.getElementById("product-preview-panel");
  if (!root) return;

  /* money/escapeHtml는 common.js의 공통 유틸을 사용합니다. */
  const signedMoney = value => Number(value || 0) > 0 ? "+" + money(value) : money(value);
  // 이미지 미등록 상품의 자리표시자. 이모지(🖼)는 화면마다 렌더링이 달라 보여서(플랫폼별 이모지 폰트 차이)
  // "이미지가 이상하게 나온다"는 오해를 사기 쉽다 — 다른 화면과 같은 무채색 SVG 아이콘으로 통일한다.
  const NO_IMAGE_ICON = '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="8.5" cy="10.5" r="1.5"/><path d="M21 15l-5-5-4 4-3-3-5 5"/></svg>';

  let allProducts = [];
  let allCategories = [];
  let view = "catalog"; // catalog | detail | cart
  let activeCategory = "";
  let currentProduct = null;
  let currentGroups = [];
  let currentAddons = [];
  let selectedOptionValues = {};  // groupId -> Set(valueId)
  let selectedAddonQty = {};      // "groupId:productId" -> qty
  let quantity = 1;
  let cart = [];

  root.innerHTML = `
    <div class="preview-dim" data-preview-close></div>
    <aside class="preview-panel" role="dialog" aria-modal="true" aria-label="상품 미리보기">
      <header class="preview-head">
        <div class="preview-head-left">
          <button type="button" class="preview-icon-btn" data-preview-back title="뒤로" hidden><span>←</span></button>
          <button type="button" class="preview-icon-btn" data-preview-home title="전체보기"><span>⌂</span></button>
          <strong class="preview-head-title">미리보기</strong>
        </div>
        <div class="preview-head-right">
          <button type="button" class="preview-icon-btn preview-cart-btn" data-preview-cart title="장바구니">
            <span>🛒</span><span class="preview-cart-badge" id="preview-cart-badge" hidden>0</span>
          </button>
          <button type="button" class="preview-icon-btn" data-preview-close title="닫기"><span>×</span></button>
        </div>
      </header>
      <div class="preview-body" id="preview-body"></div>
    </aside>`;

  root.querySelectorAll("[data-preview-close]").forEach(el => el.onclick = close);
  root.querySelector("[data-preview-home]").onclick = () => renderCatalog();
  root.querySelector("[data-preview-back]").onclick = () => { if (view === "cart") renderCatalog(); else renderCatalog(); };
  root.querySelector("[data-preview-cart]").onclick = () => renderCart();
  document.addEventListener("keydown", event => { if (event.key === "Escape" && root.classList.contains("open")) close(); });

  async function ensureData() {
    if (allProducts.length) return;
    const [productsRes, categoriesRes] = await Promise.all([
      fetch("/admin/api/commerce/products?size=200"),
      fetch("/admin/api/commerce/categories")
    ]);
    const productsData = productsRes.ok ? await productsRes.json() : { items: [] };
    allProducts = productsData.items || [];
    allCategories = categoriesRes.ok ? await categoriesRes.json() : [];
  }

  async function openCatalog() {
    await ensureData();
    activeCategory = "";
    open();
    renderCatalog();
  }

  async function openDetail(productId) {
    await ensureData();
    open();
    await showDetail(productId);
  }

  function open() {
    root.classList.add("open");
    document.body.style.overflow = "hidden";
    updateCartBadge();
  }

  function close() {
    root.classList.remove("open");
    document.body.style.overflow = "";
  }

  function setHeadTitle(text, showBack) {
    root.querySelector(".preview-head-title").textContent = text;
    root.querySelector("[data-preview-back]").hidden = !showBack;
  }

  function renderCatalog() {
    view = "catalog";
    currentProduct = null;
    setHeadTitle("전체 미리보기", false);

    const exposedCategories = allCategories.filter(c => c.exposed).sort((a, b) => a.sortOrder - b.sortOrder).map(c => c.categoryName);
    allProducts.map(p => p.category).filter(Boolean).forEach(name => { if (!exposedCategories.includes(name)) exposedCategories.push(name); });
    const tabs = ["", ...exposedCategories];

    const visible = allProducts.filter(p => p.saleStatus !== "STOPPED");
    const filtered = activeCategory ? visible.filter(p => p.category === activeCategory) : visible;

    const grouped = {};
    filtered.forEach(p => { const key = p.category || "미분류"; (grouped[key] = grouped[key] || []).push(p); });

    document.getElementById("preview-body").innerHTML = `
      <p class="preview-guide">고객이 실제로 보게 될 상품 목록입니다. 카테고리 순서와 노출 상태를 그대로 반영합니다.</p>
      <div class="preview-tabs">${tabs.map(t => `<button type="button" class="${t === activeCategory ? "active" : ""}" data-cat="${escapeHtml(t)}">${escapeHtml(t || "전체")}</button>`).join("")}</div>
      ${filtered.length === 0 ? '<p class="preview-empty">노출할 상품이 없습니다.</p>' :
        Object.keys(grouped).map(cat => `
          <section class="preview-cat-section">
            <h4>${escapeHtml(cat)}</h4>
            <div class="preview-grid">
              ${grouped[cat].map(p => `
                <button type="button" class="preview-card ${p.saleStatus === "SOLD_OUT" ? "is-soldout" : ""}" data-product="${p.id}">
                  <span class="preview-card-thumb">${p.imageUrl ? `<img src="${escapeHtml(p.imageUrl)}" alt="">` : NO_IMAGE_ICON}</span>
                  ${p.saleStatus === "SOLD_OUT" ? '<span class="preview-soldout-badge">품절</span>' : ""}
                  <strong>${escapeHtml(p.productName)}</strong>
                  <span>${money(p.salePrice)}</span>
                </button>`).join("")}
            </div>
          </section>`).join("")}
    `;
    document.querySelectorAll(".preview-tabs button").forEach(btn => btn.onclick = () => { activeCategory = btn.dataset.cat; renderCatalog(); });
    document.querySelectorAll(".preview-card").forEach(card => card.onclick = () => showDetail(Number(card.dataset.product)));
  }

  async function showDetail(productId) {
    const product = allProducts.find(p => p.id === productId);
    if (!product) return;
    view = "detail";
    currentProduct = product;
    quantity = 1;
    selectedOptionValues = {};
    selectedAddonQty = {};
    setHeadTitle(product.productName, true);

    const body = document.getElementById("preview-body");
    body.innerHTML = `<div class="preview-loading">불러오는 중...</div>`;

    const [groupsRes, addonsRes] = await Promise.all([
      fetch(`/admin/api/commerce/products/${productId}/option-groups`),
      fetch(`/admin/api/commerce/products/${productId}/addon-groups`)
    ]);
    currentGroups = groupsRes.ok ? await groupsRes.json() : [];
    currentAddons = addonsRes.ok ? await addonsRes.json() : [];

    currentGroups.filter(g => g.exposed).forEach(group => {
      const values = group.values.filter(v => v.saleStatus !== "STOPPED");
      if (group.selectionType === "SINGLE" && values.length) {
        selectedOptionValues[group.id] = new Set([values[0].id]);
      } else {
        selectedOptionValues[group.id] = new Set();
      }
    });

    renderDetail();
  }

  function renderDetail() {
    const p = currentProduct;
    const soldOut = p.saleStatus === "SOLD_OUT";
    const stopped = p.saleStatus === "STOPPED";

    const groupsHtml = currentGroups.filter(g => g.exposed).map(group => {
      const values = group.values.filter(v => v.saleStatus !== "STOPPED");
      const selected = selectedOptionValues[group.id] || new Set();
      return `
        <div class="preview-option-group" data-group="${group.id}" data-type="${group.selectionType}">
          <div class="preview-option-label">${escapeHtml(group.groupName)} ${group.requiredOption ? '<span class="preview-required">필수</span>' : ""}</div>
          <div class="preview-option-values">
            ${values.map(v => `
              <button type="button" class="preview-pill ${selected.has(v.id) ? "active" : ""} ${v.saleStatus === "SOLD_OUT" ? "is-disabled" : ""}"
                data-group="${group.id}" data-value="${v.id}" data-type="${group.selectionType}" ${v.saleStatus === "SOLD_OUT" ? "disabled" : ""}>
                ${escapeHtml(v.valueName)}${Number(v.additionalPrice) !== 0 ? `<small>${signedMoney(v.additionalPrice)}</small>` : ""}
              </button>`).join("")}
          </div>
        </div>`;
    }).join("");

    const addonsHtml = currentAddons.filter(g => g.exposed).map(group => `
      <div class="preview-addon-group" data-addon-group="${group.id}">
        <div class="preview-option-label">${escapeHtml(group.groupName)} <span class="preview-hint">최대 ${group.maxQuantity}개</span></div>
        <div class="preview-addon-rows">
          ${group.items.map(item => {
            const key = group.id + ":" + item.productId;
            const qty = selectedAddonQty[key] || 0;
            return `
            <div class="preview-addon-row">
              <span class="preview-addon-name">${escapeHtml(item.productName)}</span>
              <span class="preview-addon-price">${signedMoney(item.salePrice)}</span>
              <span class="preview-stepper">
                <button type="button" data-addon-dec="${key}" data-max="${item.maxQuantity}" data-group="${group.id}">−</button>
                <b>${qty}</b>
                <button type="button" data-addon-inc="${key}" data-max="${item.maxQuantity}" data-group="${group.id}">＋</button>
              </span>
            </div>`;
          }).join("")}
        </div>
      </div>`).join("");

    document.getElementById("preview-body").innerHTML = `
      <div class="preview-detail-image">${p.imageUrl ? `<img src="${escapeHtml(p.imageUrl)}" alt="">` : NO_IMAGE_ICON}</div>
      <div class="preview-detail-info">
        <strong class="preview-detail-name">${escapeHtml(p.productName)}</strong>
        <span class="preview-detail-price">${money(p.salePrice)}</span>
        <p class="preview-detail-meta">${escapeHtml(p.productCode)} · ${escapeHtml(p.category || "미분류")}${p.inventoryManaged !== false ? ` · 재고 ${p.stockQuantity.toLocaleString("ko-KR")}개` : ""}</p>
        ${soldOut ? '<p class="preview-status-note is-soldout">현재 품절 상태입니다. 주문 화면에서는 선택할 수 없습니다.</p>' : ""}
        ${stopped ? '<p class="preview-status-note">판매 노출이 꺼져 있어 실제 주문 화면에는 표시되지 않습니다.</p>' : ""}
      </div>
      ${groupsHtml}
      ${addonsHtml}
      <div class="preview-qty-row">
        <span class="preview-option-label">수량 선택</span>
        <span class="preview-stepper">
          <button type="button" id="preview-qty-dec">−</button><b id="preview-qty-value">${quantity}</b><button type="button" id="preview-qty-inc">＋</button>
        </span>
      </div>
      <div class="preview-message" id="preview-detail-message"></div>
    `;

    document.querySelectorAll(".preview-pill[data-type='SINGLE']").forEach(btn => btn.onclick = () => {
      selectedOptionValues[btn.dataset.group] = new Set([Number(btn.dataset.value)]);
      renderDetail();
    });
    document.querySelectorAll(".preview-pill[data-type='MULTIPLE']").forEach(btn => btn.onclick = () => {
      const set = selectedOptionValues[btn.dataset.group] || new Set();
      const valueId = Number(btn.dataset.value);
      const group = currentGroups.find(g => g.id === Number(btn.dataset.group));
      if (set.has(valueId)) set.delete(valueId);
      else if (set.size < group.maxSelection) set.add(valueId);
      selectedOptionValues[btn.dataset.group] = set;
      renderDetail();
    });
    document.querySelectorAll("[data-addon-inc]").forEach(btn => btn.onclick = () => {
      const key = btn.dataset.addonInc, max = Number(btn.dataset.max), groupId = btn.dataset.group;
      const groupTotal = totalAddonQtyInGroup(groupId);
      const group = currentAddons.find(g => g.id === Number(groupId));
      if ((selectedAddonQty[key] || 0) < max && groupTotal < group.maxQuantity) {
        selectedAddonQty[key] = (selectedAddonQty[key] || 0) + 1;
        renderDetail();
      }
    });
    document.querySelectorAll("[data-addon-dec]").forEach(btn => btn.onclick = () => {
      const key = btn.dataset.addonDec;
      if (selectedAddonQty[key] > 0) { selectedAddonQty[key] -= 1; renderDetail(); }
    });
    document.getElementById("preview-qty-inc").onclick = () => { quantity += 1; renderDetail(); };
    document.getElementById("preview-qty-dec").onclick = () => { if (quantity > 1) { quantity -= 1; renderDetail(); } };

    renderFooter();
  }

  function totalAddonQtyInGroup(groupId) {
    return Object.keys(selectedAddonQty).filter(k => k.startsWith(groupId + ":")).reduce((sum, k) => sum + selectedAddonQty[k], 0);
  }

  function currentUnitAddition() {
    let add = 0;
    currentGroups.forEach(group => {
      const set = selectedOptionValues[group.id];
      if (!set) return;
      group.values.forEach(v => { if (set.has(v.id)) add += Number(v.additionalPrice); });
    });
    return add;
  }

  function currentAddonAddition() {
    let add = 0;
    currentAddons.forEach(group => group.items.forEach(item => {
      const qty = selectedAddonQty[group.id + ":" + item.productId] || 0;
      add += Number(item.salePrice) * qty;
    }));
    return add;
  }

  function renderFooter() {
    const p = currentProduct;
    const unit = Number(p.salePrice) + currentUnitAddition();
    const total = unit * quantity + currentAddonAddition() * quantity;
    let footer = root.querySelector(".preview-footer");
    if (!footer) {
      footer = document.createElement("footer");
      footer.className = "preview-footer";
      root.querySelector(".preview-panel").appendChild(footer);
    }
    const disabled = p.saleStatus !== "ON_SALE";
    footer.innerHTML = `
      <div class="preview-total"><span>총 주문금액</span><strong>${money(total)}</strong></div>
      <button type="button" class="preview-cta" id="preview-add-cart" ${disabled ? "disabled" : ""}>${disabled ? "주문 불가" : "장바구니 담기"}</button>`;
    const addBtn = document.getElementById("preview-add-cart");
    if (addBtn) addBtn.onclick = addToCart;
  }

  function clearFooter() {
    const footer = root.querySelector(".preview-footer");
    if (footer) footer.remove();
  }

  function addToCart() {
    for (const group of currentGroups.filter(g => g.exposed)) {
      const set = selectedOptionValues[group.id] || new Set();
      if (group.requiredOption && set.size < Math.max(1, group.minSelection)) {
        document.getElementById("preview-detail-message").textContent = `"${group.groupName}"에서 필수 항목을 선택해주세요.`;
        return;
      }
    }
    const optionValueIds = [];
    Object.values(selectedOptionValues).forEach(set => set.forEach(id => optionValueIds.push(id)));
    const addOns = [];
    Object.keys(selectedAddonQty).forEach(key => {
      const qty = selectedAddonQty[key];
      if (qty > 0) {
        const [groupId, productId] = key.split(":").map(Number);
        addOns.push({ addonGroupId: groupId, productId, quantity: qty });
      }
    });
    cart.push({
      product: currentProduct,
      quantity,
      optionValueIds,
      addOns,
      unitLabel: buildOptionSummary(),
      lineTotal: (Number(currentProduct.salePrice) + currentUnitAddition() + currentAddonAddition()) * quantity
    });
    updateCartBadge();
    renderCart();
  }

  function buildOptionSummary() {
    const parts = [];
    currentGroups.forEach(group => {
      const set = selectedOptionValues[group.id];
      if (!set || !set.size) return;
      group.values.filter(v => set.has(v.id)).forEach(v => parts.push(v.valueName));
    });
    currentAddons.forEach(group => group.items.forEach(item => {
      const qty = selectedAddonQty[group.id + ":" + item.productId] || 0;
      if (qty > 0) parts.push(`${item.productName} x${qty}`);
    }));
    return parts.join(", ");
  }

  function updateCartBadge() {
    const badge = document.getElementById("preview-cart-badge");
    if (!badge) return;
    badge.hidden = cart.length === 0;
    badge.textContent = String(cart.length);
  }

  function renderCart() {
    view = "cart";
    clearFooter();
    setHeadTitle("장바구니", true);
    const body = document.getElementById("preview-body");
    if (cart.length === 0) {
      body.innerHTML = `<p class="preview-empty">담긴 상품이 없습니다. 전체 미리보기에서 상품을 담아보세요.</p>`;
      return;
    }
    body.innerHTML = `
      <div class="preview-cart-list">
        ${cart.map((line, idx) => `
          <div class="preview-cart-item">
            <div><strong>${escapeHtml(line.product.productName)}</strong> × ${line.quantity}</div>
            ${line.unitLabel ? `<small>${escapeHtml(line.unitLabel)}</small>` : ""}
            <div class="preview-cart-item-foot"><span>${money(line.lineTotal)}</span><button type="button" data-remove="${idx}">삭제</button></div>
          </div>`).join("")}
      </div>
      <div class="preview-order-form">
        <label>구매자명 <em>*</em><input id="preview-buyer-name" placeholder="예: 홍길동" value="미리보기 고객"></label>
        <label>연락처<input id="preview-buyer-phone" placeholder="선택 입력"></label>
      </div>
      <div class="preview-message" id="preview-cart-message"></div>
    `;
    document.querySelectorAll("[data-remove]").forEach(btn => btn.onclick = () => { cart.splice(Number(btn.dataset.remove), 1); updateCartBadge(); renderCart(); });

    const footer = document.createElement("footer");
    footer.className = "preview-footer";
    const grandTotal = cart.reduce((sum, line) => sum + line.lineTotal, 0);
    footer.innerHTML = `
      <div class="preview-total"><span>총 주문금액</span><strong>${money(grandTotal)}</strong></div>
      <button type="button" class="preview-cta" id="preview-submit-order">주문하기</button>`;
    root.querySelector(".preview-panel").appendChild(footer);
    document.getElementById("preview-submit-order").onclick = submitOrder;
  }

  async function submitOrder() {
    const buyerName = document.getElementById("preview-buyer-name").value.trim();
    const message = document.getElementById("preview-cart-message");
    if (!buyerName) { message.textContent = "구매자명은 필수입니다."; return; }
    const submitBtn = document.getElementById("preview-submit-order");
    submitBtn.disabled = true;
    const payload = {
      buyerName,
      buyerPhone: document.getElementById("preview-buyer-phone").value.trim() || null,
      items: cart.map(line => ({
        productId: line.product.id,
        quantity: line.quantity,
        optionValueIds: line.optionValueIds,
        addOns: line.addOns
      }))
    };
    try {
      const response = await fetch("/admin/api/commerce/orders", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "주문 생성에 실패했습니다.");
      cart = [];
      updateCartBadge();
      clearFooter();
      document.getElementById("preview-body").innerHTML = `
        <div class="preview-success">
          <strong>주문이 생성됐습니다.</strong>
          <p>주문번호 <b>${escapeHtml(data.orderNo)}</b> · ${money(data.payableAmount)}</p>
          <a class="preview-cta" href="/admin/commerce/orders">주문 관리에서 확인하기 →</a>
        </div>`;
    } catch (error) {
      message.textContent = error.message;
      submitBtn.disabled = false;
    }
  }

  window.openProductPreview = openDetail;
  window.openCatalogPreview = openCatalog;
})();
