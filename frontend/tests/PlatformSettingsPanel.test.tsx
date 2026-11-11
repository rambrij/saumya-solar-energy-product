import React from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import PlatformSettingsPanel from '../src/PlatformSettingsPanel';

const defaults=[
 {key:'PLATFORM_DEFAULT_CURRENCY',type:'CURRENCY',value:'INR',version:0},
 {key:'PLATFORM_DEFAULT_TIME_ZONE',type:'TIME_ZONE',value:'Asia/Kolkata',version:0},
 {key:'PLATFORM_DEFAULT_LANGUAGE',type:'LANGUAGE',value:'en-IN',version:0},
 {key:'PLATFORM_SUPPORT_EMAIL',type:'EMAIL',value:'',version:0},
 {key:'PLATFORM_SUPPORT_PHONE',type:'PHONE',value:'',version:0},
 {key:'PLATFORM_BANNER_MESSAGE',type:'STRING',value:'',version:0}
];
describe('Platform settings panel',()=>{
 beforeEach(()=>vi.stubGlobal('fetch',vi.fn(async(_input:any,init:any={})=>({ok:true,status:200,json:async()=>init.method==='PUT'?{...defaults[0],value:'USD',version:1}:defaults}))));
 afterEach(()=>{cleanup();vi.unstubAllGlobals()});
 it('loads allow-listed typed defaults and saves with an expected version',async()=>{
  render(<PlatformSettingsPanel s={{accessToken:'test-token',user:{permissions:['PLATFORM_MANAGE']}}} as any/>);
  expect(await screen.findByDisplayValue('INR')).toBeTruthy();
  fireEvent.change(screen.getByLabelText('Default currency'),{target:{value:'USD'}});
  fireEvent.click(screen.getAllByRole('button',{name:'Save'})[0]);
  await waitFor(()=>expect(vi.mocked(fetch).mock.calls.some(call=>String(call[0]).includes('/platform/settings/PLATFORM_DEFAULT_CURRENCY')&&call[1]?.method==='PUT')).toBe(true));
  const call=vi.mocked(fetch).mock.calls.find(call=>String(call[0]).includes('/platform/settings/PLATFORM_DEFAULT_CURRENCY'))!;
  expect(JSON.parse(String(call[1]?.body))).toEqual({value:'USD',expectedVersion:0});
 });
 it('explains that platform credentials are not managed in this screen',async()=>{
  render(<PlatformSettingsPanel s={{accessToken:'test-token',user:{permissions:['PLATFORM_MANAGE']}}} as any/>);
  expect(await screen.findByText(/Credentials, API keys and provider secrets are not stored or displayed/)).toBeTruthy();
 });
});
