/* 결제 예외 처리 — 확정 안 된 결제(RecoveryTask)를 워크리스트로 처리한다. */
(function () {
  "use strict";
  var API = "/admin/api/recovery/tasks";
  var PAY = "/admin/api/payment-operations/payments";

  /* recoveryType → 화면 표현.
     retryable=true 인 유형만 [PG 재조회] 로 자동 처리, 나머지는 운영자가 결과를 확인하고 상태만 반영. */
  var TYPE = {
    APPROVE_UNKNOWN_CHECK: {
      kind: "승인 결과불명", tone: "warn", retryable: true, action: "PG 재조회", primaryDot: "결과 확인 중",
      what: "PG 승인 요청 중 결과가 불명확합니다. 재조회로 실제 승인 여부를 확정해야 합니다.",
      blocks: "확정 전까지 매출원장 SALE·정산·알림톡이 생성되지 않습니다."
    },
    CANCEL_UNKNOWN_CHECK: {
      kind: "취소 결과불명", tone: "warn", retryable: false, action: "취소 확인 완료", primaryDot: "취소 결과 확인 중",
      what: "부분취소 요청 결과가 불명확합니다. PG 관리자 콘솔에서 취소 반영 여부를 확인한 뒤 결과를 반영합니다.",
      blocks: "CANCEL 원장이 생성되지 않아 정산 금액이 취소분만큼 과다 집계됩니다."
    },
    NETWORK_CANCEL: {
      kind: "망취소", tone: "bad", retryable: false, action: "망취소 완료", primaryDot: "망취소 필요",
      what: "PG 승인은 성공했으나 승인 후 내부 처리가 실패했습니다. PG 콘솔에서 승인 취소(망취소)를 실행한 뒤 결과를 반영합니다.",
      blocks: "고객 카드에 승인 금액이 걸려 있습니다. 방치 시 민원."
    },
    APPROVE_INTERNAL_SAVE_FAILED: {
      kind: "저장 실패", tone: "idle", retryable: false, action: "처리 완료", primaryDot: "저장 재시도 대기",
      what: "PG 승인은 확인됐으나 이후 매출원장 저장이 실패했습니다. 재처리 후 결과를 반영합니다.",
      blocks: "승인은 됐는데 원장·알림톡이 없습니다."
    },
    EXTERNAL_SEND_RETRY: {
      kind: "외부전송", tone: "idle", retryable: false, action: "처리 완료", primaryDot: "재전송 대기",
      what: "외부 전송 요청이 실패했습니다. 재전송 후 결과를 반영합니다.",
      blocks: "후속 외부 연동이 완료되지 않았습니다."
    },
    ALIMTALK_RETRY: {
      kind: "알림톡", tone: "idle", retryable: false, action: "처리 완료", primaryDot: "재발송 대기",
      what: "알림톡 발송이 실패했습니다. 재발송 후 결과를 반영합니다.",
      blocks: "고객 알림이 전달되지 않았습니다."
    }
  };
  function typeMeta(t) { return TYPE[t] || { kind: t || "복구", tone: "idle", retryable: false, action: "처리 완료", primaryDot: "확인 필요", what: "", blocks: "" }; }

  var $ = function (s, r) { return (r || document).querySelector(s); };
  var esc = function (s) {
    return String(s == null ? "" : s).replace(/[&<>"]/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c];
    });
  };
  function agingClass(days) { return days >= 3 ? "d3" : days >= 1 ? "d1" : ""; }
  function agingText(created) {
    var d = new Date(created), now = new Date();
    var hrs = Math.floor((now - d) / 3600000);
    if (hrs < 1) return "방금";
    if (hrs < 24) return hrs + "시간 경과";
    return Math.floor(hrs / 24) + "일 경과";
  }

  var listEl = $("#exc-worklist"), countEl = $("#exc-count"), chipsEl = $("#exc-chips"),
      emptyEl = $("#exc-empty"), selbar = $("#exc-selbar"), selN = $("#exc-sel-n"),
      navBadge = document.querySelector('.nav a[href="/admin/payment-operations"]');
  var tasks = [];

  function load() {
    Promise.all([
      fetch(API + "?status=READY&size=100").then(function (r) { return r.json(); }),
      fetch(API + "?status=FAILED&size=100").then(function (r) { return r.json(); })
    ]).then(function (res) {
      var seen = {};
      tasks = [];
      (res[0].data || []).concat(res[1].data || []).forEach(function (t) {
        if (seen[t.id]) return; seen[t.id] = 1; tasks.push(t);
      });
      sortTasks();
      render();
    }).catch(function () {
      listEl.innerHTML = '<div class="worklist-empty show"><strong>목록을 불러오지 못했습니다</strong><p>새로고침 후 다시 시도해 주세요.</p></div>';
    });
  }

  function sortTasks() {
    var mode = $("#exc-sort").value;
    tasks.sort(function (a, b) {
      if (mode === "type") return (a.recoveryType || "").localeCompare(b.recoveryType || "");
      return new Date(a.createdAt) - new Date(b.createdAt); // 오래된 순
    });
  }

  function render() {
    var live = tasks.filter(function (t) { return t.status === "READY" || t.status === "FAILED"; });
    countEl.textContent = live.length;
    if (navBadge) {
      var b = navBadge.querySelector(".nav-badge") || (function () {
        var s = document.createElement("span"); s.className = "nav-badge"; navBadge.appendChild(s); return s;
      })();
      b.textContent = live.length || "";
      b.style.display = live.length ? "" : "none";
    }
    emptyEl.classList.toggle("show", live.length === 0);

    // 유형별 칩
    var by = {};
    live.forEach(function (t) { var k = typeMeta(t.recoveryType).kind; by[k] = (by[k] || 0) + 1; });
    chipsEl.innerHTML = Object.keys(by).map(function (k) {
      return '<span class="chip">' + esc(k) + ' <b>' + by[k] + '</b></span>';
    }).join("");

    listEl.innerHTML = live.map(itemHtml).join("");
    hydrateAmounts();
  }

  /* 목록에 보이는 행의 승인 금액을 미리 채운다 (RecoveryTask 에는 금액이 없음) */
  function hydrateAmounts() {
    listEl.querySelectorAll(".worklist-item[data-pay]").forEach(function (item) {
      var pay = item.dataset.pay, amtEl = item.querySelector("[data-amt]");
      if (!pay || !amtEl || item.dataset.amtDone) return;
      item.dataset.amtDone = "1";
      fetch(PAY + "/" + pay).then(function (r) { return r.json(); }).then(function (p) {
        if (p && p.approvedAmount != null) amtEl.textContent = Number(p.approvedAmount).toLocaleString("ko-KR") + "원";
        var chEl = item.querySelector("[data-channel]");
        if (chEl && p && p.channelType) {
          var pos = p.channelType === "POS";
          chEl.className = "channel-badge " + (pos ? "pos" : "web");
          chEl.textContent = pos ? "매장" : "온라인";
        }
      }).catch(function () {});
    });
  }

  function itemHtml(t) {
    var m = typeMeta(t.recoveryType);
    var days = Math.floor((new Date() - new Date(t.createdAt)) / 86400000);
    var kindCls = m.tone === "bad" ? "bad" : m.tone === "warn" ? "warn" : "";
    var dotCls = m.tone === "bad" ? "bad" : "warn";
    var primaryAct = m.retryable ? "retry" : "done";
    var hint = m.retryable
      ? "재조회 결과에 따라 매출원장·후속 처리가 자동으로 이어집니다"
      : "외부 처리를 마친 뒤 결과만 반영합니다";
    return '<div class="worklist-item" data-id="' + t.id + '" data-type="' + esc(t.recoveryType) + '" data-pay="' + (t.paymentId || "") + '">'
      + '<div class="worklist-row">'
      +   '<input type="checkbox" aria-label="선택"' + (m.retryable ? '' : ' disabled') + '>'
      +   '<span class="wl-kind ' + kindCls + '">' + esc(m.kind) + '</span>'
      +   '<span class="wl-main"><strong>' + esc(t.orderNo) + '</strong><span>' + esc(t.tid || "") + ' <span data-channel></span></span></span>'
      +   '<span class="wl-amt" data-amt>—</span>'
      +   '<span class="wl-age ' + agingClass(days) + '">' + esc(agingText(t.createdAt)) + '</span>'
      +   '<span class="status-dot ' + dotCls + '">' + esc(m.primaryDot) + '</span>'
      +   '<button class="btn btn-sm btn-light" data-quick type="button">' + esc(m.action) + '</button>'
      +   '<svg class="chev" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 6l6 6-6 6"/></svg>'
      + '</div>'
      + '<div class="worklist-detail">'
      +   '<dl class="wl-facts">'
      +     '<div><dt>무슨 일</dt><dd>' + esc(m.what) + (t.lastErrorMessage ? ' <b>(' + esc(t.lastErrorMessage) + ')</b>' : '') + '</dd></div>'
      +     '<div><dt>지금 막고 있는 것</dt><dd>' + esc(m.blocks) + '</dd></div>'
      +   '</dl>'
      +   '<ul class="wl-timeline" data-timeline><li>PG 로그를 불러오는 중…</li></ul>'
      +   '<div class="wl-retry">재시도 <span class="bar"><i style="width:' + Math.round((t.retryCount || 0) / (t.maxRetryCount || 5) * 100) + '%"></i></span> ' + (t.retryCount || 0) + ' / ' + (t.maxRetryCount || 5) + '</div>'
      +   '<div class="wl-result"></div>'
      +   '<div class="wl-actions">'
      +     '<button class="btn btn-primary" data-act="' + primaryAct + '" type="button">' + esc(m.action) + '</button>'
      +     '<button class="btn btn-light" data-act="fail" type="button">' + (m.retryable ? '미승인으로 종결' : '실패로 표시') + '</button>'
      +     '<span class="hint">' + hint + '</span>'
      +   '</div>'
      + '</div>'
      + '</div>';
  }

  /* 펼침 시 결제 금액 + PG 로그 채우기 */
  function hydrate(item) {
    var pay = item.dataset.pay;
    if (!pay || item.dataset.hydrated) return;
    item.dataset.hydrated = "1";
    fetch(PAY + "/" + pay).then(function (r) { return r.json(); }).then(function (p) {
      var a = item.querySelector("[data-amt]");
      if (a && p.approvedAmount != null) a.textContent = Number(p.approvedAmount).toLocaleString("ko-KR") + "원";
    }).catch(function () {});
    fetch(PAY + "/" + pay + "/pg-logs").then(function (r) { return r.json(); }).then(function (logs) {
      var tl = item.querySelector("[data-timeline]");
      if (!tl) return;
      if (!logs || !logs.length) { tl.innerHTML = '<li>PG 로그 없음</li>'; return; }
      tl.innerHTML = logs.slice(0, 6).map(function (l) {
        var body = String(l.responseBody || "");
        var ok = /success=true/.test(body);
        var unknown = /UNKNOWN/.test(body);
        var label = (l.apiType || "요청") + (ok ? " · 성공" : unknown ? " · 결과 불명" : " · 실패");
        var ts = String(l.createdAt || l.requestedAt || "");
        var tm = ts.length >= 19 ? ts.slice(11, 16) : "";
        return '<li class="' + (ok ? '' : 'err') + '">' + (tm ? '<time>' + esc(tm) + '</time>' : '') + esc(label) + '</li>';
      }).join("");
    }).catch(function () {
      var tl = item.querySelector("[data-timeline]"); if (tl) tl.innerHTML = '<li>PG 로그 조회 실패</li>';
    });
  }

  /* 조치 */
  function act(item, kind) {
    var id = item.dataset.id;
    var actions = item.querySelector(".wl-actions");
    var result = item.querySelector(".wl-result");
    actions.style.display = "none";
    result.className = "wl-result show";

    if (kind === "fail") {
      fetch(API + "/" + id + "/mark-failed", { method: "POST" }).then(function () {
        result.classList.add("ok");
        result.innerHTML = "<b>종결 처리됨.</b> 실패로 확정 · 후속 처리 없음. 감사 로그에 기록.";
        finish(item, id);
      });
      return;
    }
    if (kind === "done") {
      result.innerHTML = '<span class="wl-working"><span class="wl-spin"></span>상태 반영 중…</span>';
      fetch(API + "/" + id + "/mark-success", { method: "POST" }).then(function () {
        result.classList.add("ok");
        result.innerHTML = "<b>처리 완료로 반영됨.</b> 감사 로그에 운영자 조치가 기록됩니다.";
        finish(item, id);
      });
      return;
    }

    result.innerHTML = '<span class="wl-working"><span class="wl-spin"></span>PG에 재조회 요청 중…</span>';
    fetch(API + "/" + id + "/retry", { method: "POST" })
      .then(function (r) { return r.json().then(function (b) { return { ok: r.ok, body: b }; }); })
      .then(function (res) {
        var t = res.body || {};
        result.classList.add("ok");
        if (!res.ok) {
          result.className = "wl-result show";
          result.style.background = "#fef2f2"; result.style.color = "var(--danger)";
          result.textContent = (t.message || "재조회에 실패했습니다.");
          actions.style.display = "";
          return;
        }
        var type = item.dataset.type;
        if (t.status === "SUCCESS") {
          if (type === "NETWORK_CANCEL") result.innerHTML = "<b>망취소 완료.</b> PG 승인 취소 확인 · 고객 승인 해제 · 주문 취소 처리.";
          else if (type === "APPROVE_INTERNAL_SAVE_FAILED") result.innerHTML = "<b>재시도 성공.</b> SALE 원장 생성 · 알림톡 발송 요청 · 주문 완료.";
          else if (type === "CANCEL_UNKNOWN_CHECK") result.innerHTML = "<b>취소 확인됨.</b> CANCEL 원장 생성 · 차기 정산 반영.";
          else result.innerHTML = "<b>승인 확인됨.</b> SALE 원장 생성 · 외부전송·알림톡 요청(unique 제약으로 1회) · 주문 완료.";
          finish(item, id);
        } else {
          result.innerHTML = "재조회했으나 아직 결과를 확정할 수 없습니다. 재시도 " + (t.retryCount || "?") + " / " + (t.maxRetryCount || 5) + ".";
          actions.style.display = "";
          var bar = item.querySelector(".wl-retry .bar i");
          if (bar) bar.style.width = Math.round((t.retryCount || 0) / (t.maxRetryCount || 5) * 100) + "%";
        }
      })
      .catch(function () {
        result.className = "wl-result show"; result.style.background = "#fef2f2"; result.style.color = "var(--danger)";
        result.textContent = "요청 중 오류가 발생했습니다.";
        actions.style.display = "";
      });
  }

  function finish(item, id) {
    var cb = item.querySelector('input[type=checkbox]');
    if (cb) { cb.checked = false; cb.disabled = true; }
    tasks = tasks.map(function (t) { return String(t.id) === String(id) ? Object.assign({}, t, { status: "SUCCESS" }) : t; });
    setTimeout(function () {
      item.classList.add("is-done");
      setTimeout(function () { render(); refreshSel(); }, 350);
    }, 1200);
  }

  function refreshSel() {
    var n = listEl.querySelectorAll(".worklist-item:not(.is-done) input:checked").length;
    selN.textContent = n;
    selbar.classList.toggle("show", n > 0);
  }

  /* 이벤트 */
  listEl.addEventListener("click", function (e) {
    var cb = e.target.closest('input[type=checkbox]');
    if (cb) { refreshSel(); e.stopPropagation(); return; }
    var item = e.target.closest(".worklist-item");
    if (!item) return;
    var actBtn = e.target.closest("[data-act]");
    if (actBtn) { act(item, actBtn.dataset.act); return; }
    if (e.target.closest("[data-quick]")) { item.classList.add("is-open"); hydrate(item); e.stopPropagation(); return; }
    if (e.target.closest(".worklist-row")) {
      item.classList.toggle("is-open");
      if (item.classList.contains("is-open")) hydrate(item);
    }
  });

  $("#exc-sort").addEventListener("change", function () { sortTasks(); render(); });
  $("#exc-sel-clear").addEventListener("click", function () {
    listEl.querySelectorAll("input:checked").forEach(function (c) { c.checked = false; });
    refreshSel();
  });
  $("#exc-sel-run").addEventListener("click", function () {
    listEl.querySelectorAll(".worklist-item:not(.is-done) input:checked").forEach(function (c) {
      var item = c.closest(".worklist-item");
      item.classList.add("is-open");
      act(item, "retry");
    });
    selbar.classList.remove("show");
  });

  /* 탭 */
  document.querySelectorAll("#exc-tabs button").forEach(function (b) {
    b.addEventListener("click", function () {
      document.querySelectorAll("#exc-tabs button").forEach(function (x) { x.classList.remove("active"); });
      b.classList.add("active");
      document.querySelectorAll(".page-tab-panel").forEach(function (p) { p.classList.remove("active"); });
      document.getElementById("panel-" + b.dataset.panel).classList.add("active");
    });
  });

  load();
})();
