const API_BASE=(import.meta.env.VITE_API_BASE_URL||'').replace(/\/+$/,'');
export type AuthSession={accessToken:string;csrfToken?:string;user:{id:string;tenantId?:string;name:string;email:string;accountType:string;roles:string[];permissions:string[]}};
let currentSession:AuthSession|null=null;
let refreshInFlight:Promise<AuthSession>|null=null;

export function setCurrentAuthSession(session:AuthSession|null):void {
 currentSession=session;
 if(session?.csrfToken)sessionStorage.setItem('saumya-csrf-token',session.csrfToken);
 if(!session){sessionStorage.removeItem('saumya-csrf-token');}
}

export function sessionForToken(token:string):AuthSession {
 if(!currentSession)throw new Error('Authenticated session is not initialized');
 return {...currentSession,accessToken:token};
}

function csrfHeaders(headers:Headers):Headers {
 const csrf=sessionStorage.getItem('saumya-csrf-token');
 if(csrf)headers.set('X-CSRF-Token',csrf);
 return headers;
}

async function rotateSession():Promise<AuthSession>{
 const headers=csrfHeaders(new Headers({'Accept':'application/json'}));
 const response=await fetch(API_BASE+'/api/v1/auth/refresh',{method:'POST',headers,credentials:'include'});
 if(!response.ok)throw new Error('session');
 const next=await response.json() as AuthSession;
 if(!next.accessToken||!next.user)throw new Error('invalid refresh response');
 setCurrentAuthSession(next);
 return next;
}

export async function bootstrapSession():Promise<AuthSession|null>{
 if(!sessionStorage.getItem('saumya-csrf-token'))return null;
 try{
  if(!refreshInFlight)refreshInFlight=rotateSession();
  try{return await refreshInFlight}catch{setCurrentAuthSession(null);return null}finally{refreshInFlight=null}
 }catch{return null}
}

export async function authenticatedFetch(url:string,init:RequestInit={},session?:AuthSession|null):Promise<Response>{
 let active:AuthSession|null=currentSession||session||null;
 const request=(current:AuthSession|null)=>{
  const headers=new Headers(init.headers||{});
  if(current)headers.set('Authorization','Bearer '+current.accessToken);
  if(url.includes('/auth/logout'))csrfHeaders(headers);
  return fetch(url,{...init,headers,credentials:'include'});
 };
 let response=await request(active);
 if(response.status===401&&active&&!url.includes('/auth/')){
  try{
   if(!refreshInFlight)refreshInFlight=rotateSession();
   try{active=await refreshInFlight}finally{refreshInFlight=null}
   response=await request(active);
  }catch{
   setCurrentAuthSession(null);
   window.dispatchEvent(new CustomEvent('saumya:session-expired'));
   throw new Error('Your session has expired. Please sign in again.');
  }
 }
 return response;
}
