const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\\/+$/,'');
export type AuthSession={accessToken:string;refreshToken?:string;user:{id:string;tenantId?:string;name:string;email:string;accountType:string;roles:string[];permissions:string[]}};
let refreshInFlight:Promise<AuthSession>|null=null;

export function sessionForToken(token:string):AuthSession {
 const stored=JSON.parse(localStorage.getItem('session')||'{}');
 return {...stored,accessToken:token} as AuthSession;
}

export async function authenticatedFetch(url:string,init:RequestInit={},session?:AuthSession|null):Promise<Response>{
 let active:AuthSession|null=session||null;
 if(session){try{active=JSON.parse(localStorage.getItem('session')||'null')||session}catch{active=session}}
 const request=(current:AuthSession|null)=>fetch(url,{...init,headers:{...(current?{Authorization:'Bearer '+current.accessToken}:{}),...(init.headers||{})}});
 let response=await request(active);
 if(response.status===401&&active?.refreshToken&&!url.includes('/auth/')){
  try{
   const latest=JSON.parse(localStorage.getItem('session')||'null') as AuthSession|null;
   if(latest?.refreshToken&&latest.refreshToken!==active.refreshToken) active=latest;
   else{
    if(!refreshInFlight){
     refreshInFlight=(async()=>{
      const refreshed=await fetch(API_BASE+'/api/v1/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:active!.refreshToken})});
      if(!refreshed.ok)throw new Error('session');
      const rotated=await refreshed.json();const next={...active!,...rotated} as AuthSession;
      localStorage.setItem('session',JSON.stringify(next));return next;
     })();
    }
    try{active=await refreshInFlight}finally{refreshInFlight=null}
   }
   response=await request(active);
  }catch{
   localStorage.removeItem('session');window.dispatchEvent(new CustomEvent('saumya:session-expired'));
   throw new Error('Your session has expired. Please sign in again.');
  }
 }
 return response;
}
