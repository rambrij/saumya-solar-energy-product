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
 const [branches,setBranches]=useState<any[]>([]),[plans,setPlans]=useState<any[]>([]),[subscription,setSubscription]=useState<any>(null),[audits,setAudits]=useState<any[]>([]),[branding,setBranding]=useState<any>({displayName:'',logoUrl:'',primaryColor:'#0B6B4B',secondaryColor:'#F6C85F',contactEmail:'',contactPhone:'',supportEmail:'',supportPhone:''}),[domains,setDomains]=useState<any[]>([]),[workflows,setWorkflows]=useState<any[]>([]);
 const [approvalInstances,setApprovalInstances]=useState<any[]>([]),[selectedBranch,setSelectedBranch]=useState<string|null>(null),[branchMembers,setBranchMembers]=useState<any[]>([]);
 const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[panelError,setPanelError]=useState('');
 const load=async()=>{
  setLoading(true);setPanelError('');
  try{
   if(mode==='vendor'){
    const perms=s.user?.permissions||[];const [b,sub,brand,domainRows,wf,ai]=await Promise.all([perms.includes('BRANCH_MANAGE')?request('/branches',s):Promise.resolve([]),request('/subscription',s).catch(()=>null),perms.includes('BRANDING_MANAGE')?request('/vendor/branding',s):Promise.resolve(branding),perms.includes('BRANDING_MANAGE')?request('/vendor/domains',s):Promise.resolve([]),perms.includes('APPROVAL_WORKFLOW_MANAGE')?request('/approval-workflows',s):Promise.resolve([]),perms.includes('APPROVAL_WORKFLOW_MANAGE')?request('/approval-instances',s):Promise.resolve([])]);
    setBranches(b);setSubscription(sub);setBranding(brand);setDomains(domainRows);setWorkflows(wf);setApprovalInstances(ai);
   }else setPlans(await request('/subscription-plans',s));
  }catch(e:any){setPanelError(e.message)}finally{setLoading(false)}
 };
 useEffect(()=>{void load()},[mode,s.accessToken]);
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
  try{await request('/platform/tenants/'+x.tenantId+'/subscriptions',s,'POST',{planId:x.planId,status:x.status,startsAt:new Date().toISOString()});form.reset();error('Subscription assigned.')}catch(e:any){setPanelError(e.message)}finally{setBusy(false)}
 };
 const loadAudit=async()=>{
  try{const from=new Date(Date.now()-30*86400000).toISOString(),to=new Date().toISOString();setAudits(await request('/audit-events?from='+encodeURIComponent(from)+'&to='+encodeURIComponent(to),s));}
  catch(e:any){setPanelError(e.message)}
 };
 return <section className="platform-shell enterprise-saas-panel">
  <div className="platform-heading"><div><p className="eyebrow">PHASE 3 · ENTERPRISE SAAS</p><h1>{mode==='platform'?'Subscription administration':'Branches and subscription'}</h1><p className="muted">Enterprise administration tools. Access is enforced by backend permissions and tenant scope.</p></div><button type="button" className="secondary" onClick={()=>void loadAudit()}>Load audit events (30 days)</button></div>
  {panelError&&<div className="notice" role="alert">{panelError}<button type="button" onClick={()=>setPanelError('')}>×</button></div>}
  {loading?<div className="card">Loading enterprise settings…</div>:mode==='vendor'?<>
   <div className="card"><h2>Current subscription</h2>{subscription?<><p><b>{subscription.plan?.name}</b> · {subscription.subscription?.status}</p><p>Billing: {subscription.plan?.billingCycle} · ₹{subscription.plan?.price} {subscription.plan?.currency}</p><p>Branch limit: {subscription.plan?.maxBranches} · Warehouse limit: {subscription.plan?.maxWarehouses} · User limit: {subscription.plan?.maxUsers}</p><p>Effective: {subscription.effective?'Yes':'No'}</p></>:<p>No active subscription is assigned. Contact the platform administrator.</p>}</div>
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
    <form className="form" onSubmit={createPlan}><h3>Create subscription plan</h3><input name="planCode" placeholder="Plan code (e.g. STARTER)" required/><input name="name" placeholder="Plan name" required/><textarea name="description" placeholder="Description"/><select name="billingCycle"><option value="MONTHLY">Monthly</option><option value="YEARLY">Yearly</option></select><input name="price" type="number" min="0" step="0.01" placeholder="Price (INR)" required/><input name="currency" defaultValue="INR" maxLength={3} required/><input name="maxUsers" type="number" min="1" defaultValue="5" required/><input name="maxBranches" type="number" min="1" defaultValue="1" required/><input name="maxWarehouses" type="number" min="1" defaultValue="1" required/><input name="maxStorageMb" type="number" min="0" defaultValue="1024" required/><textarea name="entitlementsJson" defaultValue="{}" placeholder="Feature entitlements JSON" required/><button disabled={busy}>{busy?'Saving…':'Create plan'}</button></form>
   </div>
   <form className="card form" onSubmit={assignSubscription}><h2>Assign subscription to tenant</h2><input name="tenantId" placeholder="Tenant UUID" required/><select name="planId" required defaultValue=""><option value="" disabled>Select plan</option>{plans.map(p=><option key={p.id} value={p.id}>{p.name} · {p.planCode}</option>)}</select><select name="status"><option value="TRIAL">Trial</option><option value="ACTIVE">Active</option></select><button disabled={busy||!plans.length}>{busy?'Assigning…':'Assign subscription'}</button></form>
  </>}
  {audits.length>0&&<div className="card"><h2>Recent audit events</h2><div className="table-wrap"><table><thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Outcome</th></tr></thead><tbody>{audits.map((a:any)=><tr key={a.id}><td>{new Date(a.createdAt).toLocaleString()}</td><td>{a.actorId||a.actorType}</td><td>{a.action}</td><td>{a.entityType} · {a.entityId||'—'}</td><td>{a.outcome}</td></tr>)}</tbody></table></div></div>}
 </section>;
}
