import React,{useCallback,useEffect,useState} from 'react';
import {authenticatedFetch,sessionForToken,type AuthSession} from './authenticatedFetch';
const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
type Props={s:{accessToken:string;user:{permissions:string[]}};error:(message:string)=>void};
export default function CmsManagementPanel({s,error}:Props){
 const [rows,setRows]=useState<any[]>([]),[selected,setSelected]=useState<any>(null),[revision,setRevision]=useState<any>(null);
 const [loading,setLoading]=useState(true),[saving,setSaving]=useState(false),[filter,setFilter]=useState('ALL');
 const [form,setForm]=useState<any>({type:'BLOG',title:'',slug:'',body:'',seoTitle:'',seoDescription:'',socialImageUrl:''});
 const canCreate=s.user.permissions.includes('CMS_CREATE'),canUpdate=s.user.permissions.includes('CMS_UPDATE'),canPublish=s.user.permissions.includes('CMS_PUBLISH');
 const request=useCallback(async(path:string,init:RequestInit={})=>{
  const response=await authenticatedFetch(API_BASE+'/api/v1'+path,init,s as AuthSession);
  const body=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(body.detail||body.message||'CMS request failed ('+response.status+')');
  return body;
 },[s.accessToken]);
 const load=useCallback(async()=>{
  setLoading(true);
  try{const params=new URLSearchParams({page:'0',size:'100'});if(filter!=='ALL')params.set('type',filter);const data=await request('/cms/content?'+params.toString());setRows(data.content||[]);}
  catch(e:any){error(e.message||'Unable to load CMS content')}
  finally{setLoading(false)}
 },[request,filter,error]);
 useEffect(()=>{void load()},[load]);
 const reset=()=>{setSelected(null);setRevision(null);setForm({type:'BLOG',title:'',slug:'',body:'',seoTitle:'',seoDescription:'',socialImageUrl:''})};
 const edit=async(row:any)=>{try{const data=await request('/cms/content/'+row.id);setSelected(data.content);setRevision(data.versions||[]);const r=data.revision||{};setForm({type:data.content.type,title:r.title||data.content.title,slug:data.content.slug,body:r.body||'',seoTitle:r.seoTitle||'',seoDescription:r.seoDescription||'',socialImageUrl:r.socialImageUrl||''})}catch(e:any){error(e.message)}};
 const submit=async(e:React.FormEvent)=>{e.preventDefault();setSaving(true);try{const path=selected?'/cms/content/'+selected.id:'/cms/content';await request(path,{method:selected?'PATCH':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(form)});reset();await load();error('Content saved as a draft revision.')}catch(e:any){error(e.message)}finally{setSaving(false)}};
 const transition=async(row:any,status:string)=>{try{await request('/cms/content/'+row.id+'/transition',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({status})});await load();error('Content status changed to '+status+'.')}catch(e:any){error(e.message)}};
 const rollback=async(row:any,number:number)=>{try{await request('/cms/content/'+row.id+'/rollback',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({revisionNumber:number})});await load();await edit(row);error('A new draft revision was created from the selected version.')}catch(e:any){error(e.message)}};
 return <section className="vendor-module-page cms-management-panel">
  <div className="management-heading"><div><p className="eyebrow">WEBSITE & CONTENT</p><h1>CMS & Blog</h1><p className="muted">Create pages, banners, FAQs, testimonials and blog posts. Public pages show only the published revision.</p></div>{canCreate&&<button type="button" onClick={()=>{reset();setSelected({new:true})}}>＋ Create content</button>}</div>
  {selected&&<form className="card form" onSubmit={submit}><h2>{selected.id?'Edit content · new revision':'Create content'}</h2>
   <label>Content type<select value={form.type} onChange={e=>setForm({...form,type:e.target.value})}><option value="BLOG">Blog</option><option value="PAGE">Page</option><option value="BANNER">Banner</option><option value="FAQ">FAQ</option><option value="TESTIMONIAL">Testimonial</option></select></label>
   <label>Title<input maxLength={180} required value={form.title} onChange={e=>setForm({...form,title:e.target.value})}/></label>
   <label>Slug<input maxLength={180} pattern="[a-z0-9]+(-[a-z0-9]+)*" required value={form.slug} onChange={e=>setForm({...form,slug:e.target.value.toLowerCase().replace(/[^a-z0-9-]/g,'').replace(/-+/g,'-')})}/></label>
   <label>Content (plain text / Markdown; HTML is not accepted)<textarea rows={10} maxLength={100000} required value={form.body} onChange={e=>setForm({...form,body:e.target.value})}/></label>
   <label>SEO title<input maxLength={180} value={form.seoTitle} onChange={e=>setForm({...form,seoTitle:e.target.value})}/></label>
   <label>SEO description<textarea rows={2} maxLength={320} value={form.seoDescription} onChange={e=>setForm({...form,seoDescription:e.target.value})}/></label>
   <label>Social image URL<input maxLength={1024} type="url" placeholder="https://…" value={form.socialImageUrl} onChange={e=>setForm({...form,socialImageUrl:e.target.value})}/></label>
   <div className="form-actions"><button disabled={saving||(!selected.id&&!canCreate)||(selected.id&&!canUpdate)}>{saving?'Saving…':'Save draft revision'}</button><button type="button" className="secondary" onClick={reset}>Cancel</button></div>
   {Array.isArray(revision)&&revision.length>0&&<div><h3>Immutable revision history</h3>{revision.map((r:any)=><article key={r.id}><span>Revision {r.revisionNumber} · {new Date(r.createdAt).toLocaleString()}</span><button type="button" className="secondary" onClick={()=>void rollback(selected,r.revisionNumber)}>Restore as new draft</button></article>)}</div>}
  </form>}
  <div className="card management-list"><div className="table-toolbar"><div><h2>Content library</h2><span className="muted">{rows.length} items</span></div><select aria-label="Filter content type" value={filter} onChange={e=>setFilter(e.target.value)}><option value="ALL">All content</option>{['BLOG','PAGE','BANNER','FAQ','TESTIMONIAL'].map(x=><option key={x}>{x}</option>)}</select></div>
   {loading?<p>Loading content…</p>:!rows.length?<p>No content yet. Create a draft to get started.</p>:<div className="table-wrap"><table><thead><tr><th>Title</th><th>Type</th><th>Slug</th><th>Status</th><th>Updated</th><th>Actions</th></tr></thead><tbody>{rows.map(row=><tr key={row.id}><td>{row.title}</td><td>{row.type}</td><td>{row.slug}</td><td>{row.status}</td><td>{row.updatedAt?new Date(row.updatedAt).toLocaleString():'—'}</td><td><div className="actions"><button type="button" className="secondary" onClick={()=>void edit(row)}>Edit</button>{canUpdate&&row.status==='DRAFT'&&<button type="button" onClick={()=>void transition(row,'REVIEW')}>Submit review</button>}{canUpdate&&row.status==='REVIEW'&&<button type="button" className="secondary" onClick={()=>void transition(row,'DRAFT')}>Return to draft</button>}{canPublish&&row.status==='REVIEW'&&<button type="button" onClick={()=>void transition(row,'PUBLISHED')}>Publish</button>}{canPublish&&['PUBLISHED','REVIEW','DRAFT'].includes(row.status)&&<button type="button" className="danger" onClick={()=>void transition(row,'ARCHIVED')}>Archive</button>}</div></td></tr>)}</tbody></table></div>}
  </div>
 </section>;
}
