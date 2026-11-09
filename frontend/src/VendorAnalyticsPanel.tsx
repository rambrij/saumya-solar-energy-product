import React,{useCallback,useEffect,useMemo,useState} from 'react';
const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
type Props={token:string;onError:(message:string)=>void};
const colors=['#168252','#e5b847','#5488b9','#8d70b5','#cf8053','#4c9d9b'];
const money=(v:any)=>'₹'+Number(v||0).toLocaleString('en-IN',{maximumFractionDigits:0});
export default function VendorAnalyticsPanel({token,onError}:Props){
 const [branches,setBranches]=useState<any[]>([]),[categories,setCategories]=useState<any[]>([]),[products,setProducts]=useState<any[]>([]);
 const [branchId,setBranchId]=useState(''),[categoryId,setCategoryId]=useState(''),[productId,setProductId]=useState('');
 const [from,setFrom]=useState(()=>{const d=new Date();d.setDate(d.getDate()-90);return d.toISOString().slice(0,10)});
 const [to,setTo]=useState(()=>new Date().toISOString().slice(0,10)),[granularity,setGranularity]=useState('DAY');
 const [breakdowns,setBreakdowns]=useState<any>({categories:[],products:[]}),[trends,setTrends]=useState<any[]>([]),[rankings,setRankings]=useState<any>({topProducts:[],topCategories:[]});
 const [loading,setLoading]=useState(true),[error,setLocalError]=useState('');
 const headers={Authorization:'Bearer '+token};
 const loadLookups=useCallback(async()=>{
  const requests=await Promise.all(['/branches','/categories','/products'].map(async path=>{const r=await fetch(API_BASE+'/api/v1'+path,{headers});const body=await r.json().catch(()=>[]);if(!r.ok)throw Error(body.detail||'Unable to load analytics filters');return body||[]}));
  setBranches(requests[0]);setCategories(requests[1]);setProducts(requests[2]);
 },[token]);
 const load=useCallback(async()=>{
  setLoading(true);setLocalError('');
  try{
   const params=new URLSearchParams({from:new Date(from+'T00:00:00').toISOString(),to:new Date(to+'T23:59:59').toISOString(),granularity,metric:'revenue',limit:'10'});
   if(branchId)params.set('branchId',branchId);if(categoryId)params.set('categoryId',categoryId);if(productId)params.set('productId',productId);
   const [b,t,r]=await Promise.all(['breakdowns','trends','rankings'].map(async (kind)=>{const q=kind==='trends'?params.toString():kind==='rankings'?params.toString():params.toString();const url=API_BASE+'/api/v1/vendor/analytics/'+kind+'?'+q;const response=await fetch(url,{headers});const body=await response.json().catch(()=>({}));if(!response.ok)throw Error(body.detail||'Unable to load vendor analytics');return body}));
   setBreakdowns(b);setTrends(t.items||[]);setRankings(r);
  }catch(e:any){setLocalError(e.message);onError(e.message)}
  finally{setLoading(false)}
 },[token,from,to,branchId,categoryId,productId,granularity]);
 useEffect(()=>{void loadLookups().catch((e:any)=>{setLocalError(e.message);onError(e.message)})},[loadLookups]);
 useEffect(()=>{if(from&&to&&new Date(from)>new Date(to)){setLocalError('Start date must be before end date.');setLoading(false);return}void load()},[load]);
 const categoriesRows=breakdowns.categories||[],productsRows=breakdowns.products||[];
 const totals=useMemo(()=>trends.reduce((a:any,x:any)=>({leads:a.leads+Number(x.leads||0),enquiries:a.enquiries+Number(x.enquiries||0),orders:a.orders+Number(x.orders||0),revenue:a.revenue+Number(x.revenue||0)}),{leads:0,enquiries:0,orders:0,revenue:0}),[trends]);
 const categoryTotal=categoriesRows.reduce((a:any,x:any)=>a+Number(x.orders||0),0);
 let cursor=0;const donut='conic-gradient('+categoriesRows.slice(0,6).map((x:any,i:number)=>{const start=cursor;cursor+=categoryTotal?Number(x.orders||0)/categoryTotal*100:0;return colors[i%colors.length]+' '+start+'% '+cursor+'%'}).join(',')+')';
 const maxCategory=Math.max(1,...categoriesRows.map((x:any)=>Number(x.revenue||0))),maxProduct=Math.max(1,...productsRows.map((x:any)=>Number(x.revenue||0)));
 const maxTrend=Math.max(1,...trends.map((x:any)=>Number(x.revenue||0)));
 const points=trends.map((x:any,i:number)=>{const px=trends.length<=1?50:i/(trends.length-1)*700;const py=175-(Number(x.revenue||0)/maxTrend)*150;return px+','+py}).join(' ');
 return <section className="vendor-analytics-page">
  <div className="management-heading"><div><p className="eyebrow">BUSINESS INSIGHTS</p><h1>Analytics</h1><p className="muted">Sales, enquiries, leads and catalogue performance for your tenant.</p></div><button className="secondary" type="button" onClick={()=>void load()}>Refresh</button></div>
  <div className="card analytics-filter-panel"><h2>Filters</h2><div className="analytics-filter-grid"><label>From<input type="date" value={from} max={to} onChange={e=>setFrom(e.target.value)}/></label><label>To<input type="date" value={to} min={from} onChange={e=>setTo(e.target.value)}/></label><label>Branch<select value={branchId} onChange={e=>setBranchId(e.target.value)}><option value="">All branches</option>{branches.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></label><label>Category<select value={categoryId} onChange={e=>{setCategoryId(e.target.value);setProductId('')}}><option value="">All categories</option>{categories.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></label><label>Product<select value={productId} onChange={e=>setProductId(e.target.value)}><option value="">All products</option>{products.filter(x=>!categoryId||x.categoryId===categoryId).map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></label><label>Trend granularity<select value={granularity} onChange={e=>setGranularity(e.target.value)}><option value="DAY">Daily</option><option value="WEEK">Weekly</option><option value="MONTH">Monthly</option></select></label></div></div>
  {loading&&<div className="card" role="status">Loading analytics…</div>}
  {!loading&&error&&<div className="card" role="alert">{error}<button className="secondary" onClick={()=>void load()}>Retry</button></div>}
  {!loading&&!error&&<><div className="analytics-stat-grid">{[['Leads',totals.leads],['Enquiries',totals.enquiries],['Orders',totals.orders],['Order revenue',money(totals.revenue)],['Categories',categoriesRows.length],['Products',productsRows.length]].map(([label,value])=><article className="analytics-stat-card" key={String(label)}><span>{label}</span><strong>{value}</strong></article>)}</div>
   <div className="analytics-two-column"><article className="card analytics-chart-card"><h2>Revenue trend</h2>{trends.length?<><svg className="analytics-line-chart" viewBox="0 0 700 200" role="img" aria-label="Revenue trend over time"><line x1="0" y1="175" x2="700" y2="175" stroke="#dce8df"/><polyline points={points} fill="none" stroke="#168252" strokeWidth="4" strokeLinejoin="round" strokeLinecap="round"/>{trends.map((x:any,i:number)=><circle key={x.period} cx={trends.length<=1?50:i/(trends.length-1)*700} cy={175-(Number(x.revenue||0)/maxTrend)*150} r="4" fill="#168252"/>)}</svg><div className="analytics-period-labels"><span>{trends[0]?.period}</span><span>{trends[trends.length-1]?.period}</span></div></>:<p className="muted">No trend data for this period.</p>}</article>
   <article className="card analytics-chart-card"><h2>Category distribution</h2>{categoriesRows.length?<div className="analytics-donut-row"><div className="analytics-donut" style={{background:donut}}><span>{categoryTotal}</span></div><div className="analytics-legend">{categoriesRows.slice(0,6).map((x:any,i:number)=><span key={x.id}><i style={{background:colors[i%colors.length]}}/>{x.name}: {x.orders} orders</span>)}</div></div>:<p className="muted">No category data for this period.</p>}</article></div>
   <div className="analytics-two-column"><article className="card analytics-chart-card"><h2>Revenue by category</h2>{categoriesRows.length?<div className="analytics-horizontal-bars">{categoriesRows.slice(0,8).map((x:any)=><div className="analytics-horizontal-row" key={x.id}><span>{x.name}</span><div className="analytics-track"><i style={{width:Number(x.revenue||0)/maxCategory*100+'%'}}/></div><b>{money(x.revenue)}</b></div>)}</div>:<p className="muted">No category results.</p>}</article>
   <article className="card analytics-chart-card"><h2>Revenue by product</h2>{productsRows.length?<div className="analytics-horizontal-bars">{productsRows.slice(0,8).map((x:any)=><div className="analytics-horizontal-row" key={x.id}><span>{x.name}</span><div className="analytics-track"><i style={{width:Number(x.revenue||0)/maxProduct*100+'%'}}/></div><b>{money(x.revenue)}</b></div>)}</div>:<p className="muted">No product results.</p>}</article></div>
   <div className="analytics-two-column"><article className="card"><h2>Top products</h2><div className="table-wrap"><table><thead><tr><th>Product</th><th>Orders</th><th>Leads</th><th>Revenue</th></tr></thead><tbody>{(rankings.topProducts||[]).map((x:any)=><tr key={x.id}><td>{x.name}</td><td>{x.orders}</td><td>{x.leads}</td><td>{money(x.revenue)}</td></tr>)}</tbody></table></div>{!rankings.topProducts?.length&&<p className="muted">No ranked products.</p>}</article><article className="card"><h2>Top categories</h2><div className="table-wrap"><table><thead><tr><th>Category</th><th>Orders</th><th>Leads</th><th>Revenue</th></tr></thead><tbody>{(rankings.topCategories||[]).map((x:any)=><tr key={x.id}><td>{x.name}</td><td>{x.orders}</td><td>{x.leads}</td><td>{money(x.revenue)}</td></tr>)}</tbody></table></div>{!rankings.topCategories?.length&&<p className="muted">No ranked categories.</p>}</article></div>
  </>}
 </section>;
}
