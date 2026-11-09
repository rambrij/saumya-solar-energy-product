import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {authenticatedFetch,bootstrapSession,setCurrentAuthSession,type AuthSession} from '../src/authenticatedFetch';

const session:AuthSession={accessToken:'expired-access',csrfToken:'csrf-old',user:{id:'u1',name:'Test',email:'test@example.test',accountType:'VENDOR',roles:['VENDOR_ADMIN'],permissions:[],tenantId:'t1'}};

beforeEach(()=>{sessionStorage.clear();setCurrentAuthSession(null)});
afterEach(()=>{sessionStorage.clear();setCurrentAuthSession(null);vi.unstubAllGlobals();vi.restoreAllMocks()});

describe('HttpOnly-cookie authenticatedFetch',()=>{
 it('rotates the HttpOnly cookie session after 401 and retries the protected request',async()=>{
  setCurrentAuthSession(session);
  const fetchMock=vi.fn()
   .mockResolvedValueOnce(new Response('',{status:401}))
   .mockResolvedValueOnce(new Response(JSON.stringify({...session,accessToken:'access-new',csrfToken:'csrf-new'}),{status:200,headers:{'Content-Type':'application/json'}}))
   .mockResolvedValueOnce(new Response('ok',{status:200}));
  vi.stubGlobal('fetch',fetchMock);
  const response=await authenticatedFetch('/api/v1/products',{},session);
  expect(response.status).toBe(200);
  expect(fetchMock).toHaveBeenCalledTimes(3);
  expect(fetchMock.mock.calls[1][1]?.credentials).toBe('include');
  expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('X-CSRF-Token')).toBe('csrf-old');
  expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get('Authorization')).toBe('Bearer access-new');
  expect(sessionStorage.getItem('saumya-csrf-token')).toBe('csrf-new');
 });

 it('shares one cookie refresh operation for concurrent expired requests',async()=>{
  setCurrentAuthSession(session);
  let refreshCalls=0;
  const fetchMock=vi.fn(async(url:string,init?:RequestInit)=>{
   if(url.includes('/auth/refresh')){
    refreshCalls++;
    await Promise.resolve();
    return new Response(JSON.stringify({...session,accessToken:'access-new',csrfToken:'csrf-new'}),{status:200,headers:{'Content-Type':'application/json'}});
   }
   const auth=new Headers(init?.headers).get('Authorization');
   return auth==='Bearer access-new'?new Response('ok',{status:200}):new Response('',{status:401});
  });
  vi.stubGlobal('fetch',fetchMock);
  const [first,second]=await Promise.all([
   authenticatedFetch('/api/v1/products',{},session),
   authenticatedFetch('/api/v1/orders',{},session)
  ]);
  expect(first.status).toBe(200);
  expect(second.status).toBe(200);
  expect(refreshCalls).toBe(1);
 });

 it('bootstraps a new in-memory session after page reload using cookie and CSRF token',async()=>{
  sessionStorage.setItem('saumya-csrf-token','csrf-persisted');
  const fetchMock=vi.fn().mockResolvedValue(new Response(JSON.stringify({...session,accessToken:'bootstrapped'}),{status:200,headers:{'Content-Type':'application/json'}}));
  vi.stubGlobal('fetch',fetchMock);
  const result=await bootstrapSession();
  expect(result?.accessToken).toBe('bootstrapped');
  expect(fetchMock.mock.calls[0][1]?.credentials).toBe('include');
  expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('X-CSRF-Token')).toBe('csrf-persisted');
 });

 it('clears the in-memory session when refresh is rejected',async()=>{
  setCurrentAuthSession(session);
  const expired=vi.fn();
  window.addEventListener('saumya:session-expired',expired);
  const fetchMock=vi.fn()
   .mockResolvedValueOnce(new Response('',{status:401}))
   .mockResolvedValueOnce(new Response('',{status:401}));
  vi.stubGlobal('fetch',fetchMock);
  await expect(authenticatedFetch('/api/v1/products',{},session)).rejects.toThrow('session has expired');
  expect(sessionStorage.getItem('saumya-csrf-token')).toBeNull();
  expect(expired).toHaveBeenCalledTimes(1);
  window.removeEventListener('saumya:session-expired',expired);
 });
});
