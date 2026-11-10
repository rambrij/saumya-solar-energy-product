import { test, expect } from '@playwright/test';

test('React vendor workspace stays isolated when switching between two tenant accounts', async ({ page, baseURL }) => {
  test.setTimeout(120_000);
  if (!baseURL) throw new Error('E2E_BASE_URL is required');
  const suffix = String(Date.now());
  const emailB = 'tenant-b-' + suffix + '@example.test';
  const passwordB = 'TenantB-Password-2026';
  const productNameB = 'TENANT-B-ONLY-' + suffix;
  await page.goto(baseURL);

  const fixture = await page.evaluate(async ({ emailB, passwordB, suffix, productNameB }) => {
    const login = async (email: string, password: string) => {
      const response = await fetch('/api/v1/auth/login', {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      if (!response.ok) throw new Error('Fixture login failed: ' + response.status);
      return response.json();
    };
    const platform = await login('platform@saumya.local', 'Platform@123');
    const headers = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + platform.accessToken, 'X-CSRF-Token': platform.csrfToken };
    const tenantResponse = await fetch('/api/v1/tenants', {
      method: 'POST', credentials: 'include', headers,
      body: JSON.stringify({ name: 'Browser tenant ' + suffix, slug: 'browser-tenant-' + suffix,
        adminName: 'Browser tenant admin', adminEmail: emailB, adminPassword: passwordB }),
    });
    if (!tenantResponse.ok) throw new Error('Tenant fixture creation failed: ' + tenantResponse.status);
    const tenant = await tenantResponse.json();
    const vendor = await login(emailB, passwordB);
    const vendorHeaders = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + vendor.accessToken, 'X-CSRF-Token': vendor.csrfToken };
    const categoryResponse = await fetch('/api/v1/categories', {
      method: 'POST', credentials: 'include', headers: vendorHeaders,
      body: JSON.stringify({ name: 'Browser category ' + suffix, description: 'Tenant B fixture', active: true }),
    });
    if (!categoryResponse.ok) throw new Error('Category fixture creation failed: ' + categoryResponse.status);
    const category = await categoryResponse.json();
    const productResponse = await fetch('/api/v1/catalog/products', {
      method: 'POST', credentials: 'include', headers: vendorHeaders,
      body: JSON.stringify({ categoryId: category.id, sku: 'B-' + suffix, name: productNameB,
        price: 999, purchasePrice: 500, discountPercent: 0, taxPercent: 0, reorderLevel: 0,
        warrantyYears: 1, unitOfMeasure: 'PCS', serialized: false, active: true }),
    });
    if (!productResponse.ok) throw new Error('Product fixture creation failed: ' + productResponse.status);
    const product = await productResponse.json();
    return { tenantId: tenant.id, productId: product.id, productNameB };
  }, { emailB, passwordB, suffix, productNameB });

  async function signIn(email: string, password: string) {
    await page.goto(baseURL!);
    await page.locator('button.public-login-link').click({ force: true });
    await page.getByPlaceholder('Email').fill(email);
    await page.getByPlaceholder('Password').fill(password);
    await page.getByRole('button', { name: 'Login', exact: true }).last().click();
    await expect(page.getByRole('navigation', { name: 'Application navigation' })).toBeVisible();
    await page.getByRole('button', { name: 'Catalog', exact: true }).click();
    await page.getByRole('button', { name: 'Products', exact: true }).click();
  }

  await signIn('admin@saumya.local', 'Admin@123');
  const tenantA = await page.evaluate(async () => {
    const response = await fetch('/api/v1/auth/login', { method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'admin@saumya.local', password: 'Admin@123' }) });
    return response.json();
  });
  expect(tenantA.user.tenantId).not.toBe(fixture.tenantId);
  await page.getByRole('textbox', { name: 'Search Product records' }).fill(fixture.productNameB);
  await expect(page.getByText('No matching records')).toBeVisible();
  await expect(page.getByText(fixture.productNameB, { exact: true })).toHaveCount(0);

  const foreignAccess = await page.evaluate(async ({ tenantId, productId }) => {
    const sessionResponse = await fetch('/api/v1/auth/login', { method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'admin@saumya.local', password: 'Admin@123' }) });
    const session = await sessionResponse.json();
    const list = await fetch('/api/v1/products?tenantId=' + tenantId, { credentials: 'include',
      headers: { Authorization: 'Bearer ' + session.accessToken } });
    const rows = await list.json();
    const update = await fetch('/api/v1/products/' + productId, { method: 'PATCH', credentials: 'include',
      headers: { Authorization: 'Bearer ' + session.accessToken, 'Content-Type': 'application/json', 'X-CSRF-Token': session.csrfToken },
      body: JSON.stringify({ name: 'cross tenant tamper', tenantId }) });
    const remove = await fetch('/api/v1/products/' + productId, { method: 'DELETE', credentials: 'include',
      headers: { Authorization: 'Bearer ' + session.accessToken, 'X-CSRF-Token': session.csrfToken } });
    return { listStatus: list.status, containsForeign: rows.some((row: any) => row.id === productId),
      updateStatus: update.status, deleteStatus: remove.status };
  }, { tenantId: fixture.tenantId, productId: fixture.productId });
  expect(foreignAccess.listStatus).toBe(200);
  expect(foreignAccess.containsForeign).toBe(false);
  expect([403, 404]).toContain(foreignAccess.updateStatus);
  expect([403, 404]).toContain(foreignAccess.deleteStatus);

  await page.getByRole('button', { name: 'Logout', exact: true }).click();
  await signIn(emailB, passwordB);
  await expect(page.getByText(fixture.productNameB, { exact: true })).toBeVisible();
});
