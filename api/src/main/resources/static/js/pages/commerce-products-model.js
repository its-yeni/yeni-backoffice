window.CommerceProductListModel = (function () {
  function filter(products, category) {
    return category ? products.filter(product => product.category === category) : products;
  }

  function summarize(products) {
    return {
      total: products.length,
      onSale: products.filter(product => product.saleStatus === "ON_SALE").length,
      soldOut: products.filter(product => product.saleStatus === "SOLD_OUT").length,
      exposed: products.filter(product => product.saleStatus !== "STOPPED").length
    };
  }

  function row(product, index) {
    const stock = product.inventoryManaged === false ? "미사용" : AppFormat.number(product.stockQuantity);
    return `<tr class="is-editable-row" data-product-row="${product.id}">
      <td class="row-index">${index}</td>
      <td><button type="button" class="table-product" data-edit="${product.id}"><span class="table-product-thumb">${product.imageUrl ? `<img src="${escapeHtml(product.imageUrl)}" alt="">` : ""}</span><span><strong>${escapeHtml(product.productName)}</strong><small>${escapeHtml(product.productCode)}</small></span></button></td>
      <td>${escapeHtml(product.category || "미분류")}</td><td class="amount">${money(product.salePrice)}</td><td class="number">${stock}</td>
      <td><label class="switch"><input type="checkbox" data-status="SOLD_OUT" data-id="${product.id}" ${product.saleStatus === "SOLD_OUT" ? "checked" : ""}><span></span></label></td>
      <td><label class="switch"><input type="checkbox" data-status="ON_SALE" data-id="${product.id}" ${product.saleStatus === "ON_SALE" ? "checked" : ""}><span></span></label></td>
      <td>${formatDate(product.updatedAt)}</td><td class="actions"><button type="button" class="row-icon-btn" data-row-edit="${product.id}" title="상품 수정">수정</button><button type="button" class="row-icon-btn" data-options="${product.id}" title="옵션 설정">옵션</button><button type="button" class="row-icon-btn" data-preview="${product.id}" title="미리보기">보기</button></td></tr>`;
  }

  function formatDate(value) {
    return value ? new Date(value).toLocaleDateString("ko-KR", {year: "numeric", month: "2-digit", day: "2-digit"}) : "-";
  }

  return Object.freeze({filter, summarize, row});
})();
