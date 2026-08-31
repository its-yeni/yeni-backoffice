document.addEventListener('DOMContentLoaded', () => {
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
  const priority = {CRITICAL:'긴급',HIGH:'높음',MEDIUM:'보통'};
  $('ops-refresh-btn')?.addEventListener('click', () => location.reload());
  $('ops-trend-range')?.addEventListener('change', event => { const url=new URL(location.href);url.searchParams.set('days',event.target.value);location.href=url; });
  const order={CRITICAL:0,HIGH:1,MEDIUM:2};
  const rows=(window.opsQueueItems||[]).slice().sort((a,b)=>order[a.priority]-order[b.priority]||b.count-a.count);
  $('ops-queue-rows').innerHTML=rows.length?rows.map(item=>`<a class="ops-queue-row" href="${escape(item.actionUrl)}"><span class="ops-priority ${item.priority.toLowerCase()}">${priority[item.priority]||item.priority}</span><span>${escape(item.domain)}</span><span class="ops-queue-item"><strong>${escape(item.title)}</strong><small>${escape(item.description)}</small></span><b>${item.count}건</b><span class="ops-next">${escape(item.actionLabel)} →</span></a>`).join(''):'<div class="ops-queue-empty">지금 확인할 운영 예외가 없습니다.</div>';
  const points=window.opsSalesTrend||[], chart=$('ops-sales-chart');
  if(!points.length){chart.innerHTML='<p class="ops-chart-empty">표시할 매출 데이터가 없습니다.</p>';return;}
  CanvasCharts.area(chart, points, {
    height: 150,
    ariaLabel: '최근 매출 흐름',
    value: point => Number(point.revenue) || 0,
    label: point => String(point.date).slice(5).replace('-', '.')
  });
});
