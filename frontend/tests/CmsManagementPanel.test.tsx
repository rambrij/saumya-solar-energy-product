import React from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import CmsManagementPanel from '../src/CmsManagementPanel';

const draft={id:'cms-1',tenantId:'tenant-a',type:'BLOG',title:'Solar guide',slug:'solar-guide',status:'DRAFT',updatedAt:'2026-11-11T10:00:00Z'};
describe('CMS management panel',()=>{
 beforeEach(()=>vi.stubGlobal('fetch',vi.fn(async(input:any,init:any={})=>{
  const url=String(input);
  if(url.includes('/cms/content?'))return {ok:true,status:200,json:async()=>({content:[draft]})};
  if(url.endsWith('/cms/content/cms-1'))return {ok:true,status:200,json:async()=>({content:draft,revision:{title:draft.title,body:'Solar content'},versions:[]})};
  if(url.endsWith('/cms/content'))return {ok:true,status:201,json:async()=>({content:draft,revision:{},versions:[]})};
  if(url.includes('/transition'))return {ok:true,status:200,json:async()=>draft};
  return {ok:true,status:200,json:async()=>({})};
 })));
 afterEach(()=>{cleanup();vi.unstubAllGlobals()});
 it('lists tenant CMS content and exposes only authorized workflow actions',async()=>{
  render(<CmsManagementPanel s={{accessToken:'token',user:{permissions:['CMS_VIEW','CMS_UPDATE','CMS_PUBLISH']}}} error={vi.fn()}/>);
  expect(await screen.findByText('Solar guide')).toBeTruthy();
  expect(screen.getByRole('button',{name:'Submit review'})).toBeTruthy();
  expect(screen.getByRole('button',{name:'Archive'})).toBeTruthy();
 });
 it('creates content through the authenticated CMS API',async()=>{
  render(<CmsManagementPanel s={{accessToken:'token',user:{permissions:['CMS_VIEW','CMS_CREATE']}}} error={vi.fn()}/>);
  await screen.findByText('Solar guide');
  fireEvent.click(screen.getByRole('button',{name:/Create content/i}));
  fireEvent.change(screen.getByLabelText('Title'),{target:{value:'Solar FAQ'}});
  fireEvent.change(screen.getByLabelText('Slug'),{target:{value:'solar-faq'}});
  fireEvent.change(screen.getByLabelText(/Content \(plain text/),{target:{value:'How solar works'}});
  fireEvent.click(screen.getByRole('button',{name:'Save draft revision'}));
  await waitFor(()=>expect(vi.mocked(fetch).mock.calls.some(call=>String(call[0]).endsWith('/cms/content')&&call[1]?.method==='POST')).toBe(true));
 });
});
