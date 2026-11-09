import React from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import EnterpriseSaasPanel from '../src/EnterpriseSaasPanel';

const plans=[{id:'plan-starter',planCode:'STARTER',name:'Starter',description:'Small teams',billingCycle:'MONTHLY',price:999,currency:'INR',maxUsers:5,maxBranches:1,maxWarehouses:1,maxStorageMb:512,maxProducts:100,maxCustomers:100,trialPeriodDays:14,displayOrder:1,entitlementsJson:'{"branches":false}',active:true},
{id:'plan-pro',planCode:'PROFESSIONAL',name:'Professional',description:'Growing teams',billingCycle:'MONTHLY',price:2999,currency:'INR',maxUsers:25,maxBranches:3,maxWarehouses:3,maxStorageMb:5120,maxProducts:1000,maxCustomers:1000,trialPeriodDays:14,displayOrder:2,entitlementsJson:'{"branches":true}',active:true}];
const session:any={accessToken:'test-token',user:{permissions:['SUBSCRIPTION_MANAGE']}};
const ok=(data:any)=>({ok:true,status:200,json:async()=>data});
describe('Platform subscription administration',()=>{
 beforeEach(()=>{vi.stubGlobal('fetch',vi.fn(async (url:string)=>String(url).includes('/platform/subscription-plans')?ok(plans):ok([])));});
 afterEach(()=>{cleanup();vi.unstubAllGlobals();});
 it('loads plans and filters the plan list by name or code',async()=>{
  render(<EnterpriseSaasPanel mode="platform" s={session} error={vi.fn()}/>);
  expect(await screen.findByText('Starter')).toBeTruthy();
  expect(screen.getByText('Professional')).toBeTruthy();
  fireEvent.change(screen.getByPlaceholderText('Search by plan name or code'),{target:{value:'starter'}});
  expect(screen.getByText('Starter')).toBeTruthy();
  expect(screen.queryByText('Professional')).toBeNull();
 });
 it('opens the create plan form only after clicking the action',async()=>{
  render(<EnterpriseSaasPanel mode="platform" s={session} error={vi.fn()}/>);
  await screen.findByText('Starter');
  expect(screen.queryByRole('dialog',{name:/subscription plan/i})).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:'Create Subscription Plan'}));
  expect(await screen.findByRole('dialog')).toBeTruthy();
  expect(screen.getByText('Included features')).toBeTruthy();
  expect(screen.queryByLabelText('Included feature entitlements (JSON)')).toBeNull();
 });

 it('searches tenants and loads assignment history after selection',async()=>{
  const fetchMock=vi.mocked(fetch);
  fetchMock.mockImplementation(async (url:any)=>{
   const path=String(url);
   if(path.includes('/platform/subscription-plans'))return ok(plans) as any;
   if(path.includes('/platform/tenants/search'))return ok([{id:'tenant-1',name:'Saumya Solar',slug:'saumya-solar',status:'ACTIVE'}]) as any;
   return ok([]) as any;
  });
  render(<EnterpriseSaasPanel mode="platform" section="assignments" s={session} error={vi.fn()}/>);
  fireEvent.change(screen.getByPlaceholderText('Type at least 2 characters…'),{target:{value:'sa'}});
  fireEvent.click(await screen.findByRole('button',{name:/Saumya Solar/}));
  expect(await screen.findByText('Assignment history')).toBeTruthy();
  await waitFor(()=>expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/platform/tenants/tenant-1/subscription-history'),expect.any(Object)));
 });
});
