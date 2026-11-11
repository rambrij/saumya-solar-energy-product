import {readFileSync} from 'node:fs';
import {describe,expect,it} from 'vitest';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';
const here=dirname(fileURLToPath(import.meta.url));
const customerPortal=readFileSync(resolve(here,'../src/main.tsx'),'utf8');
const publicHome=readFileSync(resolve(here,'../src/PublicHome.tsx'),'utf8');
describe('Customer catalogue and quotation copy regressions',()=>{
 it('keeps product categories in the available-products selector without a duplicate section',()=>{
  expect(customerPortal).toContain('Available products');
  expect(customerPortal).not.toContain('<h2>Product categories</h2>');
 });
 it('uses Our Products in customer and public navigation',()=>{
  expect(customerPortal).toContain('<PublicSiteHeader accountName');
  expect(customerPortal).not.toContain("{key:'catalog'");
  expect(publicHome).toContain('Our Products');
  expect(customerPortal).not.toContain('Solar Catalogue');
  expect(publicHome).not.toContain('Solar Catalogue');
 });
 it('uses the single Solar Quotation workflow label',()=>{
  expect(customerPortal).toContain('Request a Solar Quotation');
  expect(customerPortal).not.toContain('Request a Solar Quote');
 });
 it('shows checkout status from the authenticated payment list rather than URL parameters',()=>{
  expect(customerPortal).toContain("get('mockCheckoutPaymentId')");
  expect(customerPortal).toContain("payment.status==='PENDING'");
  expect(customerPortal).toContain('Authoritative status:');
  expect(customerPortal).toContain('Returning from checkout does not itself confirm a payment.');
  expect(customerPortal).toContain("api('/refunds',{},s)");
 });
});
