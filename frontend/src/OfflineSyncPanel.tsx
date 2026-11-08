import React, {useCallback, useEffect, useState} from 'react';

type Session = {accessToken:string; user:{id:string;tenantId?:string;accountType:string}};
type Pending = {operationId:string;ownerUserId:string;ownerTenantId:string;type:string;resourceId:string;payload:string;clientCreatedAt:string;state:'PENDING'|'SYNCED';lastError?:string};

const DB_NAME='saumya-offline-operations';
const STORE='operations';
function openDb():Promise<IDBDatabase>{
 return new Promise((resolve,reject)=>{
  const request=indexedDB.open(DB_NAME,1);
  request.onupgradeneeded=()=>{const db=request.result;if(!db.objectStoreNames.contains(STORE))db.createObjectStore(STORE,{keyPath:'operationId'});};
  request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);
 });
}
async function allRows():Promise<Pending[]>{
 const db=await openDb();return new Promise((resolve,reject)=>{const tx=db.transaction(STORE,'readonly');const q=tx.objectStore(STORE).getAll();q.onsuccess=()=>resolve(q.result as Pending[]);q.onerror=()=>reject(q.error);tx.oncomplete=()=>db.close();});
}
async function saveRow(row:Pending):Promise<void>{
 const db=await openDb();return new Promise((resolve,reject)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).put(row);tx.oncomplete=()=>{db.close();resolve()};tx.onerror=()=>{db.close();reject(tx.error)};});
}
async function removeRow(id:string):Promise<void>{
 const db=await openDb();return new Promise((resolve,reject)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).delete(id);tx.oncomplete=()=>{db.close();resolve()};tx.onerror=()=>{db.close();reject(tx.error)};});
}
const API=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
export default function OfflineSyncPanel({session}:{session:Session}){
 const [rows,setRows]=useState<Pending[]>([]),[online,setOnline]=useState(navigator.onLine),[busy,setBusy]=useState(false),[message,setMessage]=useState('');
 const [type,setType]=useState('SERVICE_COMMENT_CREATE'),[resourceId,setResourceId]=useState(''),[payload,setPayload]=useState('{"body":"Customer update","visibility":"CUSTOMER"}');
 const refresh=useCallback(async()=>{try{setRows((await allRows()).filter(x=>x.state==='PENDING'&&x.ownerUserId===session.user.id&&x.ownerTenantId===(session.user.tenantId||'')));}catch(e:any){setMessage('Local offline queue unavailable: '+e.message)}},[session.user.id,session.user.tenantId]);
 const flush=useCallback(async()=>{
  if(!navigator.onLine||busy)return;
  const pending=(await allRows()).filter(x=>x.state==='PENDING'&&x.ownerUserId===session.user.id&&x.ownerTenantId===(session.user.tenantId||'')).slice(0,50);
  if(!pending.length)return;
  setBusy(true);setMessage('');
  try{
   const response=await fetch(API+'/api/v1/offline-sync/operations',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+session.accessToken},body:JSON.stringify({operations:pending.map(({operationId,type,resourceId,payload,clientCreatedAt})=>({operationId,type,resourceId,payload,clientCreatedAt}))})});
   const body=await response.json().catch(()=>[]);
   if(!response.ok)throw new Error(body.detail||body.message||'Synchronization failed ('+response.status+')');
   for(const result of body){if(result.status==='APPLIED')await removeRow(result.operationId);}
   setMessage('Synchronization completed for '+body.filter((x:any)=>x.status==='APPLIED').length+' operation(s).');
   await refresh();
  }catch(e:any){setMessage(e.message||'Synchronization failed. Pending changes were retained.');}
  finally{setBusy(false);}
 },[session.accessToken,session.user.id,session.user.tenantId,busy,refresh]);
 useEffect(()=>{void refresh();const onOnline=()=>{setOnline(true);void flush()};const onOffline=()=>setOnline(false);window.addEventListener('online',onOnline);window.addEventListener('offline',onOffline);return()=>{window.removeEventListener('online',onOnline);window.removeEventListener('offline',onOffline)}},[refresh,flush]);
 const enqueue=async(e:React.FormEvent<HTMLFormElement>)=>{
  e.preventDefault();if(!resourceId.trim()){setMessage('Enter the service ticket or survey ID.');return}
  try{JSON.parse(payload);}catch{setMessage('Payload must be valid JSON.');return}
  const row:Pending={operationId:crypto.randomUUID(),ownerUserId:session.user.id,ownerTenantId:session.user.tenantId||'',type,resourceId:resourceId.trim(),payload,clientCreatedAt:new Date().toISOString(),state:'PENDING'};
  try{await saveRow(row);setResourceId('');await refresh();setMessage('Saved locally. It will synchronize when a connection is available.');if(navigator.onLine)void flush();}
  catch(e:any){setMessage('Could not save offline operation: '+e.message)}
 };
 return <section className="card offline-sync-panel" aria-label="Offline business synchronization">
  <div className="platform-heading"><div><h2>Offline business sync</h2><p className="muted">{online?'Online · queued actions can synchronize':'Offline · actions are stored on this device'}</p></div><span className={'status-pill '+(online?'active':'pending')}>{online?'Online':'Offline'}</span></div>
  <form className="form" onSubmit={enqueue}>
   <label>Business action<select value={type} onChange={e=>{setType(e.target.value);setPayload(e.target.value==='SERVICE_COMMENT_CREATE'?'{"body":"Customer update","visibility":"CUSTOMER"}':'{}')}}>
    <option value="SERVICE_COMMENT_CREATE">Add service-ticket comment</option><option value="SURVEY_UPDATE">Update site survey</option><option value="SURVEY_START">Start site survey</option><option value="SURVEY_SUBMIT">Submit site survey</option>
   </select></label>
   <label>Service ticket / survey ID<input value={resourceId} onChange={e=>setResourceId(e.target.value)} placeholder="Record UUID" required/></label>
   {(type==='SERVICE_COMMENT_CREATE'||type==='SURVEY_UPDATE')&&<label>Action data (JSON)<textarea rows={3} value={payload} onChange={e=>setPayload(e.target.value)} required/></label>}
   <button type="submit">Save action to offline queue</button>
  </form>
  <div className="actions"><button type="button" className="secondary" disabled={!online||busy||!rows.length} onClick={()=>void flush()}>{busy?'Synchronizing…':'Synchronize now'}</button><button type="button" className="secondary" onClick={()=>void refresh()}>Refresh queue</button></div>
  {message&&<p role="status" className="muted">{message}</p>}
  {rows.length>0?<p>{rows.length} pending operation(s) on this device.</p>:<p className="muted">No pending operations on this device.</p>}
 </section>;
}
