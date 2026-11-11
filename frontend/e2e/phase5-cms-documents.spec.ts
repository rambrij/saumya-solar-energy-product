import { test, expect, request } from '@playwright/test';

async function login(api:any,baseURL:string,email:string,password:string){
  const response=await api.post(baseURL+'/api/v1/auth/login',{data:{email,password}});
  expect(response.ok()).toBeTruthy();
  return response.json();
}

test('published CMS page and blog render publicly and uploaded media is served',async({page,baseURL})=>{
  test.setTimeout(90_000);
  if(!baseURL)throw new Error('E2E_BASE_URL is required');

  // Keep API setup authentication in its own cookie jar. The login endpoint
  // issues HttpOnly cookies, and page.request shares those cookies with the
  // browser context. Sharing them made the public route render Vendor Workspace.
  const api=await request.newContext({baseURL});
  try {
    const context=await api.get('/api/v1/public/store-context').then(r=>r.json());
    const storeSlug=context?.slug||'saumya-solar';
    const vendor=await login(api,baseURL,'admin@saumya.local','Admin@123');
    const headers={Authorization:'Bearer '+vendor.accessToken,'Content-Type':'application/json'};
    const suffix=Date.now().toString();
    const slug='phase5-e2e-'+suffix;
    const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC','base64');
    const media=await api.post('/api/v1/cms/media',{headers:{Authorization:'Bearer '+vendor.accessToken},multipart:{file:{name:'phase5.png',mimeType:'image/png',buffer:png}}});
    expect(media.ok()).toBeTruthy();
    const image=await media.json();
    expect(image.imageUrl).toContain('/uploads/');
    const imageResponse=await api.get(new URL(image.imageUrl,baseURL).toString());
    expect(imageResponse.ok()).toBeTruthy();

    for(const type of ['PAGE','BLOG']){
      const itemSlug=slug+(type==='BLOG'?'-blog':'-page');
      const create=await api.post('/api/v1/cms/content',{headers,data:{type,title:'Phase 5 '+type+' '+suffix,slug:itemSlug,body:'Published CMS content visible to public visitors.',seoTitle:'Phase 5 public content',seoDescription:'Public CMS rendering test',socialImageUrl:image.imageUrl}});
      expect(create.ok()).toBeTruthy();
      const content=await create.json();
      for(const status of ['REVIEW','PUBLISHED']){
        const transition=await api.post('/api/v1/cms/content/'+content.content.id+'/transition',{headers,data:{status}});
        expect(transition.ok()).toBeTruthy();
      }
      const publicResponse=await api.get('/api/v1/public/'+storeSlug+'/content/'+type+'/'+itemSlug);
      expect(publicResponse.ok()).toBeTruthy();
      const publicBody=await publicResponse.json();
      expect(publicBody.body).toContain('Published CMS content');

      await page.goto(new URL(type==='BLOG'?'/blog/'+itemSlug:'/pages/'+itemSlug,baseURL).toString());
      await expect(page.getByText('Vendor Workspace',{exact:true})).toHaveCount(0);
      await expect(page.getByText('Published CMS content visible to public visitors.',{exact:true})).toBeVisible();
      await expect(page.locator('img[src*="/uploads/"]')).toBeVisible();
    }
  } finally {
    await api.dispose();
  }
});

test('customer document downloads are restricted to the owning customer',async({baseURL})=>{
  test.setTimeout(60_000);
  if(!baseURL)throw new Error('E2E_BASE_URL is required');
  const api=await request.newContext({baseURL});
  try {
    const owner=await login(api,baseURL,'customer@saumya.local','Customer@123');
    const invoiceResponse=await api.get('/api/v1/invoices',{headers:{Authorization:'Bearer '+owner.accessToken}});
    expect(invoiceResponse.ok()).toBeTruthy();
    const invoices=await invoiceResponse.json();
    expect(invoices.length,'seeded customer must have an invoice fixture').toBeGreaterThan(0);
    const invoiceId=invoices[0].id;
    const suffix=Date.now();
    const registration=await api.post('/api/v1/auth/register',{data:{storeSlug:'saumya-solar',name:'Phase 5 foreign customer',email:'phase5-owner-'+suffix+'@example.test',password:'Phase5Customer-Password-2026'}});
    expect(registration.ok()).toBeTruthy();
    const other=await registration.json();
    const foreignList=await api.get('/api/v1/invoices',{headers:{Authorization:'Bearer '+other.accessToken}});
    expect(foreignList.ok()).toBeTruthy();
    const rows=await foreignList.json();
    expect(rows.some((row:any)=>row.id===invoiceId)).toBe(false);
    const download=await api.get('/api/v1/invoices/'+invoiceId+'/pdf',{headers:{Authorization:'Bearer '+other.accessToken}});
    expect([403,404]).toContain(download.status());
  } finally {
    await api.dispose();
  }
});
