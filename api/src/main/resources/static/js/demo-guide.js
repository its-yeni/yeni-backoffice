(function(){
  const KEY='yeni-demo-flow';
  const steps=[
    {key:'purchase',label:'상품·결제',match:p=>p==='/admin/commerce/preview'},
    {key:'order',label:'주문 확인',match:p=>p==='/admin/commerce/orders'},
    {key:'payment',label:'PG 거래',match:p=>p==='/admin/payment-operations'},
    {key:'ledger',label:'매출 원장',match:p=>p.includes('/sales-ledger')},
    {key:'recon',label:'PG 대사',match:p=>p.includes('reconciliation')||p.includes('pg-reconciliation')},
    {key:'settlement',label:'정산 검증',match:p=>p.endsWith('/settlements')}
  ];
  function read(){try{return JSON.parse(sessionStorage.getItem(KEY)||'null')}catch(e){return null}}
  function href(key,data){const q=v=>encodeURIComponent(v||'');return {purchase:'/admin/commerce/preview?demo=1',order:`/admin/commerce/orders?demo=1&keyword=${q(data?.orderNo)}`,payment:`/admin/payment-operations?demo=1${data?.paymentId?'&paymentId='+q(data.paymentId):''}`,ledger:`/admin/payment-operations/sales-ledger?demo=1&keyword=${q(data?.orderNo)}`,recon:`/admin/payment-operations/settlements/reconciliation?demo=1&businessDate=${q(data?.businessDate)}`,settlement:'/admin/payment-operations/settlements?demo=1'}[key]}
  function render(){
    const requested=new URLSearchParams(location.search).get('demo')==='1';let data=read();
    if(requested&&!data){data={startedAt:Date.now()};sessionStorage.setItem(KEY,JSON.stringify(data))}
    document.getElementById('demo-guide')?.remove();if(!data)return;
    const found=steps.findIndex(s=>s.match(location.pathname)),current=Math.max(0,found),completed=data.orderNo?Math.max(1,current):0;
    const bar=document.createElement('section');bar.id='demo-guide';bar.className='demo-guide';
    bar.innerHTML=`<div class="demo-guide-head"><div><small>3 MINUTE OPERATIONS DEMO</small><strong>${data.orderNo?'생성된 거래를 운영 데이터에서 추적합니다.':'판매 상품을 선택하고 Mock 결제를 실행하세요.'}</strong></div><div><button type="button" data-demo-reset>처음부터</button><button type="button" data-demo-close aria-label="가이드 닫기">×</button></div></div><ol>${steps.map((s,i)=>`<li class="${i===current?'current':i<completed?'done':''}"><a href="${href(s.key,data)}"><b>${i<completed?'✓':i+1}</b><span>${s.label}</span></a></li>`).join('')}</ol>${data.orderNo?`<footer><span>주문 <b>${data.orderNo}</b></span><span>결제 <b>${data.paymentId?'PAY-'+data.paymentId:data.paymentStatus||'-'}</b></span><span>금액 <b>${Number(data.amount||0).toLocaleString('ko-KR')}원</b></span><a href="${href(steps[Math.min(current+1,steps.length-1)].key,data)}">${current===steps.length-1?'정산 화면에서 확인':'다음 단계'} →</a></footer>`:''}`;
    document.body.prepend(bar);document.body.classList.add('demo-guide-active');
    bar.querySelector('[data-demo-close]').onclick=()=>{sessionStorage.removeItem(KEY);bar.remove();document.body.classList.remove('demo-guide-active')};
    bar.querySelector('[data-demo-reset]').onclick=()=>{sessionStorage.setItem(KEY,JSON.stringify({startedAt:Date.now()}));location.href='/admin/commerce/preview?demo=1'};
  }
  document.addEventListener('DOMContentLoaded',render);window.addEventListener('yeni-demo-updated',render);
})();
