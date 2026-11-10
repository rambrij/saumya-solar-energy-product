import { test, expect } from '@playwright/test';

test('browser auth uses HttpOnly refresh cookie and enforces CSRF, rotation and logout revocation', async ({ page, baseURL }) => {
  if (!baseURL) throw new Error('E2E_BASE_URL is required');
  await page.goto(baseURL);

  const session = await page.evaluate(async () => {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'admin@saumya.local', password: 'Admin@123' }),
    });
    if (!response.ok) throw new Error('Test vendor login failed: ' + response.status);
    return response.json();
  });
  expect(session.accessToken).toBeTruthy();
  expect(session.csrfToken).toBeTruthy();

  const cookies = await page.context().cookies(baseURL.replace(/\/$/, '') + '/api/v1/auth/refresh');
  const refreshCookie = cookies.find(cookie => cookie.name === 'saumya_refresh');
  expect(refreshCookie, 'refresh credential must be a browser cookie').toBeTruthy();
  expect(refreshCookie?.httpOnly).toBe(true);
  expect(refreshCookie?.path).toBe('/api/v1/auth');

  const persistedAccessToken = await page.evaluate(token => {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i) || '';
      const value = localStorage.getItem(key) || '';
      if (value === token || value.includes(token)) return true;
    }
    return false;
  }, session.accessToken);
  expect(persistedAccessToken).toBe(false);

  const missingCsrf = await page.evaluate(async () => {
    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    });
    return response.status;
  });
  expect(missingCsrf).toBe(403);

  const mismatchedCsrf = await page.evaluate(async () => {
    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-CSRF-Token': 'invalid-csrf-token' },
    });
    return response.status;
  });
  expect(mismatchedCsrf).toBe(403);

  const rotated = await page.evaluate(async csrfToken => {
    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-CSRF-Token': csrfToken },
    });
    return { status: response.status, body: response.ok ? await response.json() : null };
  }, session.csrfToken);
  expect(rotated.status).toBe(200);
  expect(rotated.body?.accessToken).toBeTruthy();
  expect(rotated.body?.csrfToken).toBeTruthy();

  const logoutStatus = await page.evaluate(async csrfToken => {
    const response = await fetch('/api/v1/auth/logout', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-CSRF-Token': csrfToken },
    });
    return response.status;
  }, rotated.body.csrfToken);
  expect(logoutStatus).toBe(204);

  const revoked = await page.evaluate(async token => {
    const response = await fetch('/api/v1/me', {
      headers: { Authorization: 'Bearer ' + token },
    });
    return response.status;
  }, rotated.body.accessToken);
  expect(revoked).toBe(401);
});
