import React,{useState}from'react';
const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
export default function PackageImageUpload({packageId,token,onUploaded,onError}:any){
 const[file,setFile]=useState<File|null>(null);const[busy,setBusy]=useState(false);
 const upload=async()=>{if(!file)return;setBusy(true);try{const form=new FormData();form.append('file',file);const response=await fetch(API_BASE+'/api/v1/packages/'+packageId+'/image',{method:'POST',headers:{Authorization:'Bearer '+token},body:form});const body=await response.json().catch(()=>({}));if(!response.ok)throw new Error(body.detail||body.message||'Package image upload failed');setFile(null);onUploaded?.(body)}catch(e:any){onError(e.message)}finally{setBusy(false)}};
 return <div className="package-image-upload"><label>Package image (JPEG, PNG or WebP, max 5 MB)<input type="file" accept="image/jpeg,image/png,image/webp" onChange={e=>setFile(e.target.files?.[0]||null)}/></label><button type="button" disabled={!file||busy} onClick={upload}>{busy?'Uploading…':'Upload image'}</button></div>;
}
