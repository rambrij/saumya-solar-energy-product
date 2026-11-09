import React from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {cleanup,render,screen,fireEvent,waitFor} from '@testing-library/react';
import PlatformAnalyticsPanel from '../src/PlatformAnalyticsPanel';

const usage={items:[{tenantId:'tenant-1',tenantName:'Saumya Solar',tenantSlug:'saumya-solar',tenantStatus:'ACTIVE',planName:'Professional',subscriptionStatus:'ACTIVE',users:{used:4,limit:10},products:{used:12,limit:100},branches:{used:2,limit:3},warehouses:{used:1,limit:2},storageMb:{used:25,limit:512}}],page:0,size:100,total:1,totalPages:1};
describe('Platform analytics page',()=>{
 beforeEach(()=>vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,status:200,json:async()=>usage}))));
 afterEach(()=>{cleanup();vi.unstubAllGlobals()});
 it('loads tenant usage from the platform analytics endpoint, not subscription plans',async()=>{
  render(<PlatformAnalyticsPanel s={{accessToken:'test-token',user:{permissions:['SUBSCRIPTION_MANAGE']}}}/>);
  expect(await screen.findByText('Tenant resource usage')).toBeTruthy();
  expect(screen.getByText('Saumya Solar')).toBeTruthy();
  const calls=vi.mocked(fetch).mock.calls.map(call=>String(call[0]));
  expect(calls.some(url=>url.includes('/platform/analytics/tenant-usage'))).toBe(true);
  expect(calls.some(url=>url.includes('/platform/subscription-plans'))).toBe(false);
 });
 it('shows an empty state when there are no tenants',async()=>{
  vi.mocked(fetch).mockResolvedValue({ok:true,status:200,json:async()=>({...usage,items:[],total:0,totalPages:0})} as any);
  render(<PlatformAnalyticsPanel s={{accessToken:'test-token',user:{permissions:['SUBSCRIPTION_MANAGE']}}}/>);
  expect(await screen.findByText('No tenant usage data')).toBeTruthy();
 });
 it('shows a retryable error state',async()=>{
  vi.mocked(fetch).mockRejectedValueOnce(new Error('Network unavailable'));
  render(<PlatformAnalyticsPanel s={{accessToken:'test-token',user:{permissions:['SUBSCRIPTION_MANAGE']}}}/>);
  expect(await screen.findByText('Analytics could not be loaded')).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'Retry'}));
  await waitFor(()=>expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2));
 });
});
