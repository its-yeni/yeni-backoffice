document.addEventListener("DOMContentLoaded", () => {
  const $ = id => document.getElementById(id);
  const money = v => (v == null ? "-" : AppFormat.money(v));

  const STATE_LABEL = {
    MATCHED: "일치", INTERNAL_ONLY: "내부만 존재", PG_ONLY: "PG만 존재",
    AMOUNT_MISMATCH: "금액 상이", FEE_MISMATCH: "수수료 상이"
  };
  const RESOLUTION_LABEL = { NOT_REQUIRED: "-", OPEN: "미처리", IN_REVIEW: "확인 중", RESOLVED: "해결", EXCLUDED: "정산 제외" };
  // 금액 상이 계열은 하나로 묶어서 필터/집계한다.
  const AMOUNT_GROUP = ["AMOUNT_MISMATCH", "FEE_MISMATCH"];
  const REASON_PRESETS = [
    "PG 수수료 차이 — 계약 요율과 대조 후 내부 원장 보정",
    "부분취소 미반영 — 취소 전표를 원장에 반영",
    "PG 승인 누락 — 승인 결과 재조회 후 매출 확정",
    "내부 유실 거래 — 결제 로그 복구 후 매출 생성",
    "중복 전송 — PG 자료 중복분, 정산 대상에서 제외"
  ];

  let batches = [], current = null, filter = "ACTION", selectedRow = null;

  $("pgr-date").value = new URLSearchParams(location.search).get("businessDate") || new Date().toISOString().slice(0, 10);

  /* ---------- 파일 수신 패널 ---------- */
  $("pgr-import-toggle").onclick = () => setImportOpen($("pgr-import").hidden);
  function setImportOpen(open) {
    $("pgr-import").hidden = !open;
    $("pgr-import-toggle").classList.toggle("is-active", open);
    $("pgr-import-toggle").textContent = open ? "가져오기 닫기" : "PG 파일 가져오기";
  }

  document.querySelectorAll("[data-sample]").forEach(button => button.onclick = async () => {
    const businessDate = $("pgr-date").value;
    if (!businessDate) return AppToast.error("영업일을 먼저 선택해 주세요.");
    button.disabled = true;
    try {
      const res = await fetch(`/admin/api/pg-reconciliation/sample?businessDate=${encodeURIComponent(businessDate)}&scenario=${button.dataset.sample}`);
      if (!res.ok) throw new Error(await res.text());
      const blob = await res.blob(), link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `pg-settlement-${businessDate}-${button.dataset.sample.toLowerCase()}.csv`;
      document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(link.href);
      AppToast.success("샘플 CSV를 내려받았습니다. 같은 영업일로 업로드하면 대사 결과가 나옵니다.");
    } catch (e) { AppToast.error(e.message || "샘플 CSV를 만들지 못했습니다."); }
    finally { button.disabled = false; }
  });

  $("pgr-upload").onsubmit = async event => {
    event.preventDefault();
    const button = event.submitter, file = $("pgr-file").files[0];
    if (!file) return AppToast.error("정산 CSV 파일을 선택해 주세요.");
    const form = new FormData(); form.append("file", file);
    const query = new URLSearchParams({ pgCompany: $("pgr-pg").value.trim(), mid: $("pgr-mid").value.trim(), businessDate: $("pgr-date").value });
    button.disabled = true;
    try {
      const res = await fetch("/admin/api/pg-reconciliation/imports?" + query, { method: "POST", body: form });
      if (!res.ok) throw new Error(await res.text());
      const imported = await res.json();
      AppToast.success(`대사 완료 — 일치 ${imported.matchedCount}건 · 확인 필요 ${imported.mismatchCount}건`);
      $("pgr-file").value = "";
      setImportOpen(false);
      await load(imported.id);
    } catch (e) { AppToast.error(e.message || "PG 파일을 가져오지 못했습니다."); }
    finally { button.disabled = false; }
  };

  /* ---------- 배치 목록 ---------- */
  async function load(preferredId) {
    batches = await apiGet("/admin/api/pg-reconciliation") || [];
    current = batches.find(b => b.id === preferredId) || batches[0] || null;
    if (!batches.length) setImportOpen(true);
    renderBatches();
    renderResult();
  }

  function batchRate(batch) { return batch.totalCount ? Math.round(batch.matchedCount / batch.totalCount * 100) : 0; }

  function renderBatches() {
    $("pgr-batch-count").textContent = batches.length + "건";
    $("pgr-batches-empty").hidden = batches.length > 0;
    $("pgr-batch-list").innerHTML = batches.map(batch => {
      const done = !batch.unresolvedCount;
      return `<button type="button" data-batch="${batch.id}" class="${current && current.id === batch.id ? "is-on" : ""}">
        <span class="pgr-batch-top">
          <strong>${batch.businessDate}</strong>
          <em class="pgr-tag ${done ? "ok" : "review"}">${done ? "대사 완료" : "미처리 " + batch.unresolvedCount}</em>
        </span>
        <b class="pgr-batch-file">${escapeHtml(batch.fileName)}</b>
        <small>${escapeHtml(batch.pgCompany)} · ${escapeHtml(batch.mid)} · ${batch.totalCount}건 · 일치 ${batchRate(batch)}%</small>
      </button>`;
    }).join("");
    $("pgr-batch-list").querySelectorAll("[data-batch]").forEach(button => button.onclick = () => {
      current = batches.find(b => b.id === Number(button.dataset.batch));
      filter = "ACTION";
      renderBatches(); renderResult();
    });
  }

  /* ---------- 대사 결과 ---------- */
  const rows = () => (current && current.rows) || [];
  const isAmount = row => AMOUNT_GROUP.includes(row.status);
  const isAction = row => row.status !== "MATCHED" && ["OPEN", "IN_REVIEW"].includes(row.resolutionStatus);
  const countBy = predicate => rows().filter(predicate).length;

  function renderResult() {
    $("pgr-result").hidden = !current;
    if (!current) return;

    const total = rows().length;
    const matched = countBy(r => r.status === "MATCHED");
    const exceptions = total - matched;
    const actionCount = countBy(isAction);
    const rate = total ? Math.round(matched / total * 100) : 0;
    const diffSum = rows()
      .filter(r => isAmount(r) && r.internalAmount != null && r.pgAmount != null)
      .reduce((sum, r) => sum + Math.abs(Number(r.internalAmount) - Number(r.pgAmount)), 0);

    $("pgr-status-file").textContent = current.fileName;
    $("pgr-status-meta").textContent = `${current.businessDate} · ${current.pgCompany} · ${current.mid} · 수신 ${new Date(current.createdAt).toLocaleString("ko-KR")}`;

    const flag = $("pgr-status-flag");
    if (!actionCount) { flag.textContent = "이 영업일 대사 완료 — 정산 진행 가능"; flag.className = "pgr-status-flag ok"; }
    else { flag.textContent = `미처리 ${actionCount}건 — 정산 전 확인 필요`; flag.className = "pgr-status-flag review"; }

    $("pgr-m-unresolved").textContent = actionCount + "건";
    $("pgr-m-unresolved-note").textContent = actionCount ? "확인이 필요한 차이" : "모두 처리됨";
    $("pgr-m-unresolved").closest(".pgr-metric").classList.toggle("is-clear", !actionCount);
    $("pgr-m-rate").textContent = rate + "%";
    $("pgr-rate-fill").style.width = rate + "%";
    $("pgr-rate-fill").className = rate >= 100 ? "is-full" : rate >= 90 ? "is-high" : "is-low";
    $("pgr-m-diff").textContent = money(diffSum);
    $("pgr-m-total").textContent = total + "건";
    $("pgr-m-total-note").textContent = `일치 ${matched} · 예외 ${exceptions}`;

    $("pgr-f-action").textContent = actionCount;
    $("pgr-f-all").textContent = total;
    $("pgr-f-matched").textContent = matched;
    $("pgr-f-internal").textContent = countBy(r => r.status === "INTERNAL_ONLY");
    $("pgr-f-pg").textContent = countBy(r => r.status === "PG_ONLY");
    $("pgr-f-amount").textContent = countBy(isAmount);

    renderRows();
  }

  $("pgr-filter").querySelectorAll("[data-filter]").forEach(button => button.onclick = () => {
    filter = button.dataset.filter;
    $("pgr-filter").querySelectorAll("[data-filter]").forEach(b => b.classList.toggle("is-on", b === button));
    renderRows();
  });

  function visibleRows() {
    return rows().filter(row => {
      if (filter === "ACTION") return isAction(row);
      if (filter === "AMOUNT_MISMATCH") return isAmount(row);
      if (!filter) return true;
      return row.status === filter;
    });
  }

  function renderRows() {
    const list = visibleRows();
    $("pgr-empty").hidden = list.length > 0;
    $("pgr-rows").innerHTML = list.map(row => {
      const diff = row.internalAmount == null || row.pgAmount == null ? null : Number(row.internalAmount) - Number(row.pgAmount);
      const matched = row.status === "MATCHED";
      const resolution = row.resolutionStatus || "OPEN";
      return `<tr class="${matched ? "is-matched" : ""}${isAction(row) ? " is-action" : ""}" data-row="${row.id}">
        <td><span class="pgr-state ${row.status.toLowerCase()}">${STATE_LABEL[row.status] || row.status}</span></td>
        <td class="pgr-tid">${copyableValue(row.tid, "-")}</td>
        <td>${escapeHtml(row.orderNo || "-")}</td>
        <td class="number">${money(row.internalAmount)}</td>
        <td class="number">${money(row.pgAmount)}</td>
        <td class="number ${diff ? "pgr-diff" : ""}">${diff == null ? "-" : (diff > 0 ? "+" : "") + money(diff)}</td>
        <td><span class="pgr-res ${resolution.toLowerCase()}">${RESOLUTION_LABEL[resolution] || "미처리"}</span></td>
        <td>${escapeHtml(row.assignee || "-")}</td>
        <td class="pgr-row-action">${matched ? "" : `<button type="button" class="btn btn-text" data-open="${row.id}">${isAction(row) ? "조치" : "상세"}</button>`}</td>
      </tr>`;
    }).join("");
    $("pgr-rows").querySelectorAll("[data-open]").forEach(button => button.onclick = e => { e.stopPropagation(); openDrawer(Number(button.dataset.open)); });
    $("pgr-rows").querySelectorAll("tr[data-row]").forEach(tr => tr.onclick = () => {
      if (tr.classList.contains("is-matched")) return;
      openDrawer(Number(tr.dataset.row));
    });
  }

  /* ---------- 예외 처리 드로어 ---------- */
  function openDrawer(id) {
    selectedRow = rows().find(r => r.id === id);
    if (!selectedRow) return;
    const row = selectedRow;
    const diff = row.internalAmount == null || row.pgAmount == null ? null : Number(row.internalAmount) - Number(row.pgAmount);

    $("pgr-drawer-title").textContent = STATE_LABEL[row.status] || "불일치 상세";
    $("pgr-drawer-body").innerHTML = `
      <div class="pgr-compare">
        <div><span>내부 원장</span><strong>${money(row.internalAmount)}</strong></div>
        <div class="pgr-compare-op">${diff == null ? "" : "−"}</div>
        <div><span>PG 수신</span><strong>${money(row.pgAmount)}</strong></div>
        <div class="pgr-compare-eq ${diff ? "off" : "on"}">
          <span>차액</span><strong>${diff == null ? "-" : (diff > 0 ? "+" : "") + money(diff)}</strong>
        </div>
      </div>
      <dl class="pgr-facts">
        <div><dt>TID</dt><dd>${copyableValue(row.tid, "-")}</dd></div>
        <div><dt>주문번호</dt><dd>${escapeHtml(row.orderNo || "-")}</dd></div>
        <div><dt>PG 수수료</dt><dd>${money(row.pgFee)}</dd></div>
        <div><dt>확인 사항</dt><dd>${escapeHtml(row.reason || "-")}</dd></div>
      </dl>
      <section class="pgr-form">
        <h3>차이 원인</h3>
        <div class="pgr-reason-presets" id="pgr-reason-presets">
          ${REASON_PRESETS.map(text => `<button type="button" data-reason="${escapeHtml(text)}">${escapeHtml(text.split(" — ")[0])}</button>`).join("")}
        </div>
        <label>처리 상태
          <select id="pgr-res-status">
            <option value="IN_REVIEW">확인 중</option>
            <option value="RESOLVED">해결 — 정산 대상 유지</option>
            <option value="EXCLUDED">정산 대상 제외</option>
          </select>
        </label>
        <label>담당자<input id="pgr-res-assignee" value="${escapeHtml(row.assignee || '권예은')}"></label>
        <label class="pgr-form-wide">판단 근거
          <textarea id="pgr-res-note" rows="4" placeholder="대조한 자료와 처리 근거를 구체적으로 적습니다.">${escapeHtml(row.resolutionNote || "")}</textarea>
        </label>
      </section>
      <section class="pgr-log">
        <h3>처리 이력</h3>
        ${(row.actions || []).map(action => `<div>
          <time>${new Date(action.loggedAt).toLocaleString("ko-KR")}</time>
          <strong>${RESOLUTION_LABEL[(action.actionType || "").replace("RESOLUTION_", "")] || action.actionType}</strong>
          <span>${escapeHtml(action.actor || "-")}</span>
          <p>${escapeHtml(action.description || "-")}</p>
        </div>`).join("") || '<p class="pgr-log-empty">아직 처리 이력이 없습니다.</p>'}
      </section>`;

    if (["IN_REVIEW", "RESOLVED", "EXCLUDED"].includes(row.resolutionStatus)) $("pgr-res-status").value = row.resolutionStatus;
    $("pgr-reason-presets").querySelectorAll("[data-reason]").forEach(button => button.onclick = () => {
      const note = $("pgr-res-note");
      note.value = note.value.trim() ? note.value.trim() + "\n" + button.dataset.reason : button.dataset.reason;
      if (button.dataset.reason.includes("제외")) $("pgr-res-status").value = "EXCLUDED";
    });

    $("pgr-drawer-backdrop").hidden = false;
    $("pgr-drawer").classList.add("open");
    $("pgr-drawer").setAttribute("aria-hidden", "false");
  }

  function closeDrawer() {
    $("pgr-drawer").classList.remove("open");
    $("pgr-drawer").setAttribute("aria-hidden", "true");
    $("pgr-drawer-backdrop").hidden = true;
    selectedRow = null;
  }
  $("pgr-drawer-close").onclick = closeDrawer;
  $("pgr-drawer-backdrop").onclick = closeDrawer;
  document.addEventListener("keydown", e => { if (e.key === "Escape") closeDrawer(); });

  $("pgr-drawer-save").onclick = async () => {
    if (!selectedRow) return;
    const rowId = selectedRow.id;
    const payload = { status: $("pgr-res-status").value, assignee: $("pgr-res-assignee").value.trim(), note: $("pgr-res-note").value.trim() };
    if (!payload.assignee || !payload.note) return AppToast.error("담당자와 판단 근거를 입력해 주세요.");
    await apiPost(`/admin/api/pg-reconciliation/rows/${rowId}/resolution`, payload, "PATCH");
    AppToast.success("대사 조치를 저장했습니다.");
    await load(current.id);
    const updated = rows().find(r => r.id === rowId);
    if (updated && ["OPEN", "IN_REVIEW"].includes(updated.resolutionStatus)) openDrawer(updated.id);
    else closeDrawer();
  };

  $("pgr-drawer-next").onclick = () => {
    const open = rows().filter(isAction);
    const index = open.findIndex(r => r.id === (selectedRow && selectedRow.id));
    const next = open[index + 1] || open[0];
    if (next) openDrawer(next.id);
    else { AppToast.show("남은 미처리 건이 없습니다."); closeDrawer(); }
  };

  load();
});
