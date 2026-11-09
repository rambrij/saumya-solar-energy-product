import {describe,expect,it} from 'vitest';
import {getNavigationKeys,getRoleNavigation} from '../src/RoleNavigation';

describe('role navigation configuration',()=>{
 it('keeps platform subscriptions and analytics in separate sections',()=>{
  const groups=getRoleNavigation('platform');
  expect(groups.find(g=>g.label==='Subscriptions')?.items.map(i=>i.key)).toContain('subscriptions');
  expect(groups.find(g=>g.label==='Insights')?.items.map(i=>i.key)).toContain('analytics');
 });
 it('filters vendor modules by the existing permission codes',()=>{
  const keys=getNavigationKeys('vendor',['CATALOG_MANAGE','BRANCH_MANAGE','SUBSCRIPTION_VIEW']);
  expect(keys).toContain('products');
  expect(keys).toContain('branches');
  expect(keys).toContain('analytics');
  expect(keys).toContain('subscription');
  expect(keys).not.toContain('users');
  expect(keys).not.toContain('finance-service');
 });
 it('does not expose vendor-only synchronization to customer navigation',()=>{
  const keys=getNavigationKeys('customer');
  expect(keys).toContain('customer-orders');
  expect(keys).toContain('invoices');
  expect(keys).not.toContain('offline-sync');
  expect(keys).not.toContain('settings');
 });
});
