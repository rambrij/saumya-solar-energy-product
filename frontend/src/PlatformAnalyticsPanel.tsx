import React,{useCallback,useEffect,useState} from 'react';
import {authenticatedFetch,type AuthSession} from './authenticatedFetch';
const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
type Session={accessToken:string;user:{permissions:string[]}};
export default function PlatformAnalyticsPanel({s}:{s:Session}){
 const [data,setData]=useState<any>(null),[overview,setOverview]=useState<any>(null),[page,setPage]=useState(0),[loading,setLoading]=useState(true),[error,setError]=useState('');
 const [from,setFrom]=useState(()=>new Date(Date.now()-30*86400000).toISOString().slice(0,10));
 const [to,setTo]=useState(()=>new Date().toISOString().slice(0,10));
 const load=useCallback(async(nextPage=page)=>{
  setLoading(true);setError('');
  try{
   const fromIso=new Date(from+'T00:00:00.000Z').toISOString(),toIso=new Date(to+'T23:59:59.999Z').toISOString();
   const [usageResponse,overviewResponse]=await Promise.all([
    authenticatedFetch(API_BASE+'/api/v1/platform/analytics/tenant-usage?page='+nextPage+'&size=100',{},s as AuthSession),
    authenticatedFetch(API_BASE+'/api/v1/platform/analytics/overview?from='+encodeURIComponent(fromIso)+'&to='+encodeURIComponent(toIso),{},s as AuthSession)
   ]);
   const [body,overviewBody]=await Promise.all([usageResponse.json().catch(()=>({})),overviewResponse.json().catch(()=>({}))]);
   if(!usageResponse.ok)throw new Error(body.detail||body.message||'Unable to load platform usage ('+usageResponse.status+')');
   if(!overviewResponse.ok)throw new Error(overviewBody.detail||overviewBody.message||'Unable to load platform business analytics ('+overviewResponse.status+')');
   setData(body);setOverview(overviewBody);setPage(Number(body.page??nextPage));
  }catch(e:any){setError(e.message||'Unable to load platform analytics');}
  finally{setLoading(false);}
 },[s.accessToken,page,from,to]);
 useEffect(()=>{void load(0)},[s.accessToken,from,to]);
 const rows=data?.items||[];
 const sum=(key:string)=>rows.reduce((total:number,row:any)=>total+Number(row[key]?.used||0),0);
 return <section className="platform-shell platform-analytics-panel">
  <div className="platform-heading"><div><p className="eyebrow">PLATFORM INSIGHTS</p><h1>Analytics</h1><p className="muted">Aggregate platform performance and tenant resource usage.</p></div><button type="button" className="secondary" onClick={()=>void load(page)} disabled={loading}>Refresh</button></div>
  <div className="card platform-filters"><label>From<input aria-label="Analytics from date" type="date" value={from} max={to} onChange={e=>setFrom(e.target.value)}/></label><label>To<input aria-label="Analytics to date" type="date" value={to} min={from} onChange={e=>setTo(e.target.value)}/></label></div>
  {loading?<div className="card" role="status" aria-live="polite">Loading platform analytics…</div>:error?<div className="card" role="alert"><h2>Analytics could not be loaded</h2><p>{error}</p><button type="button" onClick={()=>void load(page)}>Retry</button></div>:<>
   <div className="platform-stats"><article className="card"><small>Total tenants</small><strong>{overview?.tenants?.totalTenants??data?.total??0}</strong></article><article className="card"><small>Active tenants</small><strong>{overview?.tenants?.activeTenants??0}</strong></article><article className="card"><small>Orders in period</small><strong>{overview?.orders?.orderCount??0}</strong></article><article className="card"><small>Order value in period</small><strong>₹{Number(overview?.orders?.orderValue??0).toLocaleString('en-IN',{maximumFractionDigits:2})}</strong></article></div>
   <div className="platform-stats"><article className="card"><small>Active subscriptions</small><strong>{overview?.subscriptions?.activeSubscriptions??0}</strong></article><article className="card"><small>Trial subscriptions</small><strong>{overview?.subscriptions?.trialSubscriptions??0}</strong></article><article className="card"><small>Past due subscriptions</small><strong>{overview?.subscriptions?.pastDueSubscriptions??0}</strong></article><article className="card"><small>Ordering tenants</small><strong>{overview?.orders?.orderingTenants??0}</strong></article></div>
   <div className="card"><div className="platform-heading"><div><h2>Tenant resource usage</h2><p className="muted">Usage compared with each tenant’s assigned subscription limits.</p></div></div>
    {!rows.length?<div className="empty-state"><h3>No tenant usage data</h3><p>Tenant usage will appear here after vendors are onboarded.</p></div>:<div className="table-wrap"><table><thead><tr><th>Tenant</th><th>Status</th><th>Plan</th><th>Subscription</th><th>Users</th><th>Products</th><th>Branches</th><th>Warehouses</th><th>Storage MB</th></tr></thead><tbody>{rows.map((row:any)=><tr key={row.tenantId}><td><b>{row.tenantName}</b><small>{row.tenantSlug}</small></td><td>{row.tenantStatus}</td><td>{row.planName}</td><td>{row.subscriptionStatus}</td>{['users','products','branches','warehouses','storageMb'].map(key=><td key={key}>{row[key]?.used??0} / {row[key]?.limit??'—'}</td>)}</tr>)}</tbody></table></div>}
    <div className="platform-pagination"><span>{data?.total??0} tenants · Page {Number(data?.totalPages||0)?page+1:0} of {data?.totalPages||0}</span><div className="actions"><button type="button" className="secondary" disabled={loading||page<=0} onClick={()=>void load(page-1)}>Previous</button><button type="button" className="secondary" disabled={loading||page+1>=Number(data?.totalPages||0)} onClick={()=>void load(page+1)}>Next</button></div></div>
   </div>
  </>}
 </section>;
}
