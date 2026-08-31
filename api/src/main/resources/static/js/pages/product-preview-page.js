(function () {
  const frame = document.getElementById('device-frame'), grid = document.getElementById('store-products'), tabs = document.getElementById('store-categories'),
    empty = document.getElementById('store-empty'), storeMain = grid.closest('main'), posShell = document.getElementById('pos-shell'), storeShell = frame.querySelector('.store-shell');
  let products = [], categories = [], activeCategory = '', searchKeyword = '', selectedProduct = null, runLog = [];
  // 장바구니: 한 주문에 여러 상품을 담아 한 번에 결제한다. 각 항목이 자기 옵션/수량/금액을 들고 있다.
  let cart = [];
  const DELIVERY_LABEL = { PREPARING: '배송 준비 중', IN_TRANSIT: '배송 중', DELIVERED: '배송 완료' };
  const DELIVERY_TONE = { IN_TRANSIT: 'is-warning', DELIVERED: 'is-success' };

  /* ---- 채널 전환 (WEB / POS) ---- */
  document.querySelectorAll('[data-device]').forEach(button => button.onclick = () => {
    document.querySelectorAll('[data-device]').forEach(item => item.classList.toggle('active', item === button));
    const isPos = button.dataset.device === 'pos';
    posShell.hidden = !isPos;
    storeShell.hidden = isPos;
    document.getElementById('summary-channel').textContent = isPos ? 'POS(매장)' : 'WEB(쇼핑몰)';
  });

  /* ---- 처리 단계 ---- */
  function setStep(step) {
    document.querySelectorAll('.simulation-steps li').forEach((item, index) => item.classList.toggle('active', index < step));
    const runBtn = document.getElementById('run-scenario');
    if (step === 4) { runBtn.textContent = '시나리오 완료'; runBtn.disabled = true; }
    else { runBtn.textContent = '시나리오 실행'; runBtn.disabled = false; }
  }

  function cartCount() { return cart.reduce((sum, item) => sum + item.quantity, 0); }
  function cartTotal() { return cart.reduce((sum, item) => sum + item.lineTotal, 0); }

  /* ---- 시나리오 요약 ---- */
  function updateSummary({ product, options, quantity, pay, total } = {}) {
    if (product !== undefined) document.getElementById('summary-product').textContent = product || '선택 전';
    if (options !== undefined) document.getElementById('summary-options').textContent = options || '-';
    if (quantity !== undefined) document.getElementById('summary-qty').textContent = quantity ? quantity + '개' : '-';
    if (pay !== undefined) document.getElementById('summary-pay').textContent = pay || '-';
    if (total !== undefined) document.getElementById('summary-total').textContent = total || '-';
  }
  function resetSummary() { updateSummary({ product: '', options: '', quantity: '', pay: '', total: '' }); }
  function syncCartSummary() {
    if (!cart.length) { resetSummary(); return; }
    updateSummary({
      product: cart.length === 1 ? cart[0].productName : `${cart[0].productName} 외 ${cart.length - 1}건`,
      options: '-', quantity: cartCount(), total: money(cartTotal())
    });
  }

  function logEvent(label, ref) {
    runLog.unshift({ time: new Date(), label, ref: ref || '' });
    runLog = runLog.slice(0, 8);
    const list = document.getElementById('run-log');
    list.innerHTML = runLog.map((entry, index) => `<li class="${index === 0 ? 'is-new' : ''}"><span class="log-dot"></span><span class="log-time">${fmtTime(entry.time)}</span><span class="log-label">${escapeHtml(entry.label)}</span>${entry.ref ? `<span class="log-ref">${escapeHtml(entry.ref)}</span>` : ''}</li>`).join('');
  }
  function fmtTime(date) { return date.toTimeString().slice(0, 8); }

  function removeDetail() { document.getElementById('store-detail')?.remove(); }

  /* ---- 헤더 장바구니 아이콘: 담긴 개수 배지 + 클릭 시 장바구니 화면 ---- */
  const cartIcon = document.querySelectorAll('.store-icons .store-icon-item')[1];
  if (cartIcon) {
    cartIcon.style.cursor = 'pointer';
    cartIcon.onclick = () => { if (cart.length) showCart(); };
  }
  function renderCartBadge() {
    if (!cartIcon) return;
    let badge = cartIcon.querySelector('.store-cart-badge');
    if (!badge) { badge = document.createElement('span'); badge.className = 'store-cart-badge'; cartIcon.appendChild(badge); }
    badge.textContent = cartCount();
    badge.hidden = cart.length === 0;
  }

  /* ---- 카탈로그 하단 고정 "장바구니 N건 · 합계 · 주문하기" 바 ---- */
  function renderCartBar() {
    let bar = document.getElementById('store-cart-bar');
    if (!cart.length) { bar?.remove(); return; }
    if (!bar) {
      bar = document.createElement('div');
      bar.id = 'store-cart-bar'; bar.className = 'store-cart-bar';
      storeMain.appendChild(bar);
    }
    bar.innerHTML = `<div><b>장바구니 ${cartCount()}개</b><span>${cart.length}개 상품</span></div><strong>${money(cartTotal())}</strong><button type="button" class="store-buy" id="cart-checkout">주문하기</button>`;
    bar.querySelector('#cart-checkout').onclick = () => showCheckout();
  }
  function refreshCartUi() { renderCartBadge(); renderCartBar(); syncCartSummary(); }

  function showCatalog() {
    setStep(cart.length ? 1 : 1); removeDetail(); selectedProduct = null;
    document.querySelector('.store-heading').hidden = false; tabs.hidden = false; grid.hidden = false;
    const keyword = searchKeyword.trim().toLowerCase();
    const visible = products.filter(item => item.saleStatus === 'ON_SALE' && (!activeCategory || item.category === activeCategory)
      && (!keyword || item.productName.toLowerCase().includes(keyword)));
    tabs.innerHTML = ['', ...categories].map(name => `<button type="button" class="${name === activeCategory ? 'active' : ''}" data-category="${escapeHtml(name)}">${escapeHtml(name || '전체')}</button>`).join('');
    tabs.querySelectorAll('[data-category]').forEach(button => button.onclick = () => { activeCategory = button.dataset.category; showCatalog(); });
    document.querySelector('.store-heading h2').textContent = keyword ? `'${keyword}' 검색 결과` : '상품';
    document.querySelector('.store-heading p').textContent = keyword ? `${visible.length}개의 상품을 찾았습니다.` : '상품을 골라 장바구니에 담고 한 번에 주문하세요.';
    grid.innerHTML = visible.map(item => `<button type="button" class="store-product" data-product="${item.id}"><span class="store-product-image">${item.imageUrl ? `<img src="${escapeHtml(item.imageUrl)}" alt="${escapeHtml(item.productName)}">` : NO_IMAGE_ICON}<span class="store-wish" aria-hidden="true">${HEART_ICON}</span></span><small class="store-product-category">${escapeHtml(item.category || '')}</small><strong>${escapeHtml(item.productName)}</strong><small class="store-product-price">${money(item.salePrice)}</small></button>`).join('');
    grid.querySelectorAll('[data-product]').forEach(button => button.onclick = () => showDetail(Number(button.dataset.product)));
    empty.hidden = visible.length > 0;
    empty.textContent = keyword ? '검색 결과가 없습니다.' : '판매 중인 상품이 없습니다.';
    refreshCartUi();
  }

  async function showDetail(id) {
    const product = products.find(item => item.id === id);
    if (!product) return;
    selectedProduct = product;
    logEvent('상품 선택', product.productName);
    const response = await fetch(`/admin/api/commerce/products/${id}/option-groups`), groups = response.ok ? await response.json() : [],
      visibleGroups = groups.filter(group => group.exposed && group.values.some(value => value.saleStatus !== 'STOPPED'));
    document.querySelector('.store-heading').hidden = true; tabs.hidden = true; grid.hidden = true; empty.hidden = true; removeDetail();
    document.getElementById('store-cart-bar')?.remove();
    updateSummary({ product: product.productName, options: '-', quantity: 1, pay: '-', total: money(product.salePrice) });
    const section = document.createElement('section');
    section.id = 'store-detail'; section.className = 'store-detail checkout-detail';
    section.innerHTML = `<button type="button" class="store-back">상품 목록</button><div class="store-detail-layout"><div class="store-detail-image">${product.imageUrl ? `<img src="${escapeHtml(product.imageUrl)}" alt="${escapeHtml(product.productName)}">` : NO_IMAGE_ICON}</div><div class="store-detail-info"><p class="store-breadcrumb">홈 <span>›</span> ${escapeHtml(product.category || '미분류')} <span>›</span> <b>${escapeHtml(product.productName)}</b></p><h2>${escapeHtml(product.productName)}</h2><strong>${money(product.salePrice)}</strong>${visibleGroups.map(group => {
      const values = group.values.filter(value => value.saleStatus !== 'STOPPED');
      return `<fieldset data-required="${group.requiredOption}"><legend>${escapeHtml(group.groupName)} ${group.requiredOption ? '<em>필수</em>' : '<small>선택</small>'}</legend><div>${values.map(value => `<label><input class="store-option-input" type="${group.selectionType === 'MULTIPLE' ? 'checkbox' : 'radio'}" name="preview-${group.id}" value="${value.id}" data-name="${escapeHtml(value.valueName)}" data-addition="${Number(value.additionalPrice || 0)}"><span>${escapeHtml(value.valueName)}${Number(value.additionalPrice) ? `<small>+${money(value.additionalPrice)}</small>` : ''}</span></label>`).join('')}</div></fieldset>`;
    }).join('')}<div class="quantity-row"><span>수량</span><button type="button" data-quantity="-1">−</button><b id="preview-quantity">1</b><button type="button" data-quantity="1">＋</button></div><div class="store-detail-total"><span>총 상품 금액</span><b id="store-preview-total"></b></div><div class="store-detail-actions"><button class="store-buy outline" type="button" id="add-to-cart">장바구니 담기</button><button class="store-buy" type="button" id="buy-now">바로 주문</button></div></div></div>`;
    storeMain.appendChild(section);
    section.querySelector('.store-back').onclick = showCatalog;
    let quantity = 1;
    const syncSummary = () => {
      const checked = [...section.querySelectorAll('.store-option-input:checked')];
      updateSummary({ options: checked.length ? checked.map(input => input.dataset.name).join(', ') : '-', quantity });
    };
    const updateTotal = () => {
      const addition = [...section.querySelectorAll('.store-option-input:checked')].reduce((sum, input) => sum + Number(input.dataset.addition || 0), 0);
      const total = (Number(product.salePrice) + addition) * quantity;
      section.querySelector('#store-preview-total').textContent = money(total);
      updateSummary({ total: money(total) });
      syncSummary();
    };
    section.querySelectorAll('.store-option-input').forEach(input => input.onchange = updateTotal);
    section.querySelectorAll('[data-quantity]').forEach(button => button.onclick = () => { quantity = Math.max(1, Math.min(99, quantity + Number(button.dataset.quantity))); section.querySelector('#preview-quantity').textContent = quantity; updateTotal(); });
    section.querySelector('#add-to-cart').onclick = () => { if (addToCart(section, product, visibleGroups, quantity)) showCatalog(); };
    section.querySelector('#buy-now').onclick = () => { if (addToCart(section, product, visibleGroups, quantity)) showCheckout(); };
    updateTotal();
  }

  function firstMissingRequiredGroup(section, groups) {
    return groups.find(group => group.requiredOption && !section.querySelector(`input[name="preview-${group.id}"]:checked`));
  }

  /** 상세 화면의 현재 옵션/수량을 장바구니 항목으로 담는다. 필수 옵션 미선택이면 false. */
  function addToCart(section, product, groups, quantity) {
    const missing = firstMissingRequiredGroup(section, groups);
    if (missing) { AppToast.error(`${missing.groupName} 옵션을 선택해 주세요.`); return false; }
    const checked = [...section.querySelectorAll('.store-option-input:checked')];
    const optionValueIds = checked.map(input => Number(input.value));
    const optionAddition = checked.reduce((sum, input) => sum + Number(input.dataset.addition || 0), 0);
    const unitPrice = Number(product.salePrice) + optionAddition;
    cart.push({
      productId: product.id, productName: product.productName, category: product.category || '미분류',
      imageUrl: product.imageUrl, unitPrice, optionValueIds,
      optionSummary: checked.length ? checked.map(input => input.dataset.name).join(' / ') : '-',
      quantity, lineTotal: unitPrice * quantity
    });
    logEvent('장바구니 담기', `${product.productName} · ${quantity}개`);
    AppToast.success(`${product.productName}을(를) 장바구니에 담았습니다.`);
    refreshCartUi();
    return true;
  }

  function changeCartQty(index, delta) {
    const item = cart[index];
    item.quantity = Math.max(1, Math.min(99, item.quantity + delta));
    item.lineTotal = item.unitPrice * item.quantity;
    showCart();
  }
  function removeCartItem(index) {
    cart.splice(index, 1);
    if (cart.length) showCart(); else showCatalog();
  }

  function showCart() {
    setStep(1); removeDetail();
    document.querySelector('.store-heading').hidden = true; tabs.hidden = true; grid.hidden = true; empty.hidden = true;
    document.getElementById('store-cart-bar')?.remove();
    const section = document.createElement('section');
    section.id = 'store-detail'; section.className = 'store-detail checkout-detail';
    section.innerHTML = `<button type="button" class="store-back">상품 더 담기</button>
      <div class="cart-view">
        <h2>장바구니 <small>${cartCount()}개</small></h2>
        <table class="cart-table"><thead><tr><th>분류</th><th>상품</th><th>옵션</th><th>단가</th><th>수량</th><th>금액</th><th></th></tr></thead>
        <tbody>${cart.map((item, index) => `<tr>
          <td><small>${escapeHtml(item.category)}</small></td>
          <td><strong>${escapeHtml(item.productName)}</strong></td>
          <td><small>${escapeHtml(item.optionSummary)}</small></td>
          <td>${money(item.unitPrice)}</td>
          <td><span class="cart-qty"><button type="button" data-cart-minus="${index}">−</button>${item.quantity}<button type="button" data-cart-plus="${index}">＋</button></span></td>
          <td><b>${money(item.lineTotal)}</b></td>
          <td><button type="button" class="cart-remove" data-cart-remove="${index}">삭제</button></td></tr>`).join('')}</tbody>
        <tfoot><tr><td colspan="5">총 상품 금액</td><td colspan="2"><strong>${money(cartTotal())}</strong></td></tr></tfoot></table>
        <button class="store-buy" type="button" id="cart-to-checkout">주문하기 (${cartCount()}개)</button>
      </div>`;
    storeMain.appendChild(section);
    section.querySelector('.store-back').onclick = showCatalog;
    section.querySelectorAll('[data-cart-minus]').forEach(b => b.onclick = () => changeCartQty(Number(b.dataset.cartMinus), -1));
    section.querySelectorAll('[data-cart-plus]').forEach(b => b.onclick = () => changeCartQty(Number(b.dataset.cartPlus), 1));
    section.querySelectorAll('[data-cart-remove]').forEach(b => b.onclick = () => removeCartItem(Number(b.dataset.cartRemove)));
    section.querySelector('#cart-to-checkout').onclick = () => showCheckout();
    syncCartSummary();
  }

  function showCheckout() {
    if (!cart.length) { AppToast.error('장바구니가 비어 있습니다.'); return; }
    setStep(2);
    removeDetail();
    document.querySelector('.store-heading').hidden = true; tabs.hidden = true; grid.hidden = true; empty.hidden = true;
    document.getElementById('store-cart-bar')?.remove();
    const total = money(cartTotal());
    const section = document.createElement('section');
    section.id = 'store-detail'; section.className = 'store-detail checkout-detail';
    section.innerHTML = `<button type="button" class="store-back">장바구니로 돌아가기</button>
      <div class="checkout-layout">
        <div class="checkout-main">
          <section class="checkout-block"><h3>1. 주문상품 <small>${cartCount()}개</small></h3>
            ${cart.map(item => `<div class="checkout-item-row"><span class="checkout-item-thumb">${item.imageUrl ? `<img src="${escapeHtml(item.imageUrl)}" alt="">` : NO_IMAGE_ICON}</span><div><strong>${escapeHtml(item.productName)}</strong><small>${escapeHtml(item.category)} · ${escapeHtml(item.optionSummary)}</small></div><b>${item.quantity}개</b><em>${money(item.lineTotal)}</em></div>`).join('')}</section>
          <section class="checkout-block"><h3>2. 주문자 정보</h3><label>구매자명<input id="checkout-name" value="포트폴리오 고객" maxlength="50"></label><label>연락처<input id="checkout-phone" value="010-0000-0000" maxlength="20"></label></section>
          <section class="checkout-block"><h3>3. 배송지 정보</h3><label>받는 분<input id="checkout-receiver-name" value="포트폴리오 고객" maxlength="50"></label><label>연락처<input id="checkout-receiver-phone" value="010-0000-0000" maxlength="20"></label><label>주소<input id="checkout-address1" value="서울특별시 강남구 테헤란로 123" maxlength="200"></label><label>상세주소<input id="checkout-address2" value="예니빌딩 101동 1001호" maxlength="100"></label><label>배송 요청사항<select id="checkout-delivery-request"><option value="">선택 안함</option><option value="문 앞에 놓아주세요">문 앞에 놓아주세요</option><option value="경비실에 맡겨주세요">경비실에 맡겨주세요</option><option value="배송 전 연락 부탁드려요">배송 전 연락 부탁드려요</option></select></label></section>
          <section class="checkout-block"><h3>4. 결제 수단</h3><div class="pay-methods"><label class="active"><input type="radio" name="pay-method" value="신용카드" checked>신용카드</label><label><input type="radio" name="pay-method" value="계좌이체">계좌이체</label><label><input type="radio" name="pay-method" value="간편결제">간편결제</label></div></section>
        </div>
        <aside class="checkout-summary">
          <h3>주문 요약</h3>
          ${cart.map(item => `<div class="checkout-summary-item"><span>${escapeHtml(item.productName)} · ${item.quantity}개</span><b>${money(item.lineTotal)}</b></div>`).join('')}
          <dl><div><dt>상품 금액</dt><dd>${total}</dd></div><div><dt>배송비</dt><dd>0원</dd></div></dl>
          <div class="checkout-summary-total"><span>총 결제 금액</span><b>${total}</b></div>
          <button class="store-buy" id="execute-payment">${total} 결제하기</button>
          <p class="checkout-message" id="checkout-message"></p>
          <p class="checkout-note">본 화면은 Mock PG가 연동된 테스트 환경입니다. 실제 결제는 이루어지지 않으며, 시나리오에 따라 다양한 결제 결과를 확인할 수 있습니다.</p>
        </aside>
      </div>`;
    storeMain.appendChild(section);
    section.querySelector('.store-back').onclick = showCart;
    section.querySelectorAll('input[name="pay-method"]').forEach(input => input.onchange = () => {
      section.querySelectorAll('.pay-methods label').forEach(label => label.classList.toggle('active', label.querySelector('input').checked));
      updateSummary({ pay: input.value });
      logEvent('결제 수단 선택', input.value);
    });
    updateSummary({ pay: '신용카드', total });
    logEvent('주문 확인 화면 진입', `${cartCount()}개 상품`);
    section.querySelector('#execute-payment').onclick = async () => {
      const button = section.querySelector('#execute-payment'), message = section.querySelector('#checkout-message');
      button.disabled = true; message.textContent = '주문과 결제를 처리하고 있습니다.'; setStep(3);
      const payMethod = section.querySelector('input[name="pay-method"]:checked').value;
      logEvent('결제 요청 전송', payMethod);
      const startedAt = performance.now();
      try {
        const delivery = {
          receiverName: section.querySelector('#checkout-receiver-name').value,
          receiverPhone: section.querySelector('#checkout-receiver-phone').value,
          address1: section.querySelector('#checkout-address1').value,
          address2: section.querySelector('#checkout-address2').value,
          deliveryRequest: section.querySelector('#checkout-delivery-request').value
        };
        const result = await apiPost('/admin/api/commerce/orders/mock-scenario', {
          scenario: document.getElementById('simulation-scenario').value,
          buyerName: section.querySelector('#checkout-name').value,
          buyerPhone: section.querySelector('#checkout-phone').value,
          items: cart.map(item => ({ productId: item.productId, quantity: item.quantity, optionValueIds: item.optionValueIds, addOns: [] })),
          delivery
        });
        logEvent('PG 응답 수신 (' + Math.round(performance.now() - startedAt) + 'ms)', result.order.paymentId ? 'PAY-' + result.order.paymentId : result.order.orderNo);
        showResult(section, result);
      } catch (error) {
        message.textContent = error.message;
        logEvent('결제 요청 실패', error.message);
        button.disabled = false;
      }
    };
  }

  function showResult(section, result) {
    setStep(4);
    const order = result.order, success = order.paymentStatus === 'APPROVED', unknown = order.paymentStatus === 'APPROVE_UNKNOWN';
    if (success) cart = [];
    refreshCartUi();
    if (new URLSearchParams(location.search).get('demo') === '1' || sessionStorage.getItem('yeni-demo-flow')) {
      sessionStorage.setItem('yeni-demo-flow', JSON.stringify({startedAt:Date.now(),orderNo:order.orderNo,paymentId:order.paymentId||null,tid:order.tid||null,businessDate:new Date().toISOString().slice(0,10),productName:order.productName||'-',amount:order.payableAmount,paymentStatus:order.paymentStatus}));
      window.dispatchEvent(new CustomEvent('yeni-demo-updated'));
    }
    logEvent('주문 상태 갱신 (' + order.paymentStatus + ')', order.orderNo);
    if (success) logEvent('매출 원장 반영 대상 등록', order.orderNo);
    const pipeline = [
      { label: '주문 생성', done: true },
      { label: 'PG 승인 요청·응답', done: true, warn: unknown },
      { label: '주문 상태 갱신', done: true },
      { label: '매출 원장 반영', done: success, pending: unknown },
      { label: '정산 대상 등록', done: success, pending: unknown },
    ];
    section.innerHTML = `<div class="payment-result ${success ? 'success' : unknown ? 'unknown' : 'failed'}"><div class="result-icon">${success ? CHECK_ICON : unknown ? QUESTION_ICON : CROSS_ICON}</div><small>${escapeHtml(result.scenarioLabel)}</small><h2>${success ? '결제가 승인되었습니다' : unknown ? '승인 결과를 확인 중입니다' : '결제가 완료되지 않았습니다'}</h2><p>${escapeHtml(result.resultMessage || order.lastMessage || '처리 결과를 확인해 주세요.')}</p>
      <div class="result-grid"><div><h4>주문 결과</h4><dl><div><dt>주문번호</dt><dd>${escapeHtml(order.orderNo)}</dd></div><div><dt>주문 상품</dt><dd>${escapeHtml(order.productName || '-')}</dd></div><div><dt>상품 수</dt><dd>${order.itemCount || 1}건</dd></div></dl></div>
      <div><h4>PG 처리 결과</h4><dl><div><dt>결제 ID</dt><dd>${order.paymentId ? 'PAY-' + order.paymentId : '-'}</dd></div><div><dt>TID</dt><dd>${escapeHtml(order.tid || '-')}</dd></div><div><dt>결제 상태</dt><dd>${escapeHtml(order.paymentStatus)}</dd></div></dl></div>
      <div><h4>결제 금액</h4><dl><div><dt>상품 금액</dt><dd>${money(order.productAmount)}</dd></div><div><dt>배송비</dt><dd>${money(order.deliveryFee || 0)}</dd></div><div class="total"><dt>최종 결제 금액</dt><dd>${money(order.payableAmount)}</dd></div></dl></div></div>
      ${order.delivery && success ? `<div class="delivery-info"><h4>배송 정보</h4><dl><div><dt>받는 분</dt><dd>${escapeHtml(order.delivery.receiverName)} · ${escapeHtml(order.delivery.receiverPhone)}</dd></div><div><dt>배송지</dt><dd>${escapeHtml(order.delivery.address1)}${order.delivery.address2 ? ' ' + escapeHtml(order.delivery.address2) : ''}</dd></div>${order.delivery.deliveryRequest ? `<div><dt>요청사항</dt><dd>${escapeHtml(order.delivery.deliveryRequest)}</dd></div>` : ''}<div><dt>배송 상태</dt><dd><span class="status-indicator ${DELIVERY_TONE[order.delivery.status] || ''}">${DELIVERY_LABEL[order.delivery.status] || order.delivery.status}</span></dd></div></dl></div>` : ''}
      <div class="result-pipeline"><h4>이 요청으로 생성·변경된 데이터</h4><ol>${pipeline.map(step => `<li class="${step.done ? (step.warn || step.pending ? 'pending' : 'done') : 'skip'}">${step.done ? (step.warn || step.pending ? DASH_ICON : CHECK_SM_ICON) : DASH_ICON}<span>${escapeHtml(step.label)}</span></li>`).join('')}</ol></div>
      <div class="result-actions"><a class="store-buy" href="/admin/payment-operations${order.paymentId ? `?paymentId=${order.paymentId}` : ''}">PG 거래에서 추적</a><a href="/admin/payment-operations/sales-ledger?keyword=${encodeURIComponent(order.orderNo)}">매출 원장 확인</a><a href="/admin/payment-operations/settlements">정산 관리 확인</a><button type="button" id="new-order">다른 주문 테스트</button></div></div>`;
    section.querySelector('#new-order').onclick = showCatalog;
    updateSummary({ total: money(order.payableAmount) });
  }

  document.getElementById('store-search-input').addEventListener('input', event => {
    searchKeyword = event.target.value;
    if (document.getElementById('store-detail')) return;
    showCatalog();
  });

  document.getElementById('simulation-scenario').addEventListener('change', event => {
    logEvent('시나리오 변경', event.target.selectedOptions[0].textContent);
  });

  /* ---- "시나리오 실행": 상세 화면이면 옵션 자동 선택 후 장바구니 담기, 장바구니/카탈로그면 바로 결제 진행,
     주문 확인 화면이면 결제 실행. ---- */
  document.getElementById('run-scenario').onclick = () => {
    const detail = document.getElementById('store-detail');
    const payBtn = detail?.querySelector('#execute-payment');
    if (payBtn) { payBtn.click(); return; }
    const buyNow = detail?.querySelector('#buy-now');
    if (buyNow) {
      detail.querySelectorAll('fieldset[data-required="true"]').forEach(fieldset => {
        const first = fieldset.querySelector('.store-option-input');
        if (first && ![...fieldset.querySelectorAll('.store-option-input')].some(i => i.checked)) { first.checked = true; first.dispatchEvent(new Event('change')); }
      });
      buyNow.click();
      return;
    }
    if (cart.length) { showCheckout(); return; }
    AppToast.error('먼저 상품을 선택하거나 장바구니에 담아 주세요.');
  };

  Promise.all([
    fetch('/admin/api/commerce/products?size=200&saleStatus=ON_SALE&storeCode=' + encodeURIComponent(activeStoreCode())),
    fetch('/admin/api/commerce/categories?storeCode=' + encodeURIComponent(activeStoreCode()))
  ]).then(async ([productResponse, categoryResponse]) => {
    const productData = productResponse.ok ? await productResponse.json() : { items: [] }, categoryData = categoryResponse.ok ? await categoryResponse.json() : [];
    products = productData.items || [];
    categories = categoryData.filter(item => item.exposed).sort((a, b) => a.sortOrder - b.sortOrder).map(item => item.categoryName);
    showCatalog();
  }).catch(() => { empty.hidden = false; empty.textContent = '상품 정보를 불러오지 못했습니다.'; });

  const NO_IMAGE_ICON = '<span class="plain-placeholder"><svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="8.5" cy="10.5" r="1.5"/><path d="M21 15l-5-5-4 4-3-3-5 5"/></svg></span>';
  const HEART_ICON = '<svg viewBox="0 0 24 24"><path d="M12 20.5s-7.5-4.6-10-9.3C.4 8 2 4.5 5.6 4.1c2-.2 3.7.8 4.9 2.4a5.9 5.9 0 0 1 1.5-1.7c1.3-1 3.1-1.3 4.7-.7 3 1.1 4 4.7 2.3 8-2.5 4.7-7 9.4-7 9.4Z"/></svg>';
  const CHECK_ICON = '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="m8 12.5 2.5 2.5L16 9.5"/></svg>';
  const QUESTION_ICON = '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.8.4-1 1-1 1.7"/><circle cx="12" cy="17" r=".6" fill="currentColor" stroke="none"/></svg>';
  const CROSS_ICON = '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="m9 9 6 6M15 9l-6 6"/></svg>';
  const CHECK_SM_ICON = '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="m8 12.5 2.5 2.5L16 9.5"/></svg>';
  const DASH_ICON = '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M8 12h8"/></svg>';
})();
