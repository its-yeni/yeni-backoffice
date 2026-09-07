/* ─────────────────────────────────────────────────────────────────────────────
   상태 언어 — enum → 한글 라벨 + 색(good/warn/bad/idle/info), 한 곳에서.
   모든 페이지 렌더러가 여기서 라벨과 배지를 가져온다.
   badge(domain, value) → <span class="status ..."> HTML
   dot(domain, value)   → <span class="status-dot ..."> HTML
   ──────────────────────────────────────────────────────────────────────────── */
(function () {
  var D = {
    // 결제
    payment: {
      READY:            ["결제 대기",        "idle"],
      APPROVED:         ["승인 완료",        "good"],
      APPROVE_UNKNOWN:  ["승인 결과 확인 중", "warn"],
      CANCEL_UNKNOWN:   ["취소 결과 확인 중", "warn"],
      PARTIALLY_CANCELLED: ["부분 취소",     "idle"],
      CANCELLED:        ["취소",             "idle"],
      FAILED:           ["실패",             "bad"],
      NETWORK_CANCEL:   ["망취소 필요",      "bad"]
    },
    // 주문
    order: {
      CREATED:          ["주문 생성",  "idle"],
      PAID:             ["결제 완료",  "good"],
      PREPARING:        ["출고 대기",  "warn"],
      SHIPPING:         ["배송 중",    "info"],
      CONFIRMED:        ["구매 확정",  "good"],
      CANCELLED:        ["취소",       "idle"]
    },
    // 매출 원장
    ledger: {
      NOT_SETTLED:      ["정산 대기",       "idle"],
      CALCULATED:       ["정산 계산됨",     "idle"],
      SETTLED:          ["정산 확정",       "good"],
      PAID:             ["지급 완료",       "good"],
      CARRIED_OVER:     ["다음 정산 차감",  "warn"],
      PURCHASE_PENDING: ["구매 확정 대기",  "warn"],
      CANCEL_SALE:      ["취소 매출",       "bad"]
    },
    // 정산
    settlement: {
      DRAFT:            ["초안",       "idle"],
      CONFIRMED:        ["확정",       "good"],
      READY:            ["지급 준비",  "info"],
      PAID:             ["지급 완료",  "good"]
    },
    // 복구 작업
    recovery: {
      READY:            ["재시도 대기", "warn"],
      PROCESSING:       ["처리 중",     "info"],
      FAILED:           ["재시도 실패", "bad"],
      SUCCESS:          ["처리 완료",   "good"]
    },
    // 대사
    reconciliation: {
      MATCHED:          ["일치",        "good"],
      INTERNAL_ONLY:    ["내부만 존재", "warn"],
      EXTERNAL_ONLY:    ["PG만 존재",   "warn"],
      AMOUNT_MISMATCH:  ["금액 불일치", "bad"]
    }
  };

  function entry(domain, value) {
    var m = D[domain];
    if (!m || value == null) return [String(value == null ? "" : value), "idle"];
    return m[value] || [String(value), "idle"];
  }

  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) {
    return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }

  window.StatusCatalog = {
    label: function (domain, value) { return entry(domain, value)[0]; },
    tone:  function (domain, value) { return entry(domain, value)[1]; },
    badge: function (domain, value) {
      var e = entry(domain, value);
      return '<span class="status ' + e[1] + '">' + esc(e[0]) + '</span>';
    },
    dot: function (domain, value) {
      var e = entry(domain, value);
      return '<span class="status-dot ' + e[1] + '">' + esc(e[0]) + '</span>';
    },
    raw: D
  };

  /* 뒤로 호환: 예전 페이지가 참조하던 이름 유지 (커머스 도메인은 별도 파일) */
  if (!window.CommerceStatusCatalog) window.CommerceStatusCatalog = {};
})();
