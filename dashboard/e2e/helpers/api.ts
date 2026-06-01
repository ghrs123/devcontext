import fs from 'node:fs';
import path from 'node:path';

import type { Page } from '@playwright/test';

const API_BASE_URL =
  process.env.E2E_API_URL || process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

const TOKEN_KEY = 'fitvision_access_token';
const TOKEN_COOKIE = 'fitvision_token';

interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  error: { code: string; message: string } | null;
}

interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  apiKeyPublic: string;
}

interface SeedAuthResponse {
  accessToken?: string;
  jwt?: string;
  token?: string;
}

interface StoreProfile {
  id: string;
  name: string;
  email: string;
}

interface Product {
  id: string;
  externalProductId: string;
  name: string;
}

export interface TestStore {
  email: string;
  password: string;
  jwt: string;
  apiKeyPublic: string;
  storeId: string;
}

export interface TestAdmin {
  email: string;
  password: string;
  jwt: string;
}

export function uniqueEmail(): string {
  return `test-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@fitvision-test.io`;
}

async function parseEnvelope<T>(response: Response): Promise<T> {
  const payload = (await response.json()) as ApiEnvelope<T>;

  if (!response.ok || !payload.success || payload.data === null) {
    const message = payload.error?.message || `HTTP ${response.status}`;
    throw new Error(message);
  }

  return payload.data;
}

async function parseEnvelopeSafe(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function readTokenFromSeedPayload(payload: unknown): string | null {
  if (!payload || typeof payload !== 'object') {
    return null;
  }

  const direct = payload as SeedAuthResponse;
  if (typeof direct.accessToken === 'string' && direct.accessToken) {
    return direct.accessToken;
  }
  if (typeof direct.jwt === 'string' && direct.jwt) {
    return direct.jwt;
  }
  if (typeof direct.token === 'string' && direct.token) {
    return direct.token;
  }

  const wrapped = payload as { data?: SeedAuthResponse | null };
  if (!wrapped.data || typeof wrapped.data !== 'object') {
    return null;
  }

  if (typeof wrapped.data.accessToken === 'string' && wrapped.data.accessToken) {
    return wrapped.data.accessToken;
  }
  if (typeof wrapped.data.jwt === 'string' && wrapped.data.jwt) {
    return wrapped.data.jwt;
  }
  if (typeof wrapped.data.token === 'string' && wrapped.data.token) {
    return wrapped.data.token;
  }

  return null;
}

async function loginForAdminToken(email: string, password: string): Promise<string> {
  const auth = await apiRequest<AuthResponse>('/api/dashboard/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });

  return auth.accessToken;
}

async function apiRequest<T>(
  apiPath: string,
  init: RequestInit = {},
  jwt?: string
): Promise<T> {
  const headers = new Headers(init.headers);

  if (!headers.has('Content-Type') && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (jwt) {
    headers.set('Authorization', `Bearer ${jwt}`);
  }

  const response = await fetch(`${API_BASE_URL}${apiPath}`, {
    ...init,
    headers,
  });

  return parseEnvelope<T>(response);
}

export async function createTestStore(
  overrides: { name?: string; password?: string } = {}
): Promise<TestStore> {
  const email = uniqueEmail();
  const password = overrides.password ?? 'password123';
  const name = overrides.name ?? 'Test Store';

  const auth = await apiRequest<AuthResponse>('/api/dashboard/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name, email, password, platform: 'shopify' }),
  });

  const profile = await apiRequest<StoreProfile>(
    '/api/dashboard/v1/store/profile',
    {},
    auth.accessToken
  );

  return {
    email,
    password,
    jwt: auth.accessToken,
    apiKeyPublic: auth.apiKeyPublic,
    storeId: profile.id,
  };
}

export async function createOrLoginAdmin(
  overrides: { email?: string; password?: string; name?: string } = {}
): Promise<TestAdmin> {
  const email = overrides.email ?? process.env.E2E_ADMIN_EMAIL ?? 'admin@fitvision.io';
  const password = overrides.password ?? process.env.E2E_ADMIN_PASSWORD ?? 'password123';
  const name = overrides.name ?? process.env.E2E_ADMIN_NAME ?? 'FitVision Admin';
  const bootstrapToken = process.env.E2E_ADMIN_BOOTSTRAP_TOKEN ?? 'test-bootstrap-token';

  const response = await fetch(`${API_BASE_URL}/api/admin/seed`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Bootstrap-Token': bootstrapToken,
    },
    body: JSON.stringify({ email, password, name }),
  });

  if (response.ok) {
    const seedPayload = await parseEnvelopeSafe(response);
    const seedToken = readTokenFromSeedPayload(seedPayload);

    if (seedToken) {
      return { email, password, jwt: seedToken };
    }

    const jwt = await loginForAdminToken(email, password);
    return { email, password, jwt };
  }

  if (response.status === 409 || response.status === 410) {
    try {
      const jwt = await loginForAdminToken(email, password);
      return { email, password, jwt };
    } catch {
      throw new Error(
        `Admin bootstrap is unavailable (status ${response.status}) and login failed for ${email}. Set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD to valid credentials.`
      );
    }
  }

  const payload = await parseEnvelopeSafe(response);
  const maybeMessage =
    payload &&
    typeof payload === 'object' &&
    'error' in payload &&
    payload.error &&
    typeof payload.error === 'object' &&
    'message' in payload.error &&
    typeof payload.error.message === 'string'
      ? payload.error.message
      : `HTTP ${response.status}`;

  throw new Error(`Unable to bootstrap admin account: ${maybeMessage}`);
}

export async function createTestProduct(
  jwt: string,
  name = 'Test Product'
): Promise<{ id: string; externalProductId: string }> {
  const externalProductId = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

  const product = await apiRequest<Product>(
    '/api/dashboard/v1/products',
    {
      method: 'POST',
      body: JSON.stringify({
        name,
        externalProductId,
        category: 'TOPS',
        genderTarget: 'UNISEX',
      }),
    },
    jwt
  );

  return { id: product.id, externalProductId: product.externalProductId };
}

export async function uploadTestSizeChart(jwt: string, productId: string): Promise<void> {
  const fixturePath = path.join(__dirname, '..', 'fixtures', 'size-chart-tops.csv');
  const fileBuffer = fs.readFileSync(fixturePath);
  const blob = new Blob([fileBuffer], { type: 'text/csv' });
  const file = new File([blob], 'size-chart-tops.csv', { type: 'text/csv' });

  const formData = new FormData();
  formData.append('file', file);

  await apiRequest<unknown>(
    `/api/dashboard/v1/size-charts/${productId}/upload`,
    {
      method: 'POST',
      body: formData,
    },
    jwt
  );
}

export async function isProductLimitEnforced(): Promise<boolean> {
  const probeStore = await createTestStore({ name: 'Limit Probe Store' });
  const created: string[] = [];

  try {
    for (let i = 0; i < 3; i += 1) {
      const product = await createTestProduct(probeStore.jwt, `Probe Product ${i + 1}`);
      created.push(product.id);
    }
    return false;
  } catch {
    return created.length === 2;
  } finally {
    await deleteTestStore(probeStore.jwt);
  }
}

export async function isBillingAvailable(jwt: string): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/dashboard/v1/billing/status`, {
      headers: { Authorization: `Bearer ${jwt}` },
    });
    return response.ok;
  } catch {
    return false;
  }
}

export async function deleteTestStore(jwt: string): Promise<void> {
  let products: Product[] = [];
  try {
    products = await apiRequest<Product[]>('/api/dashboard/v1/products', {}, jwt);
  } catch {
    // Best-effort cleanup. Token may be invalid (e.g., store deactivated during test).
    return;
  }

  for (const product of products) {
    const response = await fetch(`${API_BASE_URL}/api/dashboard/v1/products/${product.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${jwt}` },
    });
    if (!response.ok && response.status !== 204) {
      // Best-effort cleanup — no store delete endpoint exists
    }
  }

  const brands = await apiRequest<Array<{ id: string; isGlobal: boolean }>>(
    '/api/dashboard/v1/brands',
    {},
    jwt
  );
  for (const brand of brands.filter((b) => !b.isGlobal)) {
    await fetch(`${API_BASE_URL}/api/dashboard/v1/brands/${brand.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${jwt}` },
    });
  }
}

export async function adminUpdateStoreStatus(
  adminJwt: string,
  storeId: string,
  status: 'ACTIVE' | 'INACTIVE'
): Promise<void> {
  await apiRequest<unknown>(
    `/api/admin/v1/stores/${storeId}/status`,
    {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    },
    adminJwt
  );
}

export async function setAuthInPage(page: Page, jwt: string): Promise<void> {
  await page.context().addCookies([
    {
      name: TOKEN_COOKIE,
      value: encodeURIComponent(jwt),
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
    },
  ]);

  await page.addInitScript(
    ({ token, tokenKey, cookieName }) => {
      globalThis.localStorage.setItem(tokenKey, token);
      document.cookie = `${cookieName}=${encodeURIComponent(token)}; Path=/; SameSite=Lax; Max-Age=86400`;
    },
    { token: jwt, tokenKey: TOKEN_KEY, cookieName: TOKEN_COOKIE }
  );
}

export function renderHtmlFixture(filePath: string, replacements: Record<string, string>): string {
  let html = fs.readFileSync(filePath, 'utf8');
  for (const [token, value] of Object.entries(replacements)) {
    html = html.replaceAll(token, value);
  }
  return html;
}
