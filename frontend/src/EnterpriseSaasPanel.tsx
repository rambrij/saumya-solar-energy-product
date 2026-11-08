import React,{useEffect,useState} from 'react';
const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
type Session={accessToken:string;user:{tenantId?:string;permissions:string[]}};
async function request(path:string,s:Session,method='GET',body?:any){
 const r=await fetch(API_BASE+'/api/v1'+path,{method,headers:{Authorization:'Bearer '+s.accessToken,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
 const data=await r.json().catch(()=>({}));
 if(!r.ok)throw new Error(data.detail||data.message||'Request failed ('+r.status+')');
 return data;
}
export default function EnterpriseSaasPanel({mode,s,error}:{mode:'platform'|'vendor';s:Session;error:(message:string)=>void}){
 const [branches,setBranches]=useState<any[]>([]),[plans,setPlans]=useState<any[]>([]),[subscription,setSubscription]=useState<any>(null),[audits,setAudits]=useState<any[]>([]),[branding,setBranding]=useState<any>({displayName:'',logoUrl:'',primaryColor:'#0B6B4B',secondaryColor:'#F6C85F',contactEmail:'',contactPhone:'',supportEmail:'',supportPhone:''}),[domains,setDomains]=useState<any[]>([]),[workflows,setWorkflows]=useState<any[]>([]),[integrations,setIntegrations]=useState<any[]>([]);
 const [approvalInstances,setApprovalInstances]=useState<any[]>([]),[selectedBranch,setSelectedBranch]=useState<string|null>(null),[branchMembers,setBranchMembers]=useState<any[]>([]);
 const [subscriptionUsage,setSubscriptionUsage]=useState<any>(null),[storageUsage,setStorageUsage]=useState<any>(null),[selectedIntegration,setSelectedIntegration]=useState<string|null>(null),[integrationJobs,setIntegrationJobs]=useState<any[]>([]);
 const [auditFilters,setAuditFilters]=useState({actorId:'',action:'',entityType:'',outcome:''});
 const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[panelError,setPanelError]=useState('');
 const load=async()=>{
  setLoading(true);setPanelError('');
  try{
   if(mode==='vendor'){
    const perms=s.user?.permissions||[];const [b,sub,brand,domainRows,wf,ai,ic,su,storage]=await Promise.all([perms.includes('BRANCH_MANAGE')?request('/branches',s):Promise.resolve([]),request('/subscription',s).catch(()=>null),perms.includes('BRANDING_MANAGE')?request('/vendor/branding',s):Promise.resolve(branding),perms.includes('BRANDING_MANAGE')?request('/vendor/domains',s):Promise.resolve([]),perms.includes('APPROVAL_WORKFLOW_MANAGE')?request('/approval-workflows',s):Promise.resolve([]),perms.includes('APPROVAL_WORKFLOW_MANAGE')?request('/approval-instances',s):Promise.resolve([]),perms.includes('INTEGRATION_MANAGE')?request('/integrations',s):Promise.resolve([]),request('/subscription/usage',s).catch(()=>null),request('/subscription/storage',s).catch(()=>null)]);
    setBranches(b);setSubscription(sub);setBranding(brand);setDomains(domainRows);setWorkflows(wf);setApprovalInstances(ai);setIntegrations(ic);setSubscriptionUsage(su);setStorageUsage(storage);
   }else setPlans(await request('/subscription-plans',s));
  }catch(e:any){setPanelError(e.message)}finally{setLoading(false)}
 };
 useEffect(()=>{void load()},[mode,s.accessToken]);
 const loadIntegrationJobs=async(id:string)=>{try{setSelectedIntegration(id);setIntegrationJobs(await request('/integrations/'+id+'/sync-jobs',s));}catch(e:any){setPanelError(e.message)}};
 const queueIntegrationJob=async(e:React.FormEvent<HTMLFormElement>)=>{e.preventDefault();if(!selectedIntegration)return;const form=e.currentTarget;const x:any=Object.fromEntries(new FormData(form));try{await request('/integrations/'+selectedIntegration+'/sync-jobs',s,'POST',x);form.reset();await loadIntegrationJobs(selectedIntegration);error('Synchronization job queued.')}catch(e:any){setPanelError(e.message)}};
 const retryIntegrationJob=async(jobId:string)=>{try{await request('/integrations/sync-jobs/'+jobId+'/retry',s,'POST');if(selectedIntegration)await loadIntegrationJobs(selectedIntegration);error('Synchronization retry queued.')}catch(e:any){setPanelError(e.message)}};
 const toggleIntegration=async(item:any)=>{
  const status=item.status==='ACTIVE'?'DISABLED':'ACTIVE';
  try{const updated=await request('/integrations/'+item.id+'/status',s,'PATCH',{status});setIntegrations(v=>v.map(x=>x.id===item.id?updated:x));error('Integration status updated.')}catch(e:any){setPanelError(e.message)}
 };
 const createBranch=async(e:React.FormEvent<HTMLFormElement>)=>{
  e.preventDefault();const form=e.currentTarget;const x:any=Object.fromEntries(new FormData(form));setBusy(true);
  try{await request('/branches',s,'POST',x);form.reset();await load();error('Branch created.')}catch(e:any){setPanelError(e.message)}finally{setBusy(false)}
 };
 const loadBranchMembers=async(branchId:string)=>{
  try{setSelectedBranch(branchId);setBranchMembers(await request('/branches/'+branchId+'/users',s));}
  catch(e:any){setPanelError(e.message)}
 };
 const removeBranchMember=async(branchId:string,userId:string)=>{
  try{await request('/branches/'+branchId+'/users/'+userId,s,'DELETE');await loadBranchMembers(branchId);error('Branch membership removed.');}
  catch(e:any){setPanelError(e.message)}
 };
 const decideApproval=async(instanceId:string,decision:'APPROVE'|'REJECT')=>{
  const comments=window.prompt('Optional decision comments')||'';
  try{await request('/approval-instances/'+instanceId+'/decisions',s,'POST',{decision,comments});await load();error('Approval decision recorded.');}
  catch(e:any){setPanelError(e.message)}
 };
 const createIntegration=async(e:React.FormEvent<HTMLFormElement>)=>{
  e.preventDefault();const form=e.currentTarget;const x:any=Object.fromEntries(new FormData(form));setBusy(true);
  try{const created=await request('/integrations',s,'POST',x);setIntegrations(v=>[created,...v]);form.reset();error('Integration connection saved. A provider adapter is required before sync jobs can run.')}catch(e:any){setPanelError(e.message)}finally{setBusy(false)}
 };
 const createWorkflow=async(e:React.FormEvent<HTMLFormElement>)=>{
  e.preventDefault();const form=e.currentTarget;const x:any=Object.fromEntries(new FormData(form));setBusy(true);
  try{const created=await request('/approval-workflows',s,'POST',x);setWorkflows(v=>[created,...v]);form.reset();error('Approval workflow created.')}catch(e:any){setPanelError(e.message)}finally{setBusy(false)}
 };
 const saveBranding=async(e:React.FormEvent<HTMLFormElement>)=>{
  e.preventDefault();const form=e.currentTarget;const x:any=Object.fromEntries(new FormData(form));setBusy(true);
  try{const saved=await request('/vendor/branding',s,'PATCH',x);setBranding(saved);error('Vendor branding saved.')}catch(e:any){setPanelError(e.message)}finally{setBusy(false)}
 };
 const addDomain=async(e:React.FormEvent<HTMLFormElement>)=>{
  e.preventDefault();const form=e.currentTarget;const x:any=Object.fromEntries(new FormData(form));setBusy(true);
  try{const created=await request('/vendor/domains',s,'POST',x);setDomains(v=>[created,...v]);form.reset();error('Domain registered. Add the displayed TXT record in your DNS, then verify it.')}catch(e:any){setPanelError(e.message)}finally{setBusy(false)}
 };
 const verifyDomain=async(id:string)=>{
  try{const updated=await request('/vendor/domains/'+id+'/verify',s,'POST');setDomains(v=>v.map(d=>d.id===id?updated:d));error('Domain verified.')}catch(e:any){setPanelError(e.message)}
 };
 const assignUser=async(branchId:string)=>{
  const userId=window.prompt('Enter the existing same-vendor user ID to assign to this branch');if(!userId)return;
  try{await request('/branches/'+branchId+'/users',s,'POST',{userId});error('User assigned to branch.')}catch(e:any){setPanelError(e.message)}
 };
 const createPlan=async(e:React.FormEvent<HTMLFormElement>)=>{
  e.preventDefault();const form=e.currentTarget;const x:any=Object.fromEntries(new FormData(form));
  ['price','maxUsers','maxBranches','maxWarehouses','maxStorageMb'].forEach(k=>x[k]=Number(x[k]));
  setBusy(true);try{await request('/platform/subscription-plans',s,'POST',x);form.reset();await load();error('Subscription plan created.')}catch(e:any){setPanelError(e.message)}finally{setBusy(false)}
 };
 const assignSubscription=async(e:React.FormEvent<HTMLFormElement>)=>{
  e.preventDefault();const form=e.currentTarget;const x:any=Object.fromEntries(new FormData(form));setBusy(true);
  try{await request('/platform/tenants/'+x.tenantId+'/subscriptions',s,'POST',{planId:x.planId,status:x.status,startsAt:x.startsAt?new Date(x.startsAt).toISOString():new Date().toISOString(),endsAt:x.endsAt?new Date(x.endsAt).toISOString():null,renewsAt:x.renewsAt?new Date(x.renewsAt).toISOString():null,externalReference:x.externalReference||null});form.reset();error('Subscription assigned.')}catch(e:any){setPanelError(e.message)}finally{setBusy(false)}
 };
 const loadAudit=async()=>{
  try{const from=new Date(Date.now()-30*86400000).toISOString(),to=new Date().toISOString();const params=new URLSearchParams({from,to});Object.entries(auditFilters).forEach(([key,value])=>{if(value.trim())params.set(key,value.trim())});setAudits(await request('/audit-events?'+params.toString(),s));}
  catch(e:any){setPanelError(e.message)}
 };
 return <section className="platform-shell enterprise-saas-panel">
  <div className="platform-heading"><div><p className="eyebrow">PHASE 3 · ENTERPRISE SAAS</p><h1>{mode==='platform'?'Subscription administration':'Branches and subscription'}</h1><p className="muted">Enterprise administration tools. Access is enforced by backend permissions and tenant scope.</p></div>{s.user?.permissions?.includes('AUDIT_VIEW')&&<button type="button" className="secondary" onClick={()=>void loadAudit()}>Load audit events (30 days)</button>}</div>
  {s.user?.permissions?.includes('AUDIT_VIEW')&&<div className="card form"><h2>Audit search · last 30 days</h2><div className="form-grid"><input aria-label="Actor ID" placeholder="Actor UUID" value={auditFilters.actorId} onChange={e=>setAuditFilters(v=>({...v,actorId:e.target.value}))}/><input aria-label="Action" placeholder="Action (e.g. SUBSCRIPTION_ASSIGNED)" value={auditFilters.action} onChange={e=>setAuditFilters(v=>({...v,action:e.target.value}))}/><input aria-label="Entity type" placeholder="Entity type" value={auditFilters.entityType} onChange={e=>setAuditFilters(v=>({...v,entityType:e.target.value}))}/><select aria-label="Outcome" value={auditFilters.outcome} onChange={e=>setAuditFilters(v=>({...v,outcome:e.target.value}))}><option value="">Any outcome</option><option value="SUCCESS">Success</option><option value="FAILURE">Failure</option></select></div><button type="button" className="secondary" onClick={()=>void loadAudit()}>Search audit events</button></div>}
  {panelError&&<div className="notice" role="alert">{panelError}<button type="button" onClick={()=>setPanelError('')}>×</button></div>}
  {loading?<div className="card">Loading enterprise settings…</div>:mode==='vendor'?<>
   <div className="card"><h2>Current subscription</h2>{subscription?<><p><b>{subscription.plan?.name}</b> · {subscription.subscription?.status}</p><p>Billing: {subscription.plan?.billingCycle} · ₹{subscription.plan?.price} {subscription.plan?.currency}</p><p>Branch limit: {subscription.plan?.maxBranches} · Warehouse limit: {subscription.plan?.maxWarehouses} · User limit: {subscription.plan?.maxUsers}</p><p>Effective: {subscription.effective?'Yes':'No'}</p></>:<p>No active subscription is assigned. Contact the platform administrator.</p>}</div>
   {subscriptionUsage&&<div className="card"><h2>Subscription usage</h2><p className="muted">Metered usage for {subscriptionUsage.periodStart} to {subscriptionUsage.periodEnd} (UTC).</p><div className="table-wrap"><table><thead><tr><th>Resource</th><th>Used</th><th>Plan limit</th><th>Remaining</th></tr></thead><tbody>{(subscriptionUsage.meters||[]).map((m:any)=><tr key={m.code}><td>{m.code.replaceAll('_',' ')}</td><td>{m.used}</td><td>{m.limit??'Unlimited / grandfathered'}</td><td>{m.remaining??'—'}</td></tr>)}</tbody></table></div><p className="muted">Status: {subscriptionUsage.subscriptionStatus}</p></div>}
   {storageUsage&&<div className="card"><h2>Private evidence storage</h2><p>{(storageUsage.usedMegabytes||0).toFixed(2)} MB used {storageUsage.unlimited?'· no storage limit assigned':`of ${storageUsage.limitMegabytes} MB`}</p>{!storageUsage.unlimited&&<div className="usage-meter" role="progressbar" aria-label="Tenant evidence storage usage" aria-valuemin={0} aria-valuemax={100} aria-valuenow={storageUsage.limitBytes?Math.min(100,Math.round(storageUsage.usedBytes/storageUsage.limitBytes*100)):0}><div style={{width:(storageUsage.limitBytes?Math.min(100,storageUsage.usedBytes/storageUsage.limitBytes*100):0)+'%',height:8,background:'#0B6B4B',borderRadius:8}}/></div>}<p className="muted">Includes private service-ticket, site-survey and warranty-claim evidence files. {storageUsage.unlimited?'Your legacy subscription is grandfathered.':`${(storageUsage.remainingBytes/1048576).toFixed(2)} MB remaining.`}</p></div>}
   {s.user?.permissions?.includes('INTEGRATION_MANAGE')&&<div className="card"><h2>External integrations</h2><p className="muted">Tenant-scoped provider connections. Credentials must be stored as environment references, never in configuration JSON.</p>
    {integrations.length?<div className="table-wrap"><table><thead><tr><th>Provider</th><th>Type</th><th>Name</th><th>Status</th><th>Action</th></tr></thead><tbody>{integrations.map(item=><tr key={item.id}><td>{item.providerCode}</td><td>{item.integrationType}</td><td>{item.displayName}</td><td>{item.status}</td><td><button type="button" className="secondary" onClick={()=>void toggleIntegration(item)}>{item.status==='ACTIVE'?'Disable':'Enable'}</button> <button type="button" className="secondary" onClick={()=>void loadIntegrationJobs(item.id)}>Sync jobs</button></td></tr>)}</tbody></table></div>:<p>No external integrations configured.</p>}
    <form className="form" onSubmit={createIntegration}><h3>Register integration</h3><input name="providerCode" placeholder="Provider code (e.g. GENERIC_HTTP)" required maxLength={60}/><select name="integrationType"><option value="ACCOUNTING">Accounting</option><option value="PAYMENT_GATEWAY">Payment gateway</option><option value="SMS">SMS</option><option value="WHATSAPP">WhatsApp</option><option value="GENERIC">Generic API</option></select><input name="displayName" placeholder="Display name" required maxLength={150}/><input name="secretReference" placeholder="env://SECRET_NAME"/><textarea name="configJson" defaultValue={'{\n  "endpoint": "https://api.example.com/sync"\n}'} required/><button disabled={busy}>{busy?'Saving…':'Register integration'}</button></form>
    {selectedIntegration&&<div className="card"><h3>Synchronization jobs</h3>{integrationJobs.length?<div className="table-wrap"><table><thead><tr><th>Entity</th><th>Operation</th><th>Status</th><th>Attempts</th><th>Next attempt</th><th>Action</th></tr></thead><tbody>{integrationJobs.map(j=><tr key={j.id}><td>{j.entityType} · {j.entityId}</td><td>{j.operation}</td><td>{j.status}</td><td>{j.attemptCount}</td><td>{j.nextAttemptAt?new Date(j.nextAttemptAt).toLocaleString():'—'}</td><td>{['DEAD_LETTER','WAITING_FOR_ADAPTER','RETRY'].includes(j.status)&&<button type="button" className="secondary" onClick={()=>void retryIntegrationJob(j.id)}>Retry</button>}</td></tr>)}</tbody></table></div>:<p>No synchronization jobs for this connection.</p>}
     <form className="form" onSubmit={queueIntegrationJob}><h4>Queue synchronization</h4><input name="entityType" placeholder="Entity type (e.g. INVOICE)" required/><input name="entityId" placeholder="Entity ID" required/><input name="operation" placeholder="Operation (e.g. UPSERT)" required/><input name="idempotencyKey" placeholder="Unique idempotency key" required maxLength={160}/><textarea name="payloadJson" defaultValue="{}" required/><button type="submit">Queue job</button></form>
    </div>}
   </div>}
   {s.user?.permissions?.includes('APPROVAL_WORKFLOW_MANAGE')&&<div className="card"><h2>Approval workflows</h2>
    {workflows.map(w=><p key={w.id}><b>{w.name}</b> · {w.entityType} · {w.active?'Active':'Inactive'}</p>)}
    <h3>Approval requests</h3>
    {approvalInstances.length?<div className="table-wrap"><table><thead><tr><th>Entity</th><th>Status</th><th>Step</th><th>Requested</th><th>Actions</th></tr></thead><tbody>{approvalInstances.map(i=><tr key={i.id}><td>{i.entityType} · {i.entityId}</td><td>{i.status}</td><td>{i.currentStep}</td><td>{new Date(i.requestedAt).toLocaleString()}</td><td>{i.status==='PENDING'&&<><button type="button" onClick={()=>void decideApproval(i.id,'APPROVE')}>Approve</button> <button type="button" className="danger" onClick={()=>void decideApproval(i.id,'REJECT')}>Reject</button></>}</td></tr>)}</tbody></table></div>:<p>No approval requests yet.</p>}
    <form className="form" onSubmit={createWorkflow}><h3>Create quotation approval workflow</h3><input name="code" placeholder="Workflow code" required/><input name="name" placeholder="Workflow name" required/><input name="entityType" value="QUOTATION" readOnly/><textarea name="stepsJson" defaultValue={'[\n  { "approverRole": "VENDOR_ADMIN", "permissionCode": "DISCOUNT_APPROVE" },\n  { "approverRole": "VENDOR_ADMIN", "permissionCode": "DISCOUNT_APPROVE" }\n]'} required/><button disabled={busy}>{busy?'Saving…':'Create workflow'}</button></form>
   </div>}
   {s.user?.permissions?.includes("BRANDING_MANAGE")&&<div className="card"><h2>Vendor branding</h2><form className="form" onSubmit={saveBranding}>
    <input name="displayName" defaultValue={branding.displayName||''} placeholder="Public display name" required/>
    <input name="logoUrl" defaultValue={branding.logoUrl||''} placeholder="Logo URL (HTTPS) or /uploads/ path"/>
    <label>Primary color<input name="primaryColor" type="color" defaultValue={branding.primaryColor||'#0B6B4B'}/></label>
    <label>Secondary color<input name="secondaryColor" type="color" defaultValue={branding.secondaryColor||'#F6C85F'}/></label>
    <input name="contactEmail" type="email" defaultValue={branding.contactEmail||''} placeholder="Public contact email"/>
    <input name="contactPhone" defaultValue={branding.contactPhone||''} placeholder="Public contact phone"/>
    <input name="supportEmail" type="email" defaultValue={branding.supportEmail||''} placeholder="Support email"/>
    <input name="supportPhone" defaultValue={branding.supportPhone||''} placeholder="Support phone"/>
    <button disabled={busy}>{busy?'Saving…':'Save branding'}</button>
   </form></div>}
   {s.user?.permissions?.includes("BRANDING_MANAGE")&&<div className="card"><h2>Custom domains</h2><form className="form" onSubmit={addDomain}><input name="domain" placeholder="shop.example.com" required/><button disabled={busy}>Register domain</button></form>
    {domains.map(d=><article key={d.id}><div><b>{d.domain}</b><small>{d.status} · TXT value: saumya-solar-verification={d.verificationToken}</small></div>{d.status!=='VERIFIED'&&<button type="button" className="secondary" onClick={()=>void verifyDomain(d.id)}>Verify DNS</button>}</article>)}
   </div>}
   {s.user?.permissions?.includes("BRANCH_MANAGE")&&<div className="card"><h2>Branches</h2>{branches.length?<div className="table-wrap"><table><thead><tr><th>Code</th><th>Name</th><th>Address</th><th>Status</th><th>Actions</th></tr></thead><tbody>{branches.map(b=><tr key={b.id}><td>{b.code}</td><td>{b.name}</td><td>{b.address||'—'}</td><td>{b.active?'Active':'Inactive'}</td><td><button type="button" className="secondary" onClick={()=>void assignUser(b.id)}>Assign user</button> <button type="button" className="secondary" onClick={()=>void loadBranchMembers(b.id)}>Members</button></td></tr>)}</tbody></table></div>:<p>No branches configured.</p>}
    {selectedBranch&&<div className="card"><h3>Branch members</h3>{branchMembers.length?<ul>{branchMembers.map(m=><li key={m.id}>{m.userId} <button type="button" className="danger" onClick={()=>void removeBranchMember(selectedBranch,m.userId)}>Remove</button></li>)}</ul>:<p>No members assigned to this branch.</p>}</div>}
    <form className="form" onSubmit={createBranch}><h3>Create branch</h3><input name="code" placeholder="Branch code" maxLength={50} required/><input name="name" placeholder="Branch name" maxLength={150} required/><input name="address" placeholder="Address"/><input name="phone" placeholder="Phone"/><input name="email" type="email" placeholder="Contact email"/><button disabled={busy}>{busy?'Saving…':'Create branch'}</button></form>
   </div>}
  </>:<>
   <div className="card"><h2>Subscription plans</h2>{plans.length?<div className="table-wrap"><table><thead><tr><th>Plan</th><th>Cycle</th><th>Price</th><th>Limits</th></tr></thead><tbody>{plans.map(p=><tr key={p.id}><td>{p.name} ({p.planCode})</td><td>{p.billingCycle}</td><td>{p.currency} {p.price}</td><td>{p.maxUsers} users · {p.maxBranches} branches · {p.maxWarehouses} warehouses</td></tr>)}</tbody></table></div>:<p>No plans created yet.</p>}
    <form className="form" onSubmit={createPlan}><h3>Create subscription plan</h3><input name="planCode" placeholder="Plan code (e.g. STARTER)" required/><input name="name" placeholder="Plan name" required/><textarea name="description" placeholder="Description"/><select name="billingCycle"><option value="MONTHLY">Monthly</option><option value="YEARLY">Yearly</option></select><input name="price" type="number" min="0" step="0.01" placeholder="Price (INR)" required/><input name="currency" defaultValue="INR" maxLength={3} required/><input name="maxUsers" type="number" min="1" defaultValue="5" required/><input name="maxBranches" type="number" min="1" defaultValue="1" required/><input name="maxWarehouses" type="number" min="1" defaultValue="1" required/><input name="maxStorageMb" type="number" min="0" defaultValue="1024" required/><textarea name="entitlementsJson" defaultValue={'{"branches":true,"multiWarehouse":true,"customBranding":true,"customDomains":true,"approvalWorkflows":true,"accountingIntegration":true,"paymentGateway":true,"apiIntegrations":true}'} placeholder="Feature entitlements JSON" required/><button disabled={busy}>{busy?'Saving…':'Create plan'}</button></form>
   </div>
   <form className="card form" onSubmit={assignSubscription}><h2>Assign subscription to tenant</h2><input name="tenantId" placeholder="Tenant UUID" required/><select name="planId" required defaultValue=""><option value="" disabled>Select plan</option>{plans.map(p=><option key={p.id} value={p.id}>{p.name} · {p.planCode}</option>)}</select><select name="status"><option value="TRIAL">Trial</option><option value="ACTIVE">Active</option></select><label>Starts at<input name="startsAt" type="datetime-local"/></label><label>Ends at<input name="endsAt" type="datetime-local"/></label><label>Renews at<input name="renewsAt" type="datetime-local"/></label><input name="externalReference" placeholder="External billing reference (optional)"/><button disabled={busy||!plans.length}>{busy?'Assigning…':'Assign subscription'}</button></form>
  </>}
  {audits.length>0&&<div className="card"><h2>Recent audit events</h2><div className="table-wrap"><table><thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Outcome</th></tr></thead><tbody>{audits.map((a:any)=><tr key={a.id}><td>{new Date(a.createdAt).toLocaleString()}</td><td>{a.actorId||a.actorType}</td><td>{a.action}</td><td>{a.entityType} · {a.entityId||'—'}</td><td>{a.outcome}</td></tr>)}</tbody></table></div></div>}
 </section>;
}
