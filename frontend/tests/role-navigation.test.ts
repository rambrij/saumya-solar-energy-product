import {describe,expect,it} from 'vitest';
import {getNavigationKeys,getRoleNavigation} from '../src/RoleNavigation';

describe('role navigation configuration',()=>{
 it('keeps platform plans, assignments, analytics and audit in distinct groups',()=>{
  const groups=getRoleNavigation('platform',['SUBSCRIPTION_MANAGE','AUDIT_VIEW','PLATFORM_MANAGE']);
  expect(groups.find(g=>g.label==='Subscriptions')?.items.map(i=>i.key)).toEqual(['subscriptions','assignments']);
  expect(groups.find(g=>g.label==='Insights')?.items.map(i=>i.key)).toEqual(['analytics']);
  expect(groups.find(g=>g.label==='Governance')?.items.map(i=>i.key)).toEqual(['audit']);
 });
 it('exposes analytics only when vendor admin has both reporting and branch permissions',()=>{
  expect(getNavigationKeys('vendor',['REPORT_VIEW'])).not.toContain('analytics');
  expect(getNavigationKeys('vendor',['BRANCH_MANAGE'])).not.toContain('analytics');
  expect(getNavigationKeys('vendor',['REPORT_VIEW','BRANCH_MANAGE'],['SALES_MANAGER'])).not.toContain('analytics');
  expect(getNavigationKeys('vendor',['REPORT_VIEW','BRANCH_MANAGE'],['VENDOR_ADMIN'])).toContain('analytics');
 });
 it('shows only customer-owned account functions and logout',()=>{
  const groups=getRoleNavigation('customer');
  const keys=getNavigationKeys('customer');
  expect(groups.find(g=>g.label==='My Account')?.items.map(i=>i.key)).toEqual(['profile','customer-orders','invoices','enquiries','addresses','settings','logout']);
  expect(keys).toContain('dashboard');
  expect(keys).not.toContain('catalog');
  expect(keys).not.toContain('offline-sync');
 });
});
