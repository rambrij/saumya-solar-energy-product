import { test, expect } from '@playwright/test';

test('public enquiry reaches the solar store workflow', async ({ page }) => {
  await page.goto('/contact');
  await page.getByPlaceholder('Your name').fill('E2E Solar Customer');
  await page.getByPlaceholder('Email address').fill(`e2e-${Date.now()}@example.test`);
  await page.getByPlaceholder('Phone number').fill('9876543210');
  await page.getByPlaceholder('Monthly electricity bill (₹)').fill('4500');
  await page.getByPlaceholder('Required capacity (kW)').fill('3');
  await page.getByRole('button', { name: 'Send enquiry' }).click();
  await expect(page.getByRole('status')).toContainText('Your enquiry has been sent');
});

test('vendor can sign in and view business dashboard and customer management', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Login' }).click();
  await page.getByPlaceholder('Email').fill('admin@saumya.local');
  await page.getByPlaceholder('Password').fill('Admin@123');
  await page.getByRole('button', { name: 'Login', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Business Dashboard' })).toBeVisible();
  await page.getByRole('button', { name: 'Customer', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Customer' })).toBeVisible();
});

test('customer can sign in and view account dashboard and quotations', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Login' }).click();
  await page.getByPlaceholder('Email').fill('customer@saumya.local');
  await page.getByPlaceholder('Password').fill('Customer@123');
  await page.getByRole('button', { name: 'Login', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'My Solar Dashboard' })).toBeVisible();
  await page.getByRole('button', { name: 'My Quotations' }).click();
  await expect(page.getByRole('heading', { name: 'My Quotations' })).toBeVisible();
});
