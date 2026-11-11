import React,{useCallback,useEffect,useState} from 'react';
import {authenticatedFetch,type AuthSession} from './authenticatedFetch';
const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
type Setting={key:string;type:string;value:string;version:number;updatedAt?:string|null};
const labels:Record<string,string>={PLATFORM_DEFAULT_CURRENCY:'Default currency',PLATFORM_DEFAULT_TIME_ZONE:'Default time zone',PLATFORM_DEFAULT_LANGUAGE:'Default language',PLATFORM_SUPPORT_EMAIL:'Platform support email',PLATFORM_SUPPORT_PHONE:'Platform support phone',PLATFORM_BANNER_MESSAGE:'Platform announcement'};
export default function PlatformSettingsPanel({s}: {s:AuthSession}){
 const [rows,setRows]=useState<Setting[]>([]),[drafts,setDrafts]=useState<Record<string,string>>({}),[loading,setLoading]=useState(true),[saving,setSaving]=useState(''),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const request=useCallback(async(path:string,init:RequestInit={})=>{const response=await authenticatedFetch(API_BASE+'/api/v1'+path,init,s);const body=await response.json().catch(()=>({}));if(!response.ok)throw new Error(body.detail||body.message||'Platform settings request failed ('+response.status+')');return body},[s.accessToken]);
 const load=useCallback(async()=>{setLoading(true);setError('');try{const values:Setting[]=await request('/platform/settings');setRows(values);setDrafts(Object.fromEntries(values.map(x=>[x.key,x.value])))}catch(e:any){setError(e.message||'Unable to load settings')}finally{setLoading(false)}},[request]);
 useEffect(()=>{void load()},[load]);
 const save=async(row:Setting)=>{setSaving(row.key);setError('');setNotice('');try{const updated=await request('/platform/settings/'+encodeURIComponent(row.key),{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({value:drafts[row.key]??'',expectedVersion:row.version})});setRows(current=>current.map(x=>x.key===row.key?updated:x));setDrafts(current=>({...current,[row.key]:updated.value}));setNotice(labels[row.key]+' saved.')}catch(e:any){setError(e.message||'Unable to save setting')}finally{setSaving('')}};
 if(loading)return <section className="card"><h1>Platform Settings</h1><p>Loading typed platform configuration…</p></section>;
 return <section className="vendor-module-page platform-settings-panel">
  <div className="management-heading"><div><p className="eyebrow">PLATFORM CONFIGURATION</p><h1>Platform Settings</h1><p className="muted">Allow-listed defaults and public support details. Credentials, API keys and provider secrets are not stored or displayed here.</p></div><button type="button" className="secondary" onClick={()=>void load()}>Refresh</button></div>
  {error&&<div className="notice error-state" role="alert">{error}</div>}{notice&&<div className="notice" role="status">{notice}</div>}
  <div className="card settings-grid">{rows.map(row=><article className="platform-setting-row" key={row.key}>
   <div><h3>{labels[row.key]||row.key}</h3><small>{row.key} · {row.type} · version {row.version}</small></div>
   <div className="platform-setting-editor">
    {row.type==='STRING'?<textarea maxLength={300} rows={3} value={drafts[row.key]??''} onChange={e=>setDrafts(v=>({...v,[row.key]:e.target.value}))}/>:row.type==='CURRENCY'?<input aria-label={labels[row.key]} maxLength={3} pattern="[A-Z]{3}" value={drafts[row.key]??''} onChange={e=>setDrafts(v=>({...v,[row.key]:e.target.value.toUpperCase()}))}/>:row.type==='TIME_ZONE'?<select aria-label={labels[row.key]} value={drafts[row.key]??''} onChange={e=>setDrafts(v=>({...v,[row.key]:e.target.value}))}>{['Asia/Kolkata','UTC','Asia/Dubai','Europe/London','America/New_York','America/Los_Angeles'].map(x=><option key={x}>{x}</option>)}</select>:row.type==='LANGUAGE'?<select aria-label={labels[row.key]} value={drafts[row.key]??''} onChange={e=>setDrafts(v=>({...v,[row.key]:e.target.value}))}>{['en-IN','hi-IN','en-US'].map(x=><option key={x}>{x}</option>)}</select>:<input aria-label={labels[row.key]} type={row.type==='EMAIL'?'email':'tel'} maxLength={row.type==='EMAIL'?254:40} value={drafts[row.key]??''} onChange={e=>setDrafts(v=>({...v,[row.key]:e.target.value}))}/>}
    <button type="button" disabled={saving===row.key||drafts[row.key]===row.value} onClick={()=>void save(row)}>{saving===row.key?'Saving…':'Save'}</button>
   </div>
   {row.updatedAt&&<small>Last updated {new Date(row.updatedAt).toLocaleString()}</small>}
  </article>)}</div>
  <p className="muted">Platform-wide configuration applies only as a default. Tenant-specific branding, invoice prefixes, tax and discount policies remain managed in their existing tenant settings.</p>
 </section>;
}
