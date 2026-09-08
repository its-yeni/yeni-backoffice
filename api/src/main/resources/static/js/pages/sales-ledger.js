(function(){
 let data=[],pagination;const $=id=>document.getElementById(id),rows=$('ledger-rows'),drawer=$('ledger-detail'),backdrop=$('ledger-backdrop');
 function today(offset){const date=new Date();date.setDate(date.getDate()+offset);return [date.getFullYear(),String(date.getMonth()+1).padStart(2,'0'),String(date.getDate()).padStart(2,'0')].join('-')}
 const initialParams=new URLSearchParams(location.search);
 $('ledger-start').value=initialParams.get('startDate')||(initialParams.get('period')==='today'?today(0):today(-7));
 $('ledger-end').value=initialParams.get('endDate')||today(0);$('ledger-keyword').value=initialParams.get('keyword')||'';
 pagination=AdminPagination.mount($('ledger-pagination'),{total:0,size:20,onChange:load});$('ledger-search').onclick=resetAndLoad;$('ledger-type').onchange=resetAndLoad;$('ledger-settlement').onchange=resetAndLoad;$('ledger-detail-close').onclick=close;backdrop.onclick=close;
 if(initialParams.get('transactionType'))$('ledger-type').value=initialParams.get('transactionType');
 if(initialParams.get('settlementStatus'))$('ledger-settlement').value=initialParams.get('settlementStatus');
 const confirmSel=$('ledger-confirmed');if(confirmSel){if(initialParams.get('confirmedYn'))confirmSel.value=initialParams.get('confirmedYn');confirmSel.onchange=resetAndLoad;}
 const resetBtn=$('ledger-reset');if(resetBtn)resetBtn.onclick=()=>{$('ledger-keyword').value='';$('ledger-start').value=today(-7);$('ledger-end').value=today(0);$('ledger-type').value='';$('ledger-settlement').value='';if(confirmSel)confirmSel.value='';resetAndLoad();};
 const settlementLabel=value=>({NOT_SETTLED:'정산 대기',SETTLEMENT_READY:'정산 준비',CALCULATED:'계산 완료',SETTLED:'정산 확정',PAID:'지급 완료',CARRIED_OVER:'다음 정산 차감',EXCLUDED:'정산 제외'})[value]||value;

 // 운영자가 한 행의 현재 위치를 바로 알 수 있게: 구매 확정 여부 → 정산 진행 단계 순으로 판단한다.
 function stateOf(item){
   if(item.saleType==='CANCEL')return{label:'취소 매출',tone:'is-danger',hint:'원 승인 매출과 상계되어 정산에서 차감됩니다.'};
   if(!item.confirmedYn)return{label:'구매 확정 대기',tone:'is-warning',hint:'배송 완료(구매 확정) 전이라 아직 정산 대상이 아닙니다. 미확정 매출 화면에서 확인/처리합니다.'};
   switch(item.settlementStatus){
     case'NOT_SETTLED':return{label:'정산 대기',tone:'',hint:'구매 확정됨. 다음 정산 배치에서 집계됩니다.'};
     case'CALCULATED':return{label:'정산 계산됨',tone:'',hint:'정산 초안(DRAFT)에 포함되어 수수료·VAT가 계산되었습니다.'};
     case'SETTLED':return{label:'정산 확정',tone:'is-success',hint:'정산서가 확정(CONFIRMED)되었습니다.'};
     case'PAID':return{label:'지급 완료',tone:'is-success',hint:'정산금 지급이 완료되었습니다.'};
     case'CARRIED_OVER':return{label:'다음 정산 차감',tone:'is-warning',hint:'정산 확정 후 취소되어 다음 정산에서 차감됩니다.'};
     case'EXCLUDED':return{label:'정산 제외',tone:'',hint:'정산 대상에서 제외되었습니다.'};
     default:return{label:settlementLabel(item.settlementStatus),tone:'',hint:''};
   }
 }
 renderLegend();
 function renderLegend(){
   const host=$('ledger-legend');if(!host)return;
   const items=[
     ['is-warning','구매 확정 대기','배송 완료 전 — 정산 대상 아님'],
     ['','정산 대기','확정됨 — 다음 배치 집계 예정'],
     ['','정산 계산됨','정산 초안에 포함, 수수료 계산됨'],
     ['is-success','정산 확정','정산서 확정'],
     ['is-success','지급 완료','정산금 지급 완료'],
     ['is-danger','취소 매출','원 매출과 상계'],
   ];
   host.innerHTML=items.map(([t,l,d])=>`<span class="ledger-legend-item"><span class="status-indicator ${t}">${l}</span><small>${d}</small></span>`).join('');
 }

 function resetAndLoad(){pagination.setPage(1);load()}
 async function load(){
   const params=new URLSearchParams({startDate:$('ledger-start').value,endDate:$('ledger-end').value,page:String(pagination.getPage()-1),size:String(pagination.getSize())});
   const type=$('ledger-type').value,settlement=$('ledger-settlement').value,keyword=$('ledger-keyword').value.trim();
   if(type)params.set('transactionType',type);if(settlement)params.set('settlementStatus',settlement);if(keyword)params.set('keyword',keyword);
   if(confirmSel&&confirmSel.value)params.set('confirmedYn',confirmSel.value);
   const page=await apiGet('/admin/api/sales-ledger?'+params);data=page.data||[];pagination.setTotal(page.totalCount||0);render(page.summary||{});
   const requested=Number(new URLSearchParams(location.search).get('ledgerId'));if(requested&&data.some(item=>item.id===requested))open(requested);
 }
 function render(summary){
   const rowStart=(pagination.getPage()-1)*pagination.getSize();
   rows.innerHTML=data.map((item,i)=>{const st=stateOf(item);return `<tr data-ledger="${item.id}">
     <td class="row-index">${rowStart+i+1}</td>
     <td>${formatDate(item.occurredAt)}</td>
     <td><strong>${escapeHtml(item.orderNo)}</strong></td>
     <td>${escapeHtml(operationalStoreLabel(item.storeId))}</td>
     <td><span class="status-indicator ${item.saleType==='SALE'?'is-success':'is-danger'}">${item.saleType==='SALE'?'승인':'취소'}</span></td>
     <td>${item.paymentId?'#'+item.paymentId:'-'}</td>
     <td class="mono">${escapeHtml(item.pgTransactionId||item.tid||'-')}</td>
     <td class="amount">${money(item.supplyAmount)}</td>
     <td class="amount">${money(item.vatAmount)}</td>
     <td class="amount"><strong>${item.saleType==='CANCEL'?'-':''}${money(Math.abs(Number(item.totalAmount)))}</strong></td>
     <td><span class="status-indicator ${st.tone}" title="${escapeHtml(st.hint)}">${st.label}</span></td></tr>`}).join('');
   rows.querySelectorAll('[data-ledger]').forEach(row=>row.onclick=()=>open(Number(row.dataset.ledger)));
   $('ledger-empty').hidden=data.length>0;
   $('ledger-sale').textContent=money(summary.totalSaleAmount);$('ledger-cancel').textContent=money(summary.totalCancelAmount);$('ledger-net').textContent=money(summary.netSalesAmount);
   $('ledger-waiting').textContent=Number(summary.notSettledCount||0).toLocaleString('ko-KR')+'건';
   if($('ledger-unconfirmed'))$('ledger-unconfirmed').textContent=Number(summary.unconfirmedCount||0).toLocaleString('ko-KR')+'건';
 }
 async function open(id){
   const[detail,links]=await Promise.all([apiGet(`/admin/api/sales-ledger/${id}`),apiGet(`/admin/api/sales-ledger/${id}/links`)]);
   const st=stateOf(detail);
   $('ledger-detail-title').textContent=`원장 #${detail.id}`;
   $('ledger-detail-body').innerHTML=`<section class="drawer-summary"><span class="transaction-status ${detail.saleType==='SALE'?'success':'danger'}">${detail.saleType==='SALE'?'승인':'취소'}</span><strong>${money(detail.totalAmount)}</strong><p>${escapeHtml(detail.orderNo)} · ${formatDate(detail.occurredAt)}</p></section>
   <section><h3>현재 상태</h3><p><span class="status-indicator ${st.tone}">${st.label}</span></p><p class="inline-message">${escapeHtml(st.hint)}</p></section>
   <section><h3>금액 구성</h3><dl class="drawer-meta"><div><dt>공급가</dt><dd>${money(detail.supplyAmount)}</dd></div><div><dt>부가세</dt><dd>${money(detail.vatAmount)}</dd></div><div><dt>구매 확정</dt><dd>${detail.confirmedYn?'확정 ('+formatDate(detail.confirmedAt)+')':'미확정'}</dd></div><div><dt>정산 포함</dt><dd>${detail.settlementIncludedYn?'포함됨':'미포함'}</dd></div></dl></section>
   <section><h3>거래 연결</h3><dl class="drawer-meta"><div><dt>결제 ID</dt><dd>${detail.paymentId?'#'+detail.paymentId:'-'}</dd></div><div><dt>원 승인 거래</dt><dd>${links.originalSale?'#'+links.originalSale.id:'-'}</dd></div><div><dt>누적 취소</dt><dd>${money(links.cumulativeCanceledAmount)}</dd></div><div><dt>취소 가능</dt><dd>${money(links.cancelableAmount)}</dd></div><div><dt>정산 상세</dt><dd>${links.settlementDetails.length}건</dd></div></dl></section>
   <div class="drawer-actions">${detail.paymentId?`<a class="btn btn-light" href="/admin/payment-operations?paymentId=${detail.paymentId}">PG 거래 보기</a>`:''}${!detail.confirmedYn?`<a class="btn btn-light" href="/admin/payment-operations/pending-sales?keyword=${encodeURIComponent(detail.orderNo)}">미확정 매출에서 처리</a>`:''}${links.settlementDetails.length?`<a class="btn btn-light" href="/admin/payment-operations/settlements?statementId=${links.settlementDetails[0].settlementStatementId}">정산 보기</a>`:'<a class="btn btn-light" href="/admin/payment-operations/settlements">정산 관리</a>'}</div>`;
   backdrop.hidden=false;drawer.classList.add('open');
 }
 function close(){drawer.classList.remove('open');backdrop.hidden=true}
 function formatDate(value){return value?new Date(value).toLocaleString('ko-KR',{year:'2-digit',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):'-'}
 load();
})();
