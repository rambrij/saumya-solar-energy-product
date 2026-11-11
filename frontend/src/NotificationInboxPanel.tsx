import React,{useCallback,useEffect,useState} from 'react';
import {authenticatedFetch,type AuthSession} from './authenticatedFetch';
const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
export default function NotificationInboxPanel({s,error}:{s:{accessToken:string};error:(message:string)=>void}){
 const [rows,setRows]=useState<any[]>([]),[count,setCount]=useState(0),[totalPages,setTotalPages]=useState(0),[page,setPage]=useState(0),[loading,setLoading]=useState(true),[unreadOnly,setUnreadOnly]=useState(false);
 const request=useCallback(async(path:string,init:RequestInit={})=>{const r=await authenticatedFetch(API_BASE+'/api/v1'+path,init,s as AuthSession);const b=await r.json().catch(()=>({}));if(!r.ok)throw Error(b.detail||b.message||'Notification request failed');return b},[s.accessToken]);
 const load=useCallback(async()=>{setLoading(true);try{const [pageData,unread]=await Promise.all([request('/notifications?page='+page+'&size=50&unreadOnly='+unreadOnly),request('/notifications/unread-count')]);setRows(pageData.content||[]);setTotalPages(Number(pageData.totalPages||0));setCount(Number(unread.count||0))}catch(e:any){error(e.message)}finally{setLoading(false)}},[request,unreadOnly,error,page]);
 useEffect(()=>{setPage(0)},[unreadOnly]);
 useEffect(()=>{void load()},[load]);
 const markRead=async(id:string)=>{try{await request('/notifications/'+id+'/read',{method:'PATCH'});await load()}catch(e:any){error(e.message)}};
 const markAll=async()=>{try{await request('/notifications/read-all',{method:'POST'});await load()}catch(e:any){error(e.message)}};
 return <section className="vendor-module-page notification-inbox-panel"><div className="management-heading"><div><p className="eyebrow">INBOX</p><h1>Notifications</h1><p className="muted">{count} unread notification{count===1?'':'s'}</p></div><div className="actions"><button type="button" className="secondary" onClick={()=>void load()}>Refresh</button><button type="button" disabled={!count} onClick={()=>void markAll()}>Mark all read</button></div></div>
 <div className="card"><label className="check-row"><input type="checkbox" checked={unreadOnly} onChange={e=>setUnreadOnly(e.target.checked)}/> Show unread only</label>
 {loading?<p>Loading notifications…</p>:!rows.length?<div className="empty-state"><h3>No notifications</h3><p>New business updates will appear here.</p></div>:rows.map(row=><article className="notification-row" key={row.id}><div><b>{row.title}</b><small>{row.eventType.replaceAll('_',' ')} · {new Date(row.createdAt).toLocaleString()}</small><p>{row.body}</p></div><div>{row.readAt?<span className="status-pill">Read</span>:<button type="button" className="secondary" onClick={()=>void markRead(row.id)}>Mark read</button>}</div></article>)}
 {totalPages>1&&<div className="platform-pagination"><span>Page {page+1} of {totalPages}</span><div className="actions"><button type="button" className="secondary" disabled={loading||page===0} onClick={()=>setPage(v=>v-1)}>Previous</button><button type="button" className="secondary" disabled={loading||page+1>=totalPages} onClick={()=>setPage(v=>v+1)}>Next</button></div></div>}
 </div></section>;
}
