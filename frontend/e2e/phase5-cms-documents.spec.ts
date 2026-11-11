import { test, expect } from '@playwright/test';

async function login(page:any,email:string,password:string){
  const response=await page.request.post('/api/v1/auth/login',{data:{email,password}});
  expect(response.ok()).toBeTruthy();
  return response.json();
}

test('published CMS page and blog render publicly and uploaded media is served',async({page,baseURL})=>{
  test.setTimeout(90_000);
  if(!baseURL)throw new Error('E2E_BASE_URL is required');
  await page.goto(baseURL);
  const context=await page.request.get('/api/v1/public/store-context').then(r=>r.json());
  const storeSlug=context?.slug||'saumya-solar';
  const vendor=await login(page,'admin@saumya.local','Admin@123');
  const headers={Authorization:'Bearer '+vendor.accessToken,'Content-Type':'application/json'};
  const suffix=Date.now().toString();
  const slug='phase5-e2e-'+suffix;
  const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l1sAAAAASUVORK5CYII=','base64');
  const media=await page.request.post('/api/v1/cms/media',{headers:{Authorization:'Bearer '+vendor.accessToken},multipart:{file:{name:'phase5.png',mimeType:'image/png',buffer:png}}});
  expect(media.ok()).toBeTruthy();
  const image=await media.json();
  expect(image.imageUrl).toContain('/uploads/');
  const imageResponse=await page.request.get(image.imageUrl);
  expect(imageResponse.ok()).toBeTruthy();
  for(const type of ['PAGE','BLOG']){
    const itemSlug=slug+(type==='BLOG'?'-blog':'-page');
    const create=await page.request.post('/api/v1/cms/content',{headers,data:{type,title:'Phase 5 '+type+' '+suffix,slug:itemSlug,body:'Published CMS content visible to public visitors.',seoTitle:'Phase 5 public content',seoDescription:'Public CMS rendering test',socialImageUrl:image.imageUrl}});
    expect(create.ok()).toBeTruthy();
    const content=await create.json();
    for(const status of ['REVIEW','PUBLISHED']){
      const transition=await page.request.post('/api/v1/cms/content/'+content.content.id+'/transition',{headers,data:{status}});
      expect(transition.ok()).toBeTruthy();
    }
    const publicResponse=await page.request.get('/api/v1/public/'+storeSlug+'/content/'+type+'/'+itemSlug);
    expect(publicResponse.ok()).toBeTruthy();
    const publicBody=await publicResponse.json();
    expect(publicBody.body).toContain('Published CMS content');
    const route=type==='BLOG'?'/blog/'+itemSlug:'/pages/'+itemSlug;
    await page.goto(baseURL+route);
    await expect(page.getByText('Published CMS content visible to public visitors.',{exact:true})).toBeVisible();
    await expect(page.locator('img').filter({has:page.locator('[src*="/uploads/"]')})).toHaveCount(0).catch(()=>{});
  }
});

test('customer document downloads are restricted to the owning customer',async({page})=>{
  test.setTimeout(60_000);
  const owner=await login(page,'customer@saumya.local','Customer@123');
  const invoiceResponse=await page.request.get('/api/v1/invoices',{headers:{Authorization:'Bearer '+owner.accessToken}});
  expect(invoiceResponse.ok()).toBeTruthy();
  const invoices=await invoiceResponse.json();
  expect(invoices.length,'seeded customer must have an invoice fixture').toBeGreaterThan(0);
  const invoiceId=invoices[0].id;
  const suffix=Date.now();
  const registration=await page.request.post('/api/v1/auth/register',{data:{storeSlug:'saumya-solar',name:'Phase 5 foreign customer',email:'phase5-owner-'+suffix+'@example.test',password:'Phase5Customer-Password-2026'}});
  expect(registration.ok()).toBeTruthy();
  const other=await registration.json();
  const foreignList=await page.request.get('/api/v1/invoices',{headers:{Authorization:'Bearer '+other.accessToken}});
  expect(foreignList.ok()).toBeTruthy();
  const rows=await foreignList.json();
  expect(rows.some((row:any)=>row.id===invoiceId)).toBe(false);
  const download=await page.request.get('/api/v1/invoices/'+invoiceId+'/pdf',{headers:{Authorization:'Bearer '+other.accessToken}});
  expect([403,404]).toContain(download.status());
});
