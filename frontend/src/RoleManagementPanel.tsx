import React,{useEffect,useMemo,useState} from 'react';

type Props={s:any;error:(message:string)=>void};
export default function RoleManagementPanel({s,error}:Props){
 const[roles,setRoles]=useState<any[]>([]);
 const[permissions,setPermissions]=useState<any[]>([]);
 const[selected,setSelected]=useState<any>(null);
 const[name,setName]=useState('');
 const[codes,setCodes]=useState<string[]>([]);
 const[loading,setLoading]=useState(true);
 const[saving,setSaving]=useState(false);
 const[search,setSearch]=useState('');
 const load=async()=>{
  setLoading(true);
  try{
   const [roleRows,permissionRows]=await Promise.all([api('/roles',{},s),api('/permissions',{},s)]);
   setRoles(roleRows||[]);setPermissions(permissionRows||[]);
  }catch(e:any){error(e.message||'Unable to load role permissions')}
  finally{setLoading(false)}
 };
 useEffect(()=>{void load()},[s.accessToken]);
 const groups=useMemo(()=>{
  const map=new Map<string,any[]>();
  for(const permission of permissions){
   const group=String(permission.code||'OTHER').split('_')[0];
   map.set(group,[...(map.get(group)||[]),permission]);
  }
  return [...map.entries()];
 },[permissions]);
 const startCreate=()=>{setSelected({customRole:true});setName('');setCodes([])};
 const startEdit=(role:any)=>{
  if(!role.customRole){error('System-defined roles cannot be edited. Create a custom role instead.');return}
  setSelected(role);setName(role.name);setCodes((role.permissions||[]).map((p:any)=>p.code));
 };
 const toggle=(code:string)=>setCodes(current=>current.includes(code)?current.filter(x=>x!==code):[...current,code]);
 const save=async(e:React.FormEvent<HTMLFormElement>)=>{
  e.preventDefault();setSaving(true);
  try{
   const payload={name:name.trim().toUpperCase(),permissions:codes};
   if(selected.id)await send('/roles/'+selected.id,payload,s,'PATCH');
   else await send('/roles',payload,s);
   setSelected(null);await load();error('Role permissions saved.');
  }catch(e:any){error(e.message||'Unable to save role')}
  finally{setSaving(false)}
 };
 const filtered=roles.filter(role=>String(role.name).toLowerCase().includes(search.toLowerCase()));
 return <section className="role-management-page">
  <div className="management-heading"><div><p className="eyebrow">CUSTOMERS & TEAM</p><h1>Roles & Permissions</h1><p className="muted">Create tenant-specific roles using the approved SRS permission catalogue. Platform permissions are never assignable to vendor roles.</p></div><button type="button" onClick={startCreate}>＋ Create Custom Role</button></div>
  {selected&&<form className="card form" onSubmit={save}>
   <h2>{selected.id?'Edit Custom Role':'Create Custom Role'}</h2>
   <label>Role name<input value={name} onChange={e=>setName(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g,''))} minLength={3} maxLength={50} pattern="[A-Z][A-Z0-9_]{2,49}" placeholder="e.g. REGIONAL_SALES" required/></label>
   <p className="muted">Select only the actions this role needs. Empty selection creates a read-only/no-access role.</p>
   <div className="role-permission-groups">{groups.map(([group,items])=><fieldset key={group} className="role-permission-group"><legend>{group.replaceAll('_',' ')}</legend><div className="role-permission-grid">{items.map((permission:any)=><label key={permission.code} className="role-permission-option"><input type="checkbox" checked={codes.includes(permission.code)} onChange={()=>toggle(permission.code)}/><span><b>{permission.code}</b>{permission.description&&<small>{permission.description}</small>}</span></label>)}</div></fieldset>)}</div>
   <div className="form-actions"><button disabled={saving}>{saving?'Saving…':'Save Role'}</button><button type="button" className="secondary" onClick={()=>setSelected(null)}>Cancel</button></div>
  </form>}
  <div className="card management-list"><div className="table-toolbar"><div><h2>Tenant roles</h2><span className="muted">{filtered.length} roles</span></div><input aria-label="Search roles" placeholder="Search roles…" value={search} onChange={e=>setSearch(e.target.value)}/></div>
   {loading?<p>Loading roles…</p>:!filtered.length?<p>No roles are configured.</p>:<div className="table-wrap"><table><thead><tr><th>Role</th><th>Type</th><th>Permissions</th><th>Actions</th></tr></thead><tbody>{filtered.map(role=><tr key={role.id}><td><b>{role.name.replaceAll('_',' ')}</b></td><td>{role.customRole?'Custom':'System'}</td><td>{(role.permissions||[]).length}</td><td>{role.customRole?<button type="button" className="secondary" onClick={()=>startEdit(role)}>Edit permissions</button>:<span className="muted">Managed by system</span>}</td></tr>)}</tbody></table></div>}
  </div>
 </section>;
}

async function api(path:string,init:RequestInit={},s:any){
 const headers=new Headers(init.headers||{});
 if(init.body&&!(init.body instanceof FormData)&&!headers.has('Content-Type'))headers.set('Content-Type','application/json');
 const response=await authenticatedFetch((import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'')+'/api/v1'+path,{...init,headers},s);
 if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.detail||body.message||'Request failed ('+response.status+')')}
 return response.status===204?null:response.json();
}
async function send(path:string,body:any,s:any,method='POST'){
 return api(path,{method,body:JSON.stringify(body)},s);
}
import {authenticatedFetch} from './authenticatedFetch';
