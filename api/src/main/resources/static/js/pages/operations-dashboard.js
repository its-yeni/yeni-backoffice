document.addEventListener('DOMContentLoaded', () => {
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
  const priority = {CRITICAL:'긴급',HIGH:'높음',MEDIUM:'보통'};
  mountTopbarStatus();
  configureDashboardLinks();
  clarifyDashboardMetrics();
  $('ops-trend-range')?.addEventListener('change', event => { const url=new URL(location.href);url.searchParams.set('days',event.target.value);location.href=url; });

  setupFailureInsight($, escape);
  const order={CRITICAL:0,HIGH:1,MEDIUM:2};
  const rows=(window.opsQueueItems||[]).map(item=>{const url=new URL(item.actionUrl,location.origin);url.searchParams.set('from','dashboard');url.searchParams.set('focus','queue-item');return {...item,actionUrl:url.pathname+url.search};}).sort((a,b)=>order[a.priority]-order[b.priority]||b.count-a.count);
  let selectedPriority='',selectedDomain='';
  const renderQueue=()=>{const filtered=rows.filter(item=>(!selectedPriority||item.priority===selectedPriority)&&(!selectedDomain||item.domain===selectedDomain));$('ops-queue-rows').innerHTML=filtered.length?filtered.map(item=>`<a class="ops-queue-row" href="${escape(item.actionUrl)}"><span class="ops-priority ${item.priority.toLowerCase()}">${priority[item.priority]||item.priority}</span><span>${escape(item.domain)}</span><span class="ops-queue-item"><strong>${escape(item.title)}</strong><small>${escape(item.description)}</small></span><b>${item.count}건</b><span class="ops-next">${escape(item.actionLabel)} →</span></a>`).join(''):'<div class="ops-queue-empty">조건에 맞는 운영 예외가 없습니다.</div>';if($('ops-queue-total'))$('ops-queue-total').textContent=`총 ${filtered.length}건`;};
  document.querySelectorAll('.ops-queue-tab').forEach(button=>{const level=button.dataset.priority||'';button.querySelector('b').textContent=rows.filter(item=>!level||item.priority===level).length;button.addEventListener('click',()=>{selectedPriority=level;document.querySelectorAll('.ops-queue-tab').forEach(tab=>tab.classList.toggle('active',tab===button));renderQueue();});});
  $('ops-domain-filter')?.addEventListener('change',event=>{selectedDomain=event.target.value;renderQueue();});
  renderQueue();
  let autoTimer=setInterval(()=>location.reload(),60000);
  $('ops-auto-refresh')?.addEventListener('change',event=>{clearInterval(autoTimer);autoTimer=event.target.checked?setInterval(()=>location.reload(),60000):null;});
  const points=window.opsSalesTrend||[], chart=$('ops-sales-chart');
  if(!points.length){chart.innerHTML='<p class="ops-chart-empty">표시할 매출 데이터가 없습니다.</p>';return;}
  CanvasCharts.area(chart, points, {
    height: 150,
    ariaLabel: '최근 매출 흐름',
    value: point => Number(point.revenue) || 0,
    label: point => String(point.date).slice(5).replace('-', '.')
  });
  const yesterday=points.length>1?Number(points[points.length-2].revenue)||0:0;
  if($('ops-yesterday-revenue'))$('ops-yesterday-revenue').textContent=yesterday.toLocaleString('ko-KR')+'원';
});

function configureDashboardLinks() {
  const targets = {
    '/admin/commerce/orders': 'period=today&from=dashboard&focus=today-orders',
    '/admin/payment-operations/sales-ledger': 'period=today&from=dashboard&focus=today-sales',
    '/admin/commerce/shipments': 'from=dashboard&focus=pending-shipments',
    '/admin/commerce/returns': 'status=REQUESTED&from=dashboard&focus=requested-returns'
  };
  document.querySelectorAll('.ops-summary a').forEach(link => {
    let query = targets[link.getAttribute('href')];
    const label = link.querySelector('span')?.textContent || '';
    if (label.includes('결과 불명')) query = 'status=UNKNOWN&from=dashboard&focus=unknown-payments';
    if (label.includes('배송 지연')) query = 'status=IN_TRANSIT&stale=1&from=dashboard&focus=delayed-deliveries';
    if (label.includes('반품 검수')) query = 'status=REQUESTED&from=dashboard&focus=requested-returns';
    if (label.includes('정산 초안')) query = 'status=DRAFT&from=dashboard&focus=draft-settlements';
    if (query) link.href = `${link.getAttribute('href')}?${query}`;
  });
  const more = document.querySelector('.ops-progress header a');
  if (more) more.href = '/admin/commerce/orders?period=today&from=dashboard&focus=today-orders';
}

function clarifyDashboardMetrics() {
  const cards = [...document.querySelectorAll('.ops-summary-card:first-child .ops-metric')];
  const returns = cards[3];
  if (!returns) return;
  returns.querySelector('span').textContent = '반품 접수 대기';
  const count = returns.querySelector('small')?.textContent.match(/[\d,]+/)?.[0] || '0';
  returns.querySelector('small').textContent = `오늘 접수 ${count}건`;
  const revenue = cards[1];
  if (revenue) revenue.title = '매출 원장의 오늘 SALE·CANCEL 순매출 기준';
}

function mountTopbarStatus() {
  const actions=document.querySelector('.top-actions');
  if(!actions||actions.querySelector('.ops-top-status'))return;
  const status=document.createElement('span');status.className='ops-top-status';status.innerHTML=`<span aria-hidden="true">⟳</span> 마지막 갱신 ${new Date().toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false})}`;
  const refresh=document.createElement('button');refresh.type='button';refresh.className='btn btn-light ops-top-refresh';refresh.innerHTML='⟳&nbsp; 새로고침';refresh.addEventListener('click',()=>location.reload());
  const bell=document.createElement('button');bell.type='button';bell.className='ops-top-bell';bell.setAttribute('aria-label','알림');bell.innerHTML=`<svg aria-hidden="true" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></svg><b>${(window.opsQueueItems||[]).filter(item=>item.count>0).length}</b>`;
  actions.prepend(status,refresh,bell);
}

// 실패 로그 AI 원인 요약 — 우하단 플로팅 버튼으로 패널을 열고, 패널 안에서 명시적으로 실행한다.
// 실무처럼 대시보드 로드마다 외부 LLM을 부르지 않는다.
// 백엔드가 provider 실패 시 규칙 기반(MOCK)으로 폴백하므로, 여기서는 fetch 자체 실패만 방어한다.
function setupFailureInsight($, escape) {
  const fab = $('ops-insight-fab'), drawer = $('ops-insight-drawer'), dim = $('ops-insight-dim'),
    close = $('ops-insight-close'), btn = $('ops-insight-run-btn'),
    body = $('ops-insight-body'), conn = $('ops-insight-conn');
  if (!fab || !drawer || !dim || !close || !btn || !body) return;

  const setOpen = open => {
    drawer.classList.toggle('open', open);
    drawer.setAttribute('aria-hidden', String(!open));
    fab.setAttribute('aria-expanded', String(open));
    dim.hidden = !open;
    document.body.classList.toggle('ops-insight-open', open);
    if (open) close.focus();
  };
  fab.addEventListener('click', () => setOpen(true));
  close.addEventListener('click', () => { setOpen(false); fab.focus(); });
  dim.addEventListener('click', () => setOpen(false));
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && drawer.classList.contains('open')) { setOpen(false); fab.focus(); }
  });

  const CONN_LABEL = {
    CONNECTED: 'OpenAI 연결됨', CONFIGURED: 'OpenAI 설정됨',
    FALLBACK: 'OpenAI 실패 · 규칙 기반', MOCK: '규칙 기반 요약'
  };
  const SEV_LABEL = { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' };
  const CAT_LABEL = {
    TIMEOUT: '타임아웃', AMOUNT_MISMATCH: '금액 불일치', NETWORK_ERROR: '네트워크/망취소',
    DUPLICATE_REQUEST: '중복 요청', EXTERNAL_SYSTEM_ERROR: '외부 시스템', UNKNOWN: '원인 불명'
  };
  const fmtTime = iso => { const d = new Date(iso); return isNaN(d) ? '' : d.toLocaleString('ko-KR', { hour12: false }); };

  btn.addEventListener('click', () => {
    btn.disabled = true;
    btn.textContent = '분석 중…';
    body.hidden = false;
    body.innerHTML = '<div class="ops-insight-loading"><span class="ops-insight-spinner"></span>실패 로그를 수집해 원인을 분석하는 중…</div>';

    fetch('/admin/api/insight/failure-summary', { headers: { Accept: 'application/json' } })
      .then(res => { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
      .then(data => renderInsight(data))
      .catch(() => {
        body.innerHTML = '<div class="ops-insight-error">AI 원인 요약을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</div>';
      })
      .finally(() => {
        btn.disabled = false;
        btn.textContent = '다시 분석';
      });
  });

  function renderInsight(data) {
    const status = data.connectionStatus || 'MOCK';
    if (conn) {
      conn.textContent = CONN_LABEL[status] || status;
      conn.className = 'ops-insight-conn ' + status.toLowerCase();
      conn.hidden = false;
    }

    const items = Array.isArray(data.items) ? data.items : [];
    if (!items.length) {
      body.innerHTML = `<div class="ops-insight-empty">최근 24시간 동안 원인 분석이 필요한 PG 실패·복구 작업이 없습니다.${data.summaryHeadline ? '<br>' + escape(data.summaryHeadline) : ''}</div>`;
      return;
    }

    const itemsHtml = items.map(item => {
      const sev = String(item.severity || 'MEDIUM').toUpperCase();
      const cat = String(item.category || 'UNKNOWN').toUpperCase();
      const refs = Array.isArray(item.relatedRefKeys) ? item.relatedRefKeys : [];
      const sources = Array.isArray(item.relatedSources) ? item.relatedSources : [];
      // refKey는 RECOVERY_TASK면 taskKey(복구 화면 키워드 검색 대상), PG_API_LOG면 requestId(검색 불가).
      // 그룹이 복구 작업 출처만이면 각 칩을 복구 화면 딥링크로 만든다.
      const recoveryOnly = sources.length === 1 && sources[0] === 'RECOVERY_TASK';
      const chips = refs.map(r => recoveryOnly
        ? `<a href="/admin/payment-operations/recovery-tasks?keyword=${encodeURIComponent(r)}">${escape(r)}</a>`
        : `<span>${escape(r)}</span>`).join('');
      const link = sources.includes('RECOVERY_TASK')
        ? `<a class="ops-insight-item-link" href="/admin/payment-operations/recovery-tasks${recoveryOnly && refs.length === 1 ? '?keyword=' + encodeURIComponent(refs[0]) : ''}">복구 작업에서 처리 →</a>`
        : sources.includes('PG_API_LOG')
          ? `<a class="ops-insight-item-link" href="/admin/payment-operations?status=APPROVE_FAILED">PG 거래에서 확인 →</a>`
          : '';
      return `<div class="ops-insight-item">
        <div class="ops-insight-item-head">
          <span class="ops-insight-sev ${sev.toLowerCase()}">${SEV_LABEL[sev] || sev}</span>
          <span class="ops-insight-cat">${escape(CAT_LABEL[cat] || cat)}</span>
          <span class="ops-insight-count">${Number(item.affectedCount) || refs.length}건</span>
        </div>
        <p class="ops-insight-cause">${escape(item.cause || '')}</p>
        ${item.suggestedAction ? `<p class="ops-insight-action"><strong>권장 조치</strong> · ${escape(item.suggestedAction)}</p>` : ''}
        ${refs.length ? `<div class="ops-insight-refs">${chips}</div>` : ''}
        ${link}
      </div>`;
    }).join('');

    const scope = data.totalCandidateCount > data.sampleSize
      ? `전체 ${data.totalCandidateCount}건 중 최근 ${data.sampleSize}건 기준`
      : `${data.sampleSize}건 기준`;
    body.innerHTML = `
      ${data.summaryHeadline ? `<p class="ops-insight-headline">${escape(data.summaryHeadline)}</p>` : ''}
      <div class="ops-insight-items">${itemsHtml}</div>
      <div class="ops-insight-links">
        <a href="/admin/payment-operations/recovery-tasks">복구 작업 화면 →</a>
        <a href="/admin/payment-operations">PG 거래 화면 →</a>
      </div>
      <div class="ops-insight-foot">
        <span>${escape(data.provider || '')}${data.modelUsed ? ' · ' + escape(data.modelUsed) : ''} · ${scope}</span>
        <span>${fmtTime(data.generatedAt)}</span>
      </div>`;
  }
}
