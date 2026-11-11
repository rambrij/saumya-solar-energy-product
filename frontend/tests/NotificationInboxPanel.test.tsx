import React from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import NotificationInboxPanel from '../src/NotificationInboxPanel';

describe('in-app notification inbox',()=>{
 beforeEach(()=>vi.stubGlobal('fetch',vi.fn(async(input:any,init:any={})=>{
  const url=String(input);
  if(url.includes('/notifications/unread-count'))return {ok:true,status:200,json:async()=>({count:1})};
  if(url.includes('/notifications/read-all'))return {ok:true,status:200,json:async()=>({updated:1})};
  if(url.includes('/notifications/n-1/read'))return {ok:true,status:200,json:async()=>({id:'n-1',readAt:'2026-11-11T10:00:00Z'})};
  return {ok:true,status:200,json:async()=>({content:[{id:'n-1',title:'Order confirmed',body:'Your order is confirmed',eventType:'ORDER_CONFIRMED',createdAt:'2026-11-11T10:00:00Z',readAt:null}]})};
 })));
 afterEach(()=>{cleanup();vi.unstubAllGlobals()});
 it('shows user inbox entries and unread count',async()=>{
  render(<NotificationInboxPanel s={{accessToken:'token'}} error={vi.fn()}/>);
  expect(await screen.findByText('Order confirmed')).toBeTruthy();
  expect(screen.getByText('1 unread notification')).toBeTruthy();
 });
 it('marks a single notification read through the self-scoped endpoint',async()=>{
  render(<NotificationInboxPanel s={{accessToken:'token'}} error={vi.fn()}/>);
  fireEvent.click(await screen.findByRole('button',{name:'Mark read'}));
  await waitFor(()=>expect(vi.mocked(fetch).mock.calls.some(call=>String(call[0]).includes('/notifications/n-1/read')&&call[1]?.method==='PATCH')).toBe(true));
 });
 it('records explicit opt-in before enabling SMS delivery',async()=>{
  render(<NotificationInboxPanel s={{accessToken:'token'}} error={vi.fn()}/>);
  const consent=(await screen.findAllByLabelText('I consent'))[0];
  fireEvent.click(consent);
  await waitFor(()=>expect(vi.mocked(fetch).mock.calls.some(call=>{
   const url=String(call[0]),init=call[1]||{};
   return url.includes('/notifications/preferences')&&init.method==='PUT'&&String(init.body).includes('"channel":"SMS"')&&String(init.body).includes('"consented":true');
  })).toBe(true));
 });

});
