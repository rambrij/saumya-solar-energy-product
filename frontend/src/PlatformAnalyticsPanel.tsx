import React,{useCallback,useEffect,useState} from 'react';
const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
type Session={accessToken:string;user:{permissions:string[]}};
export default function PlatformAnalyticsPanel({s}:{s:Session}){
 const [data,setData]=useState<any>(null),[page,setPage]=useState(0),[loading,setLoading]=useState(true),[error,setError]=useState('');
 const load=useCallback(async(nextPage=page)=>{
  setLoading(true);setError('');
  try{
   const response=await fetch(API_BASE+'/api/v1/platform/analytics/tenant-usage?page='+nextPage+'&size=100',{headers:{Authorization:'Bearer '+s.accessToken}});
   const body=await response.json().catch(()=>({}));
   if(!response.ok)throw new Error(body.detail||body.message||'Unable to load platform analytics ('+response.status+')');
   setData(body);setPage(Number(body.page??nextPage));
  }catch(e:any){setError(e.message||'Unable to load platform analytics');}
  finally{setLoading(false);}
 },[s.accessToken,page]);
 useEffect(()=>{void load(0)},[s.accessToken]);
 const rows=data?.items||[];
 const sum=(key:string)=>rows.reduce((total:number,row:any)=>total+Number(row[key]?.used||0),0);
 return <section className="platform-shell platform-analytics-panel">
  <div className="platform-heading"><div><p className="eyebrow">PLATFORM INSIGHTS</p><h1>Analytics</h1><p className="muted">Tenant adoption and resource usage across the platform.</p></div><button type="button" className="secondary" onClick={()=>void load(page)} disabled={loading}>Refresh</button></div>
  {loading?<div className="card" role="status" aria-live="polite">Loading platform analytics…</div>:error?<div className="card" role="alert"><h2>Analytics could not be loaded</h2><p>{error}</p><button type="button" onClick={()=>void load(page)}>Retry</button></div>:<>
   <div className="platform-stats"><article className="card"><small>Total tenants</small><strong>{data?.total??0}</strong></article><article className="card"><small>Active users (loaded tenants)</small><strong>{sum('users')}</strong></article><article className="card"><small>Active products (loaded tenants)</small><strong>{sum('products')}</strong></article><article className="card"><small>Active branches (loaded tenants)</small><strong>{sum('branches')}</strong></article></div>
   <div className="card"><div className="platform-heading"><div><h2>Tenant resource usage</h2><p className="muted">Usage compared with each tenant’s assigned subscription limits.</p></div></div>
    {!rows.length?<div className="empty-state"><h3>No tenant usage data</h3><p>Tenant usage will appear here after vendors are onboarded.</p></div>:<div className="table-wrap"><table><thead><tr><th>Tenant</th><th>Status</th><th>Plan</th><th>Subscription</th><th>Users</th><th>Products</th><th>Branches</th><th>Warehouses</th><th>Storage MB</th></tr></thead><tbody>{rows.map((row:any)=><tr key={row.tenantId}><td><b>{row.tenantName}</b><small>{row.tenantSlug}</small></td><td>{row.tenantStatus}</td><td>{row.planName}</td><td>{row.subscriptionStatus}</td>{['users','products','branches','warehouses','storageMb'].map(key=><td key={key}>{row[key]?.used??0} / {row[key]?.limit??'—'}</td>)}</tr>)}</tbody></table></div>}
    <div className="platform-pagination"><span>{data?.total??0} tenants · Page {Number(data?.totalPages||0)?page+1:0} of {data?.totalPages||0}</span><div className="actions"><button type="button" className="secondary" disabled={loading||page<=0} onClick={()=>void load(page-1)}>Previous</button><button type="button" className="secondary" disabled={loading||page+1>=Number(data?.totalPages||0)} onClick={()=>void load(page+1)}>Next</button></div></div>
   </div>
  </>}
 </section>;
}
