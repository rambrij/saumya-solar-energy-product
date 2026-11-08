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
  await expect.poll(async () => {
    const response = await page.request.get('http://127.0.0.1:8025/api/v1/messages');
    if (!response.ok()) return 0;
    const payload = await response.json();
    return Number(payload.total ?? payload.messages?.length ?? 0);
  }, { timeout: 30_000, intervals: [1000, 2000, 3000] }).toBeGreaterThan(0);
});

test('vendor can sign in and view business dashboard and customer management', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Login' }).click();
  await page.getByPlaceholder('Email').fill('admin@saumya.local');
  await page.getByPlaceholder('Password').fill('Admin@123');
  await page.getByRole('button', { name: 'Login', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Business Dashboard' })).toBeVisible();
  await page.getByRole('button', { name: 'Notifications' }).click();
  await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible();
  await page.getByRole('button', { name: 'Close' }).click();
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Vendor legal and contact profile' })).toBeVisible();
  await page.getByLabel('Legal business name').fill('Soumya Solar E2E Private Limited');
  await page.getByLabel('Contact phone').fill('9876543210');
  await page.getByLabel('Registered address').fill('12 Solar Park, Chennai');
  await page.getByRole('button', { name: 'Save vendor profile' }).click();
  await expect(page.getByText('Vendor legal profile saved.')).toBeVisible();
  await page.getByLabel('Choose document').setInputFiles({
    name: 'e2e-business-registration.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\\nE2E verification evidence')
  });
  await page.getByRole('button', { name: 'Upload for verification' }).click();
  await expect(page.getByText('Verification document uploaded for platform review.')).toBeVisible();
  await page.getByRole('button', { name: 'Audit Log' }).click();
  await expect(page.getByRole('heading', { name: 'Audit Log' })).toBeVisible();
  await page.getByRole('button', { name: 'Product', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Product' }).first()).toBeVisible();
});

test('customer can sign in and view account dashboard and quotations', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Login' }).click();
  await page.getByPlaceholder('Email').fill('customer@saumya.local');
  await page.getByPlaceholder('Password').fill('Customer@123');
  await page.getByRole('button', { name: 'Login', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'My Solar Dashboard' })).toBeVisible();
  await page.getByRole('button', { name: 'Notifications' }).click();
  await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible();
  await page.getByRole('button', { name: 'Close' }).click();
  await page.getByRole('button', { name: 'My Quotations' }).click();
  await expect(page.getByRole('heading', { name: 'My Quotations' })).toBeVisible();
});
