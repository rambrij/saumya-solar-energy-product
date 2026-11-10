import { test, expect } from '@playwright/test';

test('browser requests cannot use forged tenant, customer or branch filters as authorization', async ({ page, baseURL }) => {
  if (!baseURL) throw new Error('E2E_API_BASE_URL is required');

  await page.goto(baseURL + '/actuator/health');
  const session = await page.evaluate(async () => {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'admin@saumya.local', password: 'Admin@123' }),
    });
    if (!response.ok) throw new Error('Seed vendor login failed: ' + response.status);
    return response.json();
  });
  expect(session.accessToken).toBeTruthy();

  const principal = await page.evaluate(async (token) => {
    const response = await fetch('/api/v1/me', {
      headers: { Authorization: 'Bearer ' + token },
    });
    if (!response.ok) throw new Error('Principal lookup failed: ' + response.status);
    return response.json();
  }, session.accessToken);
  expect(principal.accountType).toBe('VENDOR');
  expect(principal.tenantId).toBeTruthy();

  const forgedFilters = {
    tenantId: '00000000-0000-4000-8000-000000000001',
    customerId: '00000000-0000-4000-8000-000000000002',
    branchId: '00000000-0000-4000-8000-000000000003',
  };
  const paths = ['/products', '/customers', '/enquiries', '/leads', '/surveys', '/quotations', '/orders'];
  for (const path of paths) {
    const result = await page.evaluate(async ({ token, path, filters }) => {
      const query = new URLSearchParams(filters).toString();
      const response = await fetch('/api/v1' + path + '?' + query, {
        headers: { Authorization: 'Bearer ' + token },
      });
      return { status: response.status, body: response.ok ? await response.json() : await response.text() };
    }, { token: session.accessToken, path, filters: forgedFilters });

    expect(result.status, path + ' should remain authorized only by the authenticated principal').toBe(200);
    expect(Array.isArray(result.body), path + ' should return a collection').toBe(true);
    for (const row of result.body as Array<Record<string, unknown>>) {
      expect(row.tenantId, path + ' returned a row outside the authenticated tenant').toBe(principal.tenantId);
    }
  }
});
