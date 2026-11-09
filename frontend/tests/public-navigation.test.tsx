import React from 'react';
import {fireEvent,render,screen} from '@testing-library/react';
import {describe,expect,it,vi} from 'vitest';
import PublicSiteHeader from '../src/PublicSiteHeader';

describe('public navigation',()=>{
 it('navigates to the product catalogue without a full page reload for signed-in customers',()=>{
  const navigate=vi.fn();
  render(<PublicSiteHeader accountName="Solar Customer" onNavigate={navigate}/>);
  const products=screen.getByRole('link',{name:'Our Products'});
  fireEvent.click(products);
  expect(navigate).toHaveBeenCalledWith('/products');
  expect(products.getAttribute('href')).toBe('/products');
 });

 it('preserves normal browser behavior for modified link clicks',()=>{
  const navigate=vi.fn();
  render(<PublicSiteHeader accountName="Solar Customer" onNavigate={navigate}/>);
  fireEvent.click(screen.getByRole('link',{name:'Our Products'}),{ctrlKey:true});
  expect(navigate).not.toHaveBeenCalled();
 });
});
