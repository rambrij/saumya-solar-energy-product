import {afterEach,describe,expect,it,vi} from 'vitest';
import {authenticatedFetch,type AuthSession} from '../src/authenticatedFetch';

const session:AuthSession={accessToken:'expired-access',refreshToken:'refresh-old',user:{id:'u1',name:'Test',email:'test@example.test',accountType:'VENDOR',roles:['VENDOR_ADMIN'],permissions:[],tenantId:'t1'}};

afterEach(()=>{localStorage.clear();vi.unstubAllGlobals();vi.restoreAllMocks()});

describe('authenticatedFetch',()=>{
 it('rotates a refresh token once after 401 and retries the protected request',async()=>{
  localStorage.setItem('session',JSON.stringify(session));
  const fetchMock=vi.fn()
   .mockResolvedValueOnce(new Response('',{status:401}))
   .mockResolvedValueOnce(new Response(JSON.stringify({accessToken:'access-new',refreshToken:'refresh-new'}),{status:200,headers:{'Content-Type':'application/json'}}))
   .mockResolvedValueOnce(new Response('ok',{status:200}));
  vi.stubGlobal('fetch',fetchMock);
  const response=await authenticatedFetch('/api/v1/products',{},session);
  expect(response.status).toBe(200);
  expect(fetchMock).toHaveBeenCalledTimes(3);
  expect(fetchMock.mock.calls[2][1]?.headers).toBeInstanceOf(Headers);
  expect((fetchMock.mock.calls[2][1]?.headers as Headers).get('Authorization')).toBe('Bearer access-new');
  expect(JSON.parse(localStorage.getItem('session')||'{}').refreshToken).toBe('refresh-new');
 });

 it('shares one refresh operation for concurrent expired requests',async()=>{
  localStorage.setItem('session',JSON.stringify(session));
  let refreshCalls=0;
  const fetchMock=vi.fn(async(url:string,init?:RequestInit)=>{
   if(url.includes('/auth/refresh')){
    refreshCalls++;
    await Promise.resolve();
    return new Response(JSON.stringify({accessToken:'access-new',refreshToken:'refresh-new'}),{status:200,headers:{'Content-Type':'application/json'}});
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

 it('clears the invalid session when refresh is rejected',async()=>{
  localStorage.setItem('session',JSON.stringify(session));
  const expired=vi.fn();
  window.addEventListener('saumya:session-expired',expired);
  const fetchMock=vi.fn()
   .mockResolvedValueOnce(new Response('',{status:401}))
   .mockResolvedValueOnce(new Response('',{status:401}));
  vi.stubGlobal('fetch',fetchMock);
  await expect(authenticatedFetch('/api/v1/products',{},session)).rejects.toThrow('session has expired');
  expect(localStorage.getItem('session')).toBeNull();
  expect(expired).toHaveBeenCalledTimes(1);
  window.removeEventListener('saumya:session-expired',expired);
 });
});
