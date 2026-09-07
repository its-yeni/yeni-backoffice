const { chromium } = require('playwright'); const path=require('path'),fs=require('fs');
const OUT=path.resolve(__dirname,'../.review-shots/full'); fs.mkdirSync(OUT,{recursive:true});
const S=['/admin/operations-dashboard','/admin/payment-operations','/admin/payment-operations/settlements','/admin/payment-operations/accounting','/admin/commerce/inventory','/admin/commerce/products','/admin/commerce/orders','/admin/analytics','/admin/audit-logs','/admin/all-features','/admin/commerce/stores','/admin/commerce/categories','/admin/payment-operations/recovery-tasks','/admin/payment-operations/sales-ledger','/admin/payment-operations/pending-sales','/admin/payment-operations/settlements/reconciliation','/admin/commerce/suppliers','/admin/commerce/purchase-orders','/admin/commerce/shipments','/admin/commerce/returns','/admin/analytics/orders','/admin/database-spec','/admin/navigation'];
(async()=>{const b=await chromium.launch({channel:'msedge'});const c=await b.newContext({viewport:{width:1440,height:900}});const p=await c.newPage();
let bad=[];
for(const u of S){try{const r=await p.goto('http://localhost:8080'+u,{waitUntil:'domcontentloaded',timeout:20000});await p.waitForTimeout(1500);
const h=await p.evaluate(()=>({d:document.documentElement.scrollWidth,w:document.documentElement.clientWidth,err:!!document.querySelector('.whitelabel,.error-page'),nav:document.querySelectorAll('.nav a').length}));
const nm=u.replace(/\//g,'_');await p.screenshot({path:path.join(OUT,nm+'.png'),fullPage:false});
const iss=[];if(r.status()!==200)iss.push('http'+r.status());if(h.d>h.w+2)iss.push('overflow');if(h.err)iss.push('err');if(h.nav!==9)iss.push('nav'+h.nav);
process.stdout.write(iss.length?'X':'.');if(iss.length)bad.push(u+' '+iss.join(','));}catch(e){process.stdout.write('E');bad.push(u+' '+e.message.split('\n')[0]);}}
console.log('\n'+(bad.length?bad.join('\n'):'all clean'));await b.close();})();
