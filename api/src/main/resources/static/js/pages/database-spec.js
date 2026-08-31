(function(){
 const keyword=document.getElementById('db-spec-keyword'),cards=Array.from(document.querySelectorAll('.db-card'));
 const pagination=AdminPagination.mount(document.getElementById('db-spec-pagination'),{total:cards.length,size:10,onChange:render});
 keyword.oninput=reset;document.getElementById('db-spec-reset').onclick=()=>{keyword.value='';reset()};
 cards.forEach(card=>{const button=card.querySelector('.db-row'),detail=card.querySelector('.db-columns');button.onclick=()=>{const open=button.getAttribute('aria-expanded')==='true';button.setAttribute('aria-expanded',String(!open));detail.hidden=open;button.querySelector('b').textContent=open?'펼치기':'접기'}});
 function reset(){pagination.setPage(1);render()}
 function render(){const query=keyword.value.trim().toLowerCase(),filtered=cards.filter(card=>(card.dataset.search||'').toLowerCase().includes(query));pagination.setTotal(filtered.length);const start=(pagination.getPage()-1)*pagination.getSize(),visible=new Set(filtered.slice(start,start+pagination.getSize()));cards.forEach(card=>card.hidden=!visible.has(card));document.getElementById('db-spec-empty').hidden=filtered.length>0}
 render();
})();
