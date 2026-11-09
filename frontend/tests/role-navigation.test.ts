import {describe,expect,it} from 'vitest';
import {getNavigationKeys,getRoleNavigation} from '../src/RoleNavigation';

describe('role navigation configuration',()=>{
 it('renders platform dashboard as one direct link and keeps subscription children grouped',()=>{
  const groups=getRoleNavigation('platform',['SUBSCRIPTION_MANAGE','AUDIT_VIEW','PLATFORM_MANAGE']);
  expect(groups.find(g=>g.label==='Dashboard')?.singleLink).toBe(true);
  expect(groups.find(g=>g.label==='Dashboard')?.items.map(i=>i.key)).toEqual(['dashboard']);
  expect(groups.find(g=>g.label==='Subscriptions')?.items.map(i=>i.key)).toEqual(['subscriptions','assignments']);
  expect(groups.find(g=>g.label==='Analytics')?.items.map(i=>i.key)).toEqual(['analytics']);
  expect(groups.find(g=>g.label==='Audit')?.items.map(i=>i.key)).toEqual(['audit']);
  expect(groups.find(g=>g.label==='Tenants')?.items.map(i=>i.key)).toEqual(['tenants']);
  expect(groups.find(g=>g.label==='Settings')?.items.map(i=>i.key)).toEqual(['settings']);
 });
 it('shows vendor admin analytics by role even when a pre-migration session lacks permission claims',()=>{
  expect(getNavigationKeys('vendor',[],['VENDOR_ADMIN'])).toContain('analytics');
  expect(getNavigationKeys('vendor',['REPORT_VIEW'],['VENDOR_ADMIN'])).toContain('analytics');
  expect(getNavigationKeys('vendor',['BRANCH_MANAGE'],['VENDOR_ADMIN'])).toContain('analytics');
  expect(getNavigationKeys('vendor',['REPORT_VIEW','BRANCH_MANAGE'],['SALES_MANAGER'])).not.toContain('analytics');
 });
 it('shows vendor account subscription and settings to vendor admin only',()=>{
  const keys=getNavigationKeys('vendor',['DISCOUNT_POLICY_MANAGE'],['VENDOR_ADMIN']);
  expect(keys).toContain('subscription');
  expect(keys).toContain('settings');
  expect(getNavigationKeys('vendor',['DISCOUNT_POLICY_MANAGE'],['SALES_MANAGER'])).not.toContain('subscription');
 });
 it('shows customer account links including quotations and logout without a duplicate dashboard group',()=>{
  const groups=getRoleNavigation('customer');
  const keys=getNavigationKeys('customer');
  expect(groups.find(g=>g.label==='Dashboard')?.singleLink).toBe(true);
  expect(groups.find(g=>g.label==='My Account')?.items.map(i=>i.key)).toEqual(['profile','customer-orders','invoices','quotations','enquiries','addresses','settings','logout']);
  expect(keys).toContain('dashboard');
  expect(keys).not.toContain('catalog');
  expect(keys).not.toContain('offline-sync');
 });
});
